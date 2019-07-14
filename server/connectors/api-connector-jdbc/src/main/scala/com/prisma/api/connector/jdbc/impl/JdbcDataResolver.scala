package com.prisma.api.connector.jdbc.impl

import com.prisma.api.connector._
import com.prisma.api.connector.jdbc.JdbcApiMetrics
import com.prisma.api.connector.jdbc.database.JdbcActionsBuilder
import com.prisma.connector.shared.jdbc.SlickDatabase
import com.prisma.gc_values._
import com.prisma.shared.models._
import slick.dbio.DBIO

import scala.concurrent.{ExecutionContext, Future}

case class JdbcDataResolver(
    project: Project,
    slickDatabase: SlickDatabase
)(implicit ec: ExecutionContext)
    extends DataResolver {

  val queryBuilder = JdbcActionsBuilder(
    project = project,
    slickDatabase = slickDatabase
  )

  private def runAttached[T](query: DBIO[T]) = {
    if (slickDatabase.isSQLite) {
      import slickDatabase.profile.api._

      val list               = sql"""PRAGMA database_list;""".as[(String, String, String)]
      val path               = s"""'db/${project.dbName}.db'"""
      val attach             = sqlu"ATTACH DATABASE #${path} AS #${project.dbName};"
      val activateForeignKey = sqlu"""PRAGMA foreign_keys = ON;"""

      val attachIfNecessary = for {
        attachedDbs <- list
        _ <- attachedDbs.map(_._2).contains(project.dbName) match {
              case true  => slick.dbio.DBIO.successful(())
              case false => attach
            }
        _      <- activateForeignKey
        result <- query
      } yield result

      slickDatabase.database.run(attachIfNecessary.withPinnedSession)
    } else {
      slickDatabase.database.run(query)
    }
  }

  override def getNodeByWhere(where: NodeSelector, selectedFields: SelectedFields): Future[Option[PrismaNode]] = {
    performWithTiming("getNodeByWhere", runAttached(queryBuilder.getNodeByWhere(where, selectedFields)))
  }

  override def getNodes(model: Model, args: QueryArguments, selectedFields: SelectedFields): Future[ResolverResult[PrismaNode]] = {
    val query = queryBuilder.getNodes(model, args, selectedFields)
    performWithTiming("loadModelRowsForExport", runAttached(query))
  }

  override def getRelatedNodes(
      fromField: RelationField,
      fromNodeIds: Vector[IdGCValue],
      args: QueryArguments,
      selectedFields: SelectedFields
  ): Future[Vector[ResolverResult[PrismaNodeWithParent]]] = {
    val query = queryBuilder.getRelatedNodes(fromField, fromNodeIds, args, selectedFields)
    performWithTiming("resolveByRelation", runAttached(query))
  }

  override def getScalarListValues(model: Model, field: ScalarField, args: QueryArguments): Future[ResolverResult[ScalarListValues]] = {
    val query = queryBuilder.getScalarListValues(model, field, args)
    performWithTiming("loadListRowsForExport", runAttached(query))
  }

  override def getScalarListValuesByNodeIds(model: Model, listField: ScalarField, nodeIds: Vector[IdGCValue]): Future[Vector[ScalarListValues]] = {
    val query = queryBuilder.getScalarListValuesByNodeIds(listField, nodeIds)
    performWithTiming("batchResolveScalarList", runAttached(query))
  }

  override def getRelationNodes(relationId: String, args: QueryArguments): Future[ResolverResult[RelationNode]] = {
    val relation = project.schema.relations.find(_.relationTableName == relationId).get
    val query    = queryBuilder.getRelationNodes(relation, args)
    performWithTiming("loadRelationRowsForExport", runAttached(query))
  }

  override def countByTable(table: String): Future[Int] = {
    val actualTable = project.schema.getModelByName(table) match {
      case Some(model) => model.dbName
      case None        => table
    }
    val query = queryBuilder.countAllFromTable(actualTable, None)
    performWithTiming("countByTable", runAttached(query))
  }

  override def countByModel(model: Model, args: QueryArguments): Future[Int] = {
    val query = queryBuilder.countFromModel(model, args)
    performWithTiming("countByModel", runAttached(query))
  }

  protected def performWithTiming[A](name: String, f: => Future[A]): Future[A] = JdbcApiMetrics.sqlQueryTimer.timeFuture(project.id, name) { f }
}
