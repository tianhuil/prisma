package com.prisma

import com.prisma.config.{DatabaseConfig, PrismaConfig}
import com.prisma.shared.models.{ConnectorCapabilities, ConnectorCapability}
import enumeratum.{Enum, EnumEntry}
import org.scalatest.{Suite, SuiteMixin, Tag}

sealed trait AssociatedWithConnectorTags {
  def tag: ConnectorTag
}

object IgnorePostgres extends Tag("ignore.postgres") with AssociatedWithConnectorTags {
  override def tag = ConnectorTag.PostgresConnectorTag
}
object IgnoreMySql extends Tag("ignore.mysql") with AssociatedWithConnectorTags {
  override def tag = ConnectorTag.MySqlConnectorTag
}
object IgnoreMongo extends Tag("ignore.mongo") with AssociatedWithConnectorTags {
  override def tag = ConnectorTag.MongoConnectorTag
}
object IgnoreSQLite extends Tag("ignore.sqlite") with AssociatedWithConnectorTags {
  override def tag = ConnectorTag.SQLiteConnectorTag
}

object IgnoreSet {
  val ignoreConnectorTags = Set(IgnorePostgres, IgnoreMySql, IgnoreMongo, IgnoreSQLite)

  def byName(name: String): Option[AssociatedWithConnectorTags] = ignoreConnectorTags.find(_.name == name)
}

sealed trait ConnectorTag extends EnumEntry
object ConnectorTag extends Enum[ConnectorTag] {
  def values = findValues

  sealed trait RelationalConnectorTag extends ConnectorTag
  object MySqlConnectorTag            extends RelationalConnectorTag
  object PostgresConnectorTag         extends RelationalConnectorTag
  object SQLiteConnectorTag           extends RelationalConnectorTag
  sealed trait DocumentConnectorTag   extends ConnectorTag
  object MongoConnectorTag            extends DocumentConnectorTag
}

trait ConnectorAwareTest extends SuiteMixin { self: Suite =>
  import IgnoreSet._
  def prismaConfig: PrismaConfig

  lazy val connector = prismaConfig.databases.head
  lazy val connectorTag = connector.connector match {
    case "mongo"                                                 => ConnectorTag.MongoConnectorTag
    case "mysql"                                                 => ConnectorTag.MySqlConnectorTag
    case "postgres" | "postgres-native"                          => ConnectorTag.PostgresConnectorTag
    case "sqlite" | "sqlite-native" | "native-integration-tests" => ConnectorTag.SQLiteConnectorTag
  }
  private lazy val isPrototype: Boolean = prismaConfig.isPrototype

  def capabilities: ConnectorCapabilities
  def runOnlyForConnectors: Set[ConnectorTag]           = ConnectorTag.values.toSet
  def doNotRunForConnectors: Set[ConnectorTag]          = Set.empty
  def runOnlyForCapabilities: Set[ConnectorCapability]  = Set.empty
  def doNotRunForCapabilities: Set[ConnectorCapability] = Set.empty
  def doNotRunForPrototypes: Boolean                    = false
  def doNotRun: Boolean                                 = false

  abstract override def tags: Map[String, Set[String]] = { // this must NOT be a val. Otherwise ScalaTest does not behave correctly.
    if (shouldSuiteBeIgnored || doNotRun) {
      ignoreAllTests
    } else {
      ignoredTestsBasedOnIndividualTagging(connector)
    }
  }

  private lazy val shouldSuiteBeIgnored: Boolean = { // this must be a val. Otherwise printing would happen many times.
    val connectorHasTheRightCapabilities = runOnlyForCapabilities.forall(capabilities.has) || runOnlyForCapabilities.isEmpty
    val connectorHasAWrongCapability     = doNotRunForCapabilities.exists(capabilities.has)
    val isTheRightConnector              = runOnlyForConnectors.contains(connectorTag) && !doNotRunForConnectors.contains(connectorTag)

    if (!isTheRightConnector) {
      println(
        s"""the suite ${self.getClass.getSimpleName} will be ignored because the current connector is not right
           | allowed connectors: ${runOnlyForConnectors.mkString(",")}
           | disallowed connectors: ${doNotRunForConnectors.mkString(",")}
           | current connector: $connectorTag
         """.stripMargin
      )
      true
    } else if (isPrototype && doNotRunForPrototypes) {
      println(
        s"""the suite ${self.getClass.getSimpleName} will be ignored because it should not run for prototypes and the current connector is a prototype
           | current connector: $connectorTag
         """.stripMargin
      )
      true
    } else if (!connectorHasTheRightCapabilities) {
      println(
        s"""the suite ${self.getClass.getSimpleName} will be ignored because it does not have the right capabilities
           | required capabilities: ${runOnlyForCapabilities.mkString(",")}
           | connector capabilities: ${capabilities.capabilities.mkString(",")}
         """.stripMargin
      )
      true
    } else if (connectorHasAWrongCapability) {
      println(
        s"""the suite ${self.getClass.getSimpleName} will be ignored because it has a wrong capability
           | wrong capabilities: ${doNotRunForCapabilities.mkString(",")}
           | connector capabilities: ${capabilities.capabilities.mkString(",")}
         """.stripMargin
      )
      true
    } else {
      false
    }
  }

  def ifConnectorIsNotSQLite[T](assertion: => T): Unit = if (connectorTag != ConnectorTag.SQLiteConnectorTag) assertion
  def ifConnectorIsSQLite[T](assertion: => T): Unit    = if (connectorTag == ConnectorTag.SQLiteConnectorTag) assertion
  def ifConnectorIsNotMongo[T](assertion: => T): Unit  = if (connectorTag != ConnectorTag.MongoConnectorTag) assertion
  def ifConnectorIsActive[T](assertion: => T): Unit = {
    // FIXME: check if we need can bring this back, discuss with do4gr
//    if (connector.active && connectorTag != ConnectorTag.MongoConnectorTag) assertion
  }

  private def ignoredTestsBasedOnIndividualTagging(connector: DatabaseConfig) = {
    super.tags.mapValues { tagNames =>
      val connectorTagsToIgnore: Set[ConnectorTag] = for {
        tagName   <- tagNames
        ignoreTag <- IgnoreSet.byName(tagName)
      } yield ignoreTag.tag

      val isIgnored = connectorTagsToIgnore.contains(connectorTag)
      if (isIgnored) {
        tagNames ++ Set("org.scalatest.Ignore")
      } else {
        tagNames
      }
    }
  }

  private def ignoreAllTests = {
    testNames.map { testName =>
      testName -> Set("org.scalatest.Ignore")
    }.toMap
  }
}
