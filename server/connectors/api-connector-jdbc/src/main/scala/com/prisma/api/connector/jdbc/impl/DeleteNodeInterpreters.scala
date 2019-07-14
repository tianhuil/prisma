package com.prisma.api.connector.jdbc.impl

import com.prisma.api.connector._
import com.prisma.api.connector.jdbc.database.JdbcActionsBuilder
import com.prisma.api.connector.jdbc.{NestedDatabaseMutactionInterpreter, TopLevelDatabaseMutactionInterpreter}
import com.prisma.api.schema.APIErrors
import com.prisma.api.schema.APIErrors.NodesNotConnectedError
import com.prisma.gc_values.IdGCValue
import com.prisma.shared.models.{Model, Project, RelationField}
import slick.dbio._

import scala.concurrent.ExecutionContext

case class DeleteNodeInterpreter(mutaction: TopLevelDeleteNode)(implicit val ec: ExecutionContext)
    extends TopLevelDatabaseMutactionInterpreter
    with CascadingDeleteSharedStuff {

  override def dbioAction(mutationBuilder: JdbcActionsBuilder) = {
    for {
      nodeOpt <- mutationBuilder.getNodeByWhere(mutaction.where)
      node <- nodeOpt match {
               case Some(node) =>
                 for {
                   _ <- performCascadingDelete(mutationBuilder, mutaction.where.model, Vector(node.id))
                   _ <- SharedDelete.checkForRequiredRelationsViolations(mutaction.project, mutaction.model, mutationBuilder, Vector(node.id))
                   _ <- mutationBuilder.deleteNodeById(mutaction.where.model, node.id)
                 } yield node
               case None =>
                 DBIO.failed(APIErrors.NodeNotFoundForWhereError(mutaction.where))
             }
    } yield DeleteNodeResult(node, mutaction)
  }
}

case class DeleteNodesInterpreter(mutaction: TopLevelDeleteNodes)(implicit val ec: ExecutionContext)
    extends TopLevelDatabaseMutactionInterpreter
    with CascadingDeleteSharedStuff {

  def dbioAction(mutationBuilder: JdbcActionsBuilder) =
    for {
      ids        <- mutationBuilder.getNodeIdsByFilter(mutaction.model, mutaction.whereFilter)
      groupedIds = ids.grouped(ParameterLimit.groupSize).toVector
      _          <- DBIO.seq(groupedIds.map(performCascadingDelete(mutationBuilder, mutaction.model, _)): _*)
      _          <- DBIO.seq(groupedIds.map(SharedDelete.checkForRequiredRelationsViolations(mutaction.project, mutaction.model, mutationBuilder, _)): _*)
      _          <- DBIO.seq(groupedIds.map(mutationBuilder.deleteNodes(mutaction.model, _)): _*)
    } yield ManyNodesResult(mutaction, ids.size)
}

case class NestedDeleteNodesInterpreter(mutaction: NestedDeleteNodes)(implicit val ec: ExecutionContext)
    extends NestedDatabaseMutactionInterpreter
    with CascadingDeleteSharedStuff {

  def dbioAction(mutationBuilder: JdbcActionsBuilder, parentId: IdGCValue) =
    for {
      ids        <- mutationBuilder.getNodesIdsByParentIdAndWhereFilter(mutaction.relationField, parentId, mutaction.whereFilter)
      groupedIds = ids.grouped(ParameterLimit.groupSize).toVector
      _          <- DBIO.seq(groupedIds.map(performCascadingDelete(mutationBuilder, mutaction.model, _)): _*)
      _          <- DBIO.seq(groupedIds.map(SharedDelete.checkForRequiredRelationsViolations(mutaction.project, mutaction.model, mutationBuilder, _)): _*)
      _          <- DBIO.seq(groupedIds.map(mutationBuilder.deleteNodes(mutaction.model, _)): _*)
    } yield ManyNodesResult(mutaction, ids.size)
}

