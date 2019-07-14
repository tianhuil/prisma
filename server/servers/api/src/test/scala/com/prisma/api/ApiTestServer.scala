package com.prisma.api

import java.io.{BufferedReader, InputStreamReader}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import java.util.Base64

import com.prisma.api.schema.{ApiUserContext, PrivateSchemaBuilder, SchemaBuilder}
import com.prisma.graphql.{GraphQlClient, GraphQlResponse}
import com.prisma.shared.models.Project
import com.prisma.utils.json.PlayJsonExtensions
import play.api.libs.json._
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import sangria.schema.Schema

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Awaitable, Future}
import scala.reflect.io.File

trait ApiTestServer extends PlayJsonExtensions {
  System.setProperty("org.jooq.no-logo", "true")

  val dependencies: ApiDependencies

  /**
    * Execute a Query that must succeed.
    */
  def query(
      query: String,
      project: Project,
      dataContains: String = "",
      variables: JsValue = JsObject.empty,
      requestId: String = "CombinedTestDatabase.requestId"
  ): JsValue = awaitInfinitely {
    queryAsync(query, project, dataContains, variables, requestId)
  }

  def queryAsync(
      query: String,
      project: Project,
      dataContains: String = "",
      variables: JsValue = JsObject.empty,
      requestId: String = "CombinedTestDatabase.requestId"
  ): Future[JsValue]

  /**
    * Execute a Query that must fail.
    */
  def queryThatMustFail(
      query: String,
      project: Project,
      errorCode: Int,
      errorCount: Int = 1,
      errorContains: String = "",
      userId: Option[String] = None,
      variables: JsValue = JsObject.empty,
      requestId: String = "CombinedTestDatabase.requestId"
  ): JsValue

  protected def querySchemaAsync(
      query: String,
      project: Project,
      schema: Schema[ApiUserContext, Unit],
      variables: JsValue,
      requestId: String
  ): Future[JsValue]

  def queryPrivateSchema(query: String, project: Project, variables: JsObject = JsObject.empty): JsValue = {
    val schemaBuilder = PrivateSchemaBuilder(project)(dependencies)
    awaitInfinitely {
      querySchemaAsync(
        query = query,
        project = project,
        schema = schemaBuilder.build(),
        variables = variables,
        requestId = "private-api-request"
      )
    }
  }

  protected def awaitInfinitely[T](awaitable: Awaitable[T]): T = Await.result(awaitable, Duration.Inf)
}

