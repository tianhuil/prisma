package com.prisma.deploy.connector.sqlite

import java.sql.Driver

import com.prisma.config.DatabaseConfig
import com.prisma.deploy.connector._
import com.prisma.deploy.connector.jdbc.SQLiteDatabaseInspector
import com.prisma.deploy.connector.jdbc.database.{JdbcClientDbQueries, JdbcDeployMutactionExecutor}
import com.prisma.deploy.connector.jdbc.persistence.{JdbcCloudSecretPersistence, JdbcMigrationPersistence, JdbcProjectPersistence, JdbcTelemetryPersistence}
import com.prisma.deploy.connector.persistence.{MigrationPersistence, ProjectPersistence, TelemetryPersistence}
import com.prisma.deploy.connector.sqlite.database.{SQLiteInternalDatabaseSchema, SQLiteJdbcDeployDatabaseMutationBuilder, SQLiteTypeMapper}
import com.prisma.shared.models.{ConnectorCapabilities, Project, ProjectIdEncoder}
import org.joda.time.DateTime
import slick.dbio.Effect.Read
import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.meta.MTable

import scala.concurrent.{ExecutionContext, Future}

case class SQLiteDeployConnector(config: DatabaseConfig, driver: Driver)(implicit ec: ExecutionContext) extends DeployConnector {
  lazy val internalDatabaseDefs = SQLiteInternalDatabaseDefs(config, driver)
  lazy val setupDatabase        = internalDatabaseDefs.setupDatabases
  lazy val databases            = internalDatabaseDefs.managementDatabases
  lazy val managementDatabase   = databases.primary
  lazy val projectDatabase      = databases.primary.database
  lazy val mySqlTypeMapper      = SQLiteTypeMapper()
  lazy val mutationBuilder      = SQLiteJdbcDeployDatabaseMutationBuilder(managementDatabase, mySqlTypeMapper)

  override val projectPersistence: ProjectPersistence             = JdbcProjectPersistence(managementDatabase, config)
  override val migrationPersistence: MigrationPersistence         = JdbcMigrationPersistence(managementDatabase)
  override val cloudSecretPersistence: JdbcCloudSecretPersistence = JdbcCloudSecretPersistence(managementDatabase)
  override val telemetryPersistence: TelemetryPersistence         = JdbcTelemetryPersistence(managementDatabase)
  override val deployMutactionExecutor: DeployMutactionExecutor   = JdbcDeployMutactionExecutor(mutationBuilder)
  override def databaseInspector: DatabaseInspector               = SQLiteDatabaseInspector(managementDatabase)
  override def capabilities: ConnectorCapabilities                = ConnectorCapabilities.sqliteJdbcPrototype

  override def createProjectDatabase(id: String): Future[Unit] = {
    val action = mutationBuilder.createDatabaseForProject(id = id)
    projectDatabase.run(action)
  }

  override def deleteProjectDatabase(id: String): Future[Unit] = {
    val action = mutationBuilder.deleteProjectDatabase(projectId = id).map(_ => ())
    projectDatabase.run(action)
  }

  override def getAllDatabaseSizes(): Future[Vector[DatabaseSize]] = {
    val action = {
      val query = sql"""SELECT table_schema, sum( data_length + index_length) / 1024 / 1024 FROM information_schema.TABLES GROUP BY table_schema"""
      query.as[(String, Double)].map { tuples =>
        tuples.map { tuple =>
          DatabaseSize(tuple._1, tuple._2)
        }
      }
    }

    projectDatabase.run(action)
  }

  override def clientDBQueries(project: Project): ClientDbQueries      = JdbcClientDbQueries(project, databases.primary)
  override def getOrCreateTelemetryInfo(): Future[TelemetryInfo]       = telemetryPersistence.getOrCreateInfo()
  override def updateTelemetryInfo(lastPinged: DateTime): Future[Unit] = telemetryPersistence.updateTelemetryInfo(lastPinged)
  override def projectIdEncoder: ProjectIdEncoder                      = ProjectIdEncoder('_')

  override def initialize(): Future[Unit] = {
    setupDatabase.primary.database
      .run(SQLiteInternalDatabaseSchema.createSchemaActions(internalDatabaseDefs.managementSchemaName, recreate = false))
      .flatMap(_ => internalDatabaseDefs.setupDatabases.shutdown)
  }

  override def reset(): Future[Unit]          = truncateTablesInDatabase(managementDatabase.database)
  override def shutdown(): Future[Unit]       = databases.shutdown
  override def managementLock(): Future[Unit] = Future.unit

  protected def truncateTablesInDatabase(database: Database)(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      schemas <- database.run(getTables(internalDatabaseDefs.managementSchemaName))
      _       <- database.run(dangerouslyTruncateTables(schemas))
    } yield ()
  }

  private def getTables(database: String)(implicit ec: ExecutionContext): DBIOAction[Vector[String], NoStream, Read] = {
    for {
      metaTables <- MTable.getTables(cat = Some(database), schemaPattern = None, namePattern = None, types = None)
    } yield metaTables.map(table => table.name.name)
  }

  private def dangerouslyTruncateTables(tableNames: Vector[String]): DBIOAction[Unit, NoStream, Effect] = {
    DBIO.seq(
      List(sqlu"""PRAGMA FOREIGN_KEYS=OFF""") ++
        tableNames.map(name => sqlu"DELETE FROM `#$name`") ++
        List(sqlu"""PRAGMA FOREIGN_KEYs=ON"""): _*
    )
  }
}