case class NestedDeleteNodeInterpreter(mutaction: NestedDeleteNode)(implicit val ec: ExecutionContext)
    extends NestedDatabaseMutactionInterpreter
    with CascadingDeleteSharedStuff {

  val parentField = mutaction.relationField
  val parent      = mutaction.relationField.model
  val child       = mutaction.relationField.relatedModel_!

  override def dbioAction(mutationBuilder: JdbcActionsBuilder, parentId: IdGCValue) = {
    for {
      childId <- getChildId(mutationBuilder, parentId)
      _       <- mutationBuilder.ensureThatNodesAreConnected(parentField, childId, parentId)
      _       <- performCascadingDelete(mutationBuilder, child, Vector(childId))
      _       <- SharedDelete.checkForRequiredRelationsViolations(mutaction.project, mutaction.relationField.relatedModel_!, mutationBuilder, Vector(childId))
      _       <- mutationBuilder.deleteNodeById(child, childId)
    } yield UnitDatabaseMutactionResult
  }

  private def getChildId(mutationBuilder: JdbcActionsBuilder, parentId: IdGCValue): DBIO[IdGCValue] = {
    mutaction.where match {
      case Some(where) =>
        mutationBuilder.getNodeIdByWhere(where).map {
          case Some(id) => id
          case None     => throw APIErrors.NodeNotFoundForWhereError(where)
        }
      case None =>
        mutationBuilder.getNodeIdByParentId(parentField, parentId).map {
          case Some(id) => id
          case None =>
            throw NodesNotConnectedError(
              relation = parentField.relation,
              parent = parentField.model,
              parentWhere = Some(NodeSelector.forId(parent, parentId)),
              child = parentField.relatedModel_!,
              childWhere = None
            )
        }
    }
  }
}

object SharedDelete {
  def checkForRequiredRelationsViolations(project: Project, model: Model, mutationBuilder: JdbcActionsBuilder, ids: Vector[IdGCValue])(
      implicit ec: ExecutionContext): DBIO[_] = {
    val fieldsWhereThisModelIsRequired = project.schema.fieldsWhereThisModelIsRequired(model)
    val actions                        = fieldsWhereThisModelIsRequired.map(field => mutationBuilder.errorIfNodesAreInRelation(ids, field))
    DBIO.sequence(actions)
  }
}

trait CascadingDeleteSharedStuff {
  implicit def ec: ExecutionContext

  def performCascadingDelete(mutationBuilder: JdbcActionsBuilder, model: Model, startingIds: Vector[IdGCValue]): DBIO[Unit] = {
    val actions = model.cascadingRelationFields.map(field => recurse(mutationBuilder = mutationBuilder, parentField = field, parentIds = startingIds))
    DBIO.seq(actions: _*)
  }

  private def recurse(
      mutationBuilder: JdbcActionsBuilder,
      parentField: RelationField,
      parentIds: Vector[IdGCValue],
      idsThatCanBeIgnored: Vector[IdGCValue] = Vector.empty
  ): DBIO[Unit] = {

    for {
      childIds        <- mutationBuilder.getNodeIdsByParentIds(parentField, parentIds)
      filteredIds     = childIds.filter(x => !idsThatCanBeIgnored.contains(x))
      childIdsGrouped = filteredIds.grouped(10000).toVector
      model           = parentField.relatedModel_!
      //nestedActions
      _ <- if (filteredIds.isEmpty) {
            DBIO.successful(())
          } else {
            //children
            val cascadingChildrenFields = model.cascadingRelationFields.filter(_ != parentField.relatedField)
            val childActions = for {
              field        <- cascadingChildrenFields
              childIdGroup <- childIdsGrouped
            } yield {
              recurse(mutationBuilder, field, childIdGroup)
            }
            //other parent
            val cascadingBackRelationFieldOfParentField = model.cascadingRelationFields.find(_ == parentField.relatedField)
            val parentActions = for {
              field        <- cascadingBackRelationFieldOfParentField.toVector
              childIdGroup <- childIdsGrouped
            } yield {
              recurse(mutationBuilder, field, childIdGroup, idsThatCanBeIgnored = parentIds)
            }

            DBIO.seq(childActions ++ parentActions: _*)
          }
      //actions for this level
      _ <- DBIO.seq(childIdsGrouped.map(checkTheseOnes(mutationBuilder, parentField, _)): _*)
      _ <- DBIO.seq(childIdsGrouped.map(mutationBuilder.deleteNodes(model, _)): _*)
    } yield ()
  }

  private def checkTheseOnes(mutationBuilder: JdbcActionsBuilder, parentField: RelationField, parentIds: Vector[IdGCValue]) = {
    val model                          = parentField.relatedModel_!
    val fieldsWhereThisModelIsRequired = model.schema.fieldsWhereThisModelIsRequired(model).filter(_ != parentField)
    val actions                        = fieldsWhereThisModelIsRequired.map(field => mutationBuilder.errorIfNodesAreInRelation(parentIds, field))
    DBIO.sequence(actions)
  }
}