case class ExternalApiTestServer()(implicit val dependencies: ApiDependencies) extends ApiTestServer {
  import com.prisma.shared.models.ProjectJsonFormatter._
  import dependencies.system.dispatcher

  implicit val system       = dependencies.system
  implicit val materializer = dependencies.materializer

  val prismaBinaryPath: String = sys.env.getOrElse("PRISMA_BINARY_PATH", sys.error("Required PRISMA_BINARY_PATH env var not found"))
  val gqlClient                = GraphQlClient("http://127.0.0.1:4466")
  val server_root = sys.env
    .get("SERVER_ROOT")
    .orElse(sys.env.get("BUILDKITE_BUILD_CHECKOUT_PATH").map(path => s"$path/server"))
    .getOrElse(sys.error("Unable to resolve server root path"))

  val prismaConfigTemplate =
    """
      |port: 4466
      |prototype: true
      |databases:
      |  default:
      |    connector: sqlite-native
      |    databaseFile: $DB_FILE
      |    migrations: true
      |    active: true
      |    rawAccess: true
      |    testMode: true
    """.stripMargin

  def renderConfig(dbName: String): String = {
    prismaConfigTemplate.replaceAllLiterally("$DB_FILE", s"$server_root/db/${dbName}_DB.db")
  }

  def queryPrismaProcess(query: String): GraphQlResponse = {
    val url = new URL("http://127.0.0.1:4466")
    val con = url.openConnection().asInstanceOf[HttpURLConnection]

    con.setDoOutput(true)
    con.setRequestMethod("POST")
    con.setRequestProperty("Content-Type", "application/json")

    val body = Json.obj("query" -> query, "variables" -> Json.obj()).toString()
    con.setRequestProperty("Content-Length", Integer.toString(body.length))
    con.getOutputStream.write(body.getBytes(StandardCharsets.UTF_8))

    try {
      val status = con.getResponseCode
      val streamReader = if (status > 299) {
        new InputStreamReader(con.getErrorStream)
      } else {
        new InputStreamReader(con.getInputStream)
      }

      val in     = new BufferedReader(streamReader)
      val buffer = new StringBuffer

      Stream.continually(in.readLine()).takeWhile(_ != null).foreach(buffer.append)
      GraphQlResponse(status, buffer.toString)
    } catch {
      case e: Throwable => GraphQlResponse(999, s"""{"errors": [{"message": "Connection error: $e"}]}""")
    } finally {
      con.disconnect()
    }
  }

  def startPrismaProcess(project: Project): java.lang.Process = {
    import java.lang.ProcessBuilder.Redirect

    val pb         = new java.lang.ProcessBuilder(prismaBinaryPath)
    val workingDir = new java.io.File(".")

    // Important: Rust requires UTF-8 encoding (encodeToString uses Latin-1)
    val encoded   = Base64.getEncoder.encode(Json.toJson(project.schema).toString().getBytes(StandardCharsets.UTF_8))
    val schemaEnv = new String(encoded, StandardCharsets.UTF_8)
    val config    = renderConfig(project.id)
    val env       = pb.environment

    env.put("PRISMA_CONFIG", config)
    env.put("PRISMA_INTERNAL_DATA_MODEL_JSON", schemaEnv)

    pb.directory(workingDir)
    pb.redirectErrorStream(true)
    pb.redirectOutput(Redirect.INHERIT)

    val p = pb.start
    Thread.sleep(50) // Offsets process startup latency
    p
  }

  override def queryAsync(query: String, project: Project, dataContains: String, variables: JsValue, requestId: String): Future[JsValue] = {
    val schemaBuilder = SchemaBuilder()(dependencies)
    val result = querySchemaAsync(
      query = query.stripMargin,
      project = project,
      schema = schemaBuilder(project),
      variables = variables,
      requestId = requestId
    )

    result.map { r =>
      r.assertSuccessfulResponse(dataContains)
      r
    }
  }

  override protected def querySchemaAsync(query: String,
                                          project: Project,
                                          schema: Schema[ApiUserContext, Unit],
                                          variables: JsValue,
                                          requestId: String): Future[JsValue] = {
    // Decide whether to go through the external server or internal resolver
    if (query.trim().stripPrefix("\n").startsWith("mutation")) {
      val queryAst = QueryParser.parse(query.stripMargin).get
      val result = dependencies.queryExecutor.execute(
        requestId = requestId,
        queryString = query,
        queryAst = queryAst,
        variables = variables,
        operationName = None,
        project = project,
        schema = schema
      )

      result.foreach(x => println(s"""Request Result:
         |$x
     """.stripMargin))
      result
    } else {
      val prismaProcess = startPrismaProcess(project)

      Future {
        println(prismaProcess.isAlive)
        queryPrismaProcess(query)
      }.map(r => r.jsonBody.get)
        .transform(r => {
          println(s"Query result: $r")
          prismaProcess.destroyForcibly().waitFor()
          r
        })
    }
  }

  override def queryThatMustFail(query: String,
                                 project: Project,
                                 errorCode: Int,
                                 errorCount: Int,
                                 errorContains: String,
                                 userId: Option[String],
                                 variables: JsValue,
                                 requestId: String): JsValue = {
    val schemaBuilder = SchemaBuilder()(dependencies)
    val result = awaitInfinitely {
      querySchemaAsync(
        query = query,
        project = project,
        schema = schemaBuilder(project),
        variables = variables,
        requestId = requestId
      )
    }

    result.assertFailingResponse(errorCode, errorCount, errorContains)
    result
  }
}

case class InternalApiTestServer()(implicit val dependencies: ApiDependencies) extends ApiTestServer {
  import dependencies.system.dispatcher

  def writeSchemaIntoFile(schema: String): Unit = File("schema").writeAll(schema)

  def printSchema: Boolean = false
  def writeSchemaToFile    = false
  def logSimple: Boolean   = false

  def queryAsync(
      query: String,
      project: Project,
      dataContains: String = "",
      variables: JsValue = JsObject.empty,
      requestId: String = "CombinedTestDatabase.requestId"
  ): Future[JsValue] = {
    val schemaBuilder = SchemaBuilder()(dependencies)
    val result = querySchemaAsync(
      query = query.stripMargin,
      project = project,
      schema = schemaBuilder(project),
      variables = variables,
      requestId = requestId
    )

    result.map { r =>
      r.assertSuccessfulResponse(dataContains)
      r
    }
  }

  def queryThatMustFail(
      query: String,
      project: Project,
      errorCode: Int,
      errorCount: Int = 1,
      errorContains: String = "",
      userId: Option[String] = None,
      variables: JsValue = JsObject.empty,
      requestId: String = "CombinedTestDatabase.requestId"
  ): JsValue = {
    val schemaBuilder = SchemaBuilder()(dependencies)
    val result = awaitInfinitely {
      querySchemaAsync(
        query = query,
        project = project,
        schema = schemaBuilder(project),
        variables = variables,
        requestId = requestId
      )
    }

    result.assertFailingResponse(errorCode, errorCount, errorContains)
    result
  }

  def querySchemaAsync(
      query: String,
      project: Project,
      schema: Schema[ApiUserContext, Unit],
      variables: JsValue,
      requestId: String
  ): Future[JsValue] = {
    val queryAst = QueryParser.parse(query.stripMargin).get

    lazy val renderedSchema = SchemaRenderer.renderSchema(schema)
    if (printSchema) println(renderedSchema)
    if (writeSchemaToFile) writeSchemaIntoFile(renderedSchema)

    val result = dependencies.queryExecutor.execute(
      requestId = requestId,
      queryString = query,
      queryAst = queryAst,
      variables = variables,
      operationName = None,
      project = project,
      schema = schema
    )

    result.foreach(x => println(s"""Request Result:
        |$x
      """.stripMargin))
    result
  }

}
