package com.prisma.deploy.validation

import com.prisma.deploy.connector.{ClientDbQueries, DeployConnector, MigrationValueGenerator}
import com.prisma.deploy.migration.validation._
import com.prisma.shared.models.FieldBehaviour.{CreatedAtBehaviour, UpdatedAtBehaviour}
import com.prisma.shared.models.Manifestations.{EmbeddedRelationLink, RelationTable}
import com.prisma.shared.models._
import org.scalactic.{Bad, Good, Or}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DestructiveChanges(clientDbQueries: ClientDbQueries,
                              project: Project,
                              nextSchema: Schema,
                              steps: Vector[MigrationStep],
                              deployConnector: DeployConnector)
    extends MigrationValueGenerator {
  val previousSchema         = project.schema
  val isMigrationFromV1ToV11 = previousSchema.isLegacy && nextSchema.isV11
  val isMongo                = deployConnector.capabilities.isMongo

  def check: Future[Vector[DeployWarning] Or Vector[DeployError]] = {
    checkAgainstExistingData.map { existingDataResults =>
      val results                         = existingDataResults ++ missingCreatedAtOrUpdatedAtDirectives
      val warnings: Vector[DeployWarning] = results.collect { case warning: DeployWarning => warning }
      val errors: Vector[DeployError]     = results.collect { case error: DeployError => error }
      if (errors.isEmpty) {
        Good(warnings)
      } else {
        Bad(errors)
      }
    }
  }

  private def missingCreatedAtOrUpdatedAtDirectives = {
    def errorMessage(fieldName: String) = {
      s"You are migrating to the new datamodel with the field `$fieldName` but is missing the directive `@$fieldName`. Please specify the field as `$fieldName: DateTime! @$fieldName` to keep its original behaviour."
    }
    def warningMessage(fieldName: String, changeVerb: String) = {
      s"You are adding the field `$fieldName` but is missing the directive `@$fieldName`. Please specify the field as `$fieldName: DateTime! @$fieldName` if you want to track the times for when a record was $changeVerb. Or deploy with `--force` to ignore this."
    }
    // Not specifying `@createdAt` or `@updatedAt` is an error when migrating from v1 to v11.
    // It is just a one time warning when new fields are created when completely in v11 mode.
    if (isMigrationFromV1ToV11) {
      for {
        model <- nextSchema.models
        field <- model.scalarFields
        error <- field.name match {
                  case ReservedFields.createdAtFieldName if !field.behaviour.contains(CreatedAtBehaviour) =>
                    Some(DeployError(model.name, field.name, errorMessage(ReservedFields.createdAtFieldName)))
                  case ReservedFields.updatedAtFieldName if !field.behaviour.contains(UpdatedAtBehaviour) =>
                    Some(DeployError(model.name, field.name, errorMessage(ReservedFields.updatedAtFieldName)))
                  case _ =>
                    None
                }
      } yield error

    } else if (nextSchema.isV11) {
      for {
        createField <- steps.collect { case x: CreateField => x }
        field       = nextSchema.getFieldByName_!(createField.model, createField.name)
        warning <- field.name match {
                    case ReservedFields.createdAtFieldName if !field.behaviour.contains(CreatedAtBehaviour) =>
                      Some(DeployWarning(field.model.name, field.name, warningMessage(ReservedFields.createdAtFieldName, "created")))
                    case ReservedFields.updatedAtFieldName if !field.behaviour.contains(UpdatedAtBehaviour) =>
                      Some(DeployWarning(field.model.name, field.name, warningMessage(ReservedFields.updatedAtFieldName, "updated")))
                    case _ =>
                      None
                  }
      } yield warning
    } else {
      Vector.empty
    }
  }

  private def checkAgainstExistingData: Future[Vector[DeployResult]] = {
    val checkResults = steps.map {
      case x: CreateModel    => validationSuccessful
      case x: DeleteModel    => deleteModelValidation(x)
      case x: UpdateModel    => validationSuccessful
      case x: CreateField    => createFieldValidation(x)
      case x: DeleteField    => deleteFieldValidation(x)
      case x: UpdateField    => updateFieldValidation(x)
      case x: CreateEnum     => validationSuccessful
      case x: DeleteEnum     => deleteEnumValidation(x)
      case x: UpdateEnum     => updateEnumValidation(x)
      case x: CreateRelation => createRelationValidation(x)
      case x: DeleteRelation => deleteRelationValidation(x)
      case x: UpdateRelation => updateRelationValidation(x)
      case x: UpdateSecrets  => validationSuccessful
    }

    Future.sequence(checkResults).map(_.flatten)
  }

  private def deleteModelValidation(x: DeleteModel) = {
    val model = previousSchema.getModelByName_!(x.name)
    clientDbQueries.existsByModel(model).map {
      case true  => Vector(DeployWarnings.dataLossModel(x.name))
      case false => Vector.empty
    }
  }

  private def createFieldValidation(x: CreateField): Future[Vector[DeployResult]] = {
    val field = nextSchema.getFieldByName_!(x.model, x.name)

    def newRequiredScalarField(model: Model) = field.isScalar && field.isRequired match {
      case true =>
        clientDbQueries.existsByModel(model).map {
          case true if !field.isUnique && !isMongo =>
            Vector(DeployWarnings.migValueUsedOnNewField(model.name, field.name, migrationValueForField(field.asScalarField_!)))

          case true if field.isUnique || isMongo =>
            Vector(DeployErrors.creatingUniqueRequiredFieldWithExistingNulls(`type` = model.name, field = field.name))

          case false =>
            Vector.empty
        }

      case false =>
        validationSuccessful
    }

    def newToOneBackRelationField(model: Model) = {
      field match {
        case rf: RelationField if !rf.isList && previousSchema.relations.exists(rel => rel.name == rf.relation.name) =>
          val previousRelation                   = previousSchema.getRelationByName_!(rf.relation.name)
          val relationSideThatCantHaveDuplicates = if (previousRelation.modelAName == model.name) RelationSide.A else RelationSide.B

          clientDbQueries.existsDuplicateByRelationAndSide(previousRelation, relationSideThatCantHaveDuplicates).map {
            case true =>
              Vector(
                DeployError(
                  `type` = model.name,
                  field = field.name,
                  description =
                    s"You are adding a singular backrelation field to a type but there are already pairs in the relation that would violate that constraint."
                ))
            case false => Vector.empty
          }
        case _ => validationSuccessful
      }
    }

    previousSchema.getModelByName(x.model) match {
      case Some(existingModel) =>
        for {
          required     <- newRequiredScalarField(existingModel)
          backRelation <- newToOneBackRelationField(existingModel)
        } yield {
          required ++ backRelation
        }

      case None =>
        validationSuccessful
    }
  }

  private def deleteFieldValidation(x: DeleteField) = {
    val model = previousSchema.getModelByName_!(x.model)
    val field = model.getFieldByName_!(x.name)

    val accidentalRemovalError = field match {
      case f if isMigrationFromV1ToV11 && f.name == ReservedFields.createdAtFieldName =>
        Some(
          DeployError(
            model.name,
            field.name,
            s"You are removing the field `${ReservedFields.createdAtFieldName}` while migrating to the new datamodel. Add the field `createdAt: DateTime! @createdAt` explicitly to your model to keep this functionality."
          ))
      case f if isMigrationFromV1ToV11 && f.name == ReservedFields.updatedAtFieldName =>
        Some(
          DeployError(
            model.name,
            field.name,
            s"You are removing the field `${ReservedFields.updatedAtFieldName}` while migrating to the new datamodel. Add the field `updatedAt: DateTime! @updatedAt` explicitly to your model to keep this functionality."
          ))
      case _ =>
        None
    }

    val dataLossError = if (field.isScalar) {
      clientDbQueries.existsByModel(model).map {
        case true  => Vector(DeployWarnings.dataLossField(x.model, x.name))
        case false => Vector.empty
      }
    } else {
      validationSuccessful
    }

    dataLossError.map(errors => errors ++ accidentalRemovalError)
  }

  private def updateFieldValidation(x: UpdateField) = {
    val model                    = previousSchema.getModelByName_!(x.model)
    val oldField                 = model.getFieldByName_!(x.name)
    val newField                 = nextSchema.getModelByName_!(x.newModel).getFieldByName_!(x.finalName)
    val cardinalityChanges       = oldField.isList != newField.isList
    val typeChanges              = oldField.typeIdentifier != newField.typeIdentifier
    val goesFromScalarToRelation = oldField.isScalar && newField.isRelation
    val goesFromRelationToScalar = oldField.isRelation && newField.isScalar
    val becomesRequired          = !oldField.isRequired && newField.isRequired
    val becomesUnique            = !oldField.isUnique && newField.isUnique
    def isIdTypeChange: Boolean  = oldField.isScalar && newField.isScalar && oldField.asScalarField_!.isId && newField.asScalarField_!.isId && typeChanges

    def warnings: Future[Vector[DeployResult]] = () match {
      case _ if cardinalityChanges || typeChanges || goesFromRelationToScalar || goesFromScalarToRelation =>
        clientDbQueries.existsByModel(model).map {
          case true if newField.isRequired && newField.isScalar && !isMongo =>
            Vector(
              DeployWarnings.dataLossField(model.name, oldField.name),
              DeployWarnings.migValueUsedOnExistingField(model.name, oldField.name, migrationValueForField(newField.asScalarField_!))
            )

          case true if newField.isRequired && newField.isScalar && isMongo =>
            Vector(DeployErrors.makingFieldRequiredMongo(model.name, oldField.name))

          case true =>
            Vector(DeployWarnings.dataLossField(model.name, oldField.name))

          case false =>
            Vector.empty
        }
      case _ => Future.successful(Vector.empty)
    }

    def resultsForNull: Future[Vector[DeployResult]] = becomesRequired match {
      case true =>
        clientDbQueries.existsNullByModelAndField(model, oldField).map {
          case true if !newField.isUnique && newField.isScalar =>
            Vector(DeployWarnings.migValueUsedOnExistingField(`type` = model.name, field = oldField.name, migrationValueForField(newField.asScalarField_!)))

          case true if !newField.isUnique && !newField.isScalar =>
            Vector(DeployErrors.makingFieldRequired(`type` = model.name, field = oldField.name))

          case true if newField.isUnique =>
            Vector(DeployErrors.updatingUniqueRequiredFieldWithExistingNulls(`type` = model.name, field = oldField.name))

          case false =>
            Vector.empty
        }
      case _ =>
        validationSuccessful
    }

    def uniqueErrors: Future[Vector[DeployError]] = becomesUnique match {
      case true =>
        clientDbQueries.existsDuplicateValueByModelAndField(model, oldField.asInstanceOf[ScalarField]).map {
          case true  => Vector(DeployErrors.makingFieldUnique(`type` = model.name, field = oldField.name))
          case false => Vector.empty
        }

      case false =>
        validationSuccessful
    }

    def idTypeChangeError: Future[Option[DeployError]] = isIdTypeChange match {
      case true =>
        clientDbQueries.existsByModel(model).map {
          case true  => Option(DeployErrors.changingTypeOfIdField(`type` = model.name, field = oldField.name))
          case false => None
        }
      case false =>
        validationSuccessfulOpt
    }

    for {
      warnings          <- warnings
      resultsForNull    <- resultsForNull
      uniqueError       <- uniqueErrors
      idTypeChangeError <- idTypeChangeError
    } yield {
      warnings ++ resultsForNull ++ uniqueError ++ idTypeChangeError
    }
  }

  private def deleteEnumValidation(x: DeleteEnum) = {
    //already covered by deleteField
    validationSuccessful
  }

  private def updateEnumValidation(x: UpdateEnum) = {
    val oldEnum                       = previousSchema.getEnumByName_!(x.name)
    val newEnum                       = nextSchema.getEnumByName_!(x.finalName)
    val deletedValues: Vector[String] = oldEnum.values.filter(value => !newEnum.values.contains(value))

    if (deletedValues.nonEmpty) {
      val modelsWithFieldsThatUseEnum = previousSchema.models.filter(m => m.fields.exists(f => f.enum.isDefined && f.enum.get.name == x.name)).toVector
      val res = deletedValues.map { deletedValue =>
        clientDbQueries.enumValueIsInUse(modelsWithFieldsThatUseEnum, x.name, deletedValue).map {
          case true  => Vector(DeployError.global(s"You are deleting the value '$deletedValue' of the enum '${x.name}', but that value is in use."))
          case false => Vector.empty
        }
      }
      Future.sequence(res).map(_.flatten)

    } else {
      validationSuccessful
    }
  }

  private def createRelationValidation(x: CreateRelation) = {

    val nextRelation = nextSchema.getRelationByName_!(x.name)

    def checkRelationSide(modelName: String) = {
      val nextModelA      = nextSchema.getModelByName_!(modelName)
      val nextModelAField = nextModelA.relationFields.find(field => field.relation == nextRelation)

      val modelARequired = nextModelAField match {
        case None        => false
        case Some(field) => field.isRequired
      }

      if (modelARequired) previousSchema.getModelByName(modelName) match {
        case Some(model) =>
          clientDbQueries.existsByModel(model).map {
            case true =>
              Vector(DeployError(`type` = model.name, s"You are creating a required relation, but there are already nodes that would violate that constraint."))
            case false => Vector.empty
          }

        case None => validationSuccessful
      } else {
        validationSuccessful
      }
    }

    val checks = Vector(checkRelationSide(nextRelation.modelAName), checkRelationSide(nextRelation.modelBName))

    Future.sequence(checks).map(_.flatten)
  }

  private def deleteRelationValidation(x: DeleteRelation) = {
    val previousRelation = previousSchema.getRelationByName_!(x.name)
    dataLossForRelationValidation(previousRelation)
  }

  private def updateRelationValidation(x: UpdateRelation) = {
    // becomes required is handled by the change on the updateField
    val previousRelation = previousSchema.getRelationByName_!(x.name)
    val nextRelation     = nextSchema.getRelationByName_!(x.finalName)

    (previousRelation.manifestation, nextRelation.manifestation) match {
      case (RelationTable(_, _, _, None), RelationTable(_, _, _, Some(idColumn))) =>
        val error = DeployError(previousRelation.name, "Adding an id field to an existing link table is forbidden.", Some(idColumn))
        Future.successful(Vector(error))

      case (_: RelationTable, _: RelationTable) =>
        validationSuccessful

      case (_: EmbeddedRelationLink, _: RelationTable) =>
        dataLossForRelationValidation(previousRelation)

      case (_: RelationTable, _: EmbeddedRelationLink) =>
        dataLossForRelationValidation(previousRelation)

      case (p: EmbeddedRelationLink, n: EmbeddedRelationLink) =>
        val previousModel  = previousSchema.getModelByName_!(p.inTableOfModelName)
        val nextModel      = nextSchema.getModelByName_!(n.inTableOfModelName)
        val modelDidChange = previousModel.stableIdentifier != nextModel.stableIdentifier
        if (modelDidChange) {
          dataLossForRelationValidation(previousRelation)
        } else {
          validationSuccessful
        }
    }
  }

  private def dataLossForRelationValidation(relation: Relation) = {
    clientDbQueries.existsByRelation(relation).map {
      case true  => Vector(DeployWarnings.dataLossRelation(relation.name))
      case false => Vector.empty
    }
  }

  private def validationSuccessful    = Future.successful(Vector.empty)
  private def validationSuccessfulOpt = Future.successful(Option.empty)
}
