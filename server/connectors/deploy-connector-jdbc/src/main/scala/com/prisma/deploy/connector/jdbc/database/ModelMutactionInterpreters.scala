package com.prisma.deploy.connector.jdbc.database

import com.prisma.deploy.connector._
import com.prisma.shared.models.Model
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.PostgresProfile.api._

case class CreateModelInterpreter(builder: JdbcDeployDatabaseMutationBuilder) extends SqlMutactionInterpreter[CreateModelTable] {

  override def execute(mutaction: CreateModelTable, schemaBeforeMigration: DatabaseSchema): DBIOAction[Any, NoStream, Effect.All] = {
    schemaBeforeMigration.table(mutaction.model.dbName) match {
      case None    => builder.createModelTable(mutaction.project, mutaction.model)
      case Some(_) => DBIO.successful(())
    }
  }

  override def rollback(mutaction: CreateModelTable, schemaBeforeMigration: DatabaseSchema) = {
    // only drop the table if it was created in the step before
    schemaBeforeMigration.table(mutaction.model.dbName) match {
      case None    => builder.dropTable(mutaction.project, tableName = mutaction.model.dbName)
      case Some(_) => DBIO.successful(())
    }
  }
}

case class DeleteModelInterpreter(builder: JdbcDeployDatabaseMutationBuilder) extends SqlMutactionInterpreter[DeleteModelTable] {
  // TODO: this is not symmetric

  override def execute(mutaction: DeleteModelTable, schemaBeforeMigration: DatabaseSchema) = {
    val droppingTable = schemaBeforeMigration.table(mutaction.model.dbName) match {
      case Some(_) => builder.dropTable(mutaction.project, tableName = mutaction.model.dbName)
      case None    => DBIO.successful(())
    }

    val dropScalarListFields = mutaction.scalarListFields.map { field =>
      builder.dropScalarListTable(mutaction.project, mutaction.model.dbName, field, schemaBeforeMigration)
    }

    DBIO.seq(dropScalarListFields :+ droppingTable: _*)
  }

  override def rollback(mutaction: DeleteModelTable, schemaBeforeMigration: DatabaseSchema) = {
    // only recreate the table if it was actually deleted in the step before
    val recreatingTable = schemaBeforeMigration.table(mutaction.model.dbName) match {
      case Some(_) => builder.createModelTable(mutaction.project, model = mutaction.model)
      case None    => DBIO.successful(())
    }
    recreatingTable
  }
}

case class UpdateModelInterpreter(builder: JdbcDeployDatabaseMutationBuilder) extends SqlMutactionInterpreter[UpdateModelTable] {
  override def execute(mutaction: UpdateModelTable, schemaBeforeMigration: DatabaseSchema)  = setName(mutaction, mutaction.oldModel, mutaction.newModel)
  override def rollback(mutaction: UpdateModelTable, schemaBeforeMigration: DatabaseSchema) = setName(mutaction, mutaction.newModel, mutaction.oldModel)

  private def setName(mutaction: UpdateModelTable, oldModel: Model, newModel: Model): DBIOAction[Any, NoStream, Effect.All] = {
    val changeModelTableName = builder.renameTable(mutaction.project, currentName = oldModel.dbName, newName = newModel.dbName)
    val changeScalarListFieldTableNames = mutaction.newModel.scalarListFields.map { field =>
      builder.renameScalarListTable(mutaction.project, oldModel.dbName, field.name, oldModel.dbName, field.name)
    }

    DBIO.seq(changeScalarListFieldTableNames :+ changeModelTableName: _*)
  }
}
