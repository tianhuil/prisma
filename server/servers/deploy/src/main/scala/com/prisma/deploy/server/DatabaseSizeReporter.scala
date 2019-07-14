package com.prisma.deploy.server

import akka.actor.Actor
import com.prisma.akkautil.LogUnhandled
import com.prisma.deploy.connector.persistence.ProjectPersistence
import com.prisma.deploy.connector.{DatabaseSize, DeployConnector}
import com.prisma.deploy.server.DatabaseSizeReporter.Report
import com.prisma.metrics.{CustomTag, GaugeMetric, MetricsFacade}
import com.prisma.shared.models.Project

import scala.collection.mutable
import scala.concurrent.Future

object DatabaseSizeReporter {
  object Report
}

case class DatabaseSizeReporter(
    projectPersistence: ProjectPersistence,
    deployConnector: DeployConnector,
    metricsFacade: MetricsFacade
) extends Actor
    with LogUnhandled {
  import context.dispatcher

  import scala.concurrent.duration._

  scheduleReport()

  val projectIdTag = CustomTag("projectId")
  val gauges       = mutable.Map.empty[String, GaugeMetric]

  override def receive = logUnhandled {
    case Report =>
      for {
        projects      <- projectPersistence.loadAll()
        databaseSizes <- getAllDatabaseSizes()
      } yield {
        projects.foreach { project =>
          databaseSizes.find(_.name == project.id).foreach { dbSize =>
            val gauge = gaugeForProject(project)
            gauge.set(dbSize.total.toLong)
          }
        }
        scheduleReport()
      }
  }

  def scheduleReport() = context.system.scheduler.scheduleOnce(5.minutes, self, Report)

  def gaugeForProject(project: Project): GaugeMetric = {
    // these Metrics are consumed by the console to power the dashboard. Only change them with extreme caution!
    gauges.getOrElseUpdate(project.id, {
      metricsFacade.defineGauge("projectDatabase.sizeInMb", (projectIdTag, project.id))
    })
  }

  def getAllDatabaseSizes(): Future[Vector[DatabaseSize]] = deployConnector.getAllDatabaseSizes()
}
