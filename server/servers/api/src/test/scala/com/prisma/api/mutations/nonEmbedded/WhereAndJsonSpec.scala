package com.prisma.api.mutations.nonEmbedded

import com.prisma.api.ApiSpecBase
import com.prisma.shared.models.ConnectorCapability.{JoinRelationLinksCapability, RelationLinkListCapability}
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class WhereAndJsonSpec extends FlatSpec with Matchers with ApiSpecBase {
  override def runOnlyForCapabilities = Set(JoinRelationLinksCapability)

  "Using the same input in an update using where as used during creation of the item" should "work" in {

    val outerWhere = """"{\"stuff\": 1, \"nestedStuff\" : {\"stuff\": 2 } }""""
    val innerWhere = """"{\"stuff\": 2, \"nestedStuff\" : {\"stuff\": 1, \"nestedStuff\" : {\"stuff\": 2 } } }""""

    val project = SchemaDsl.fromStringV11() {
      s"""type Note{
        |   id: ID! @id
        |   outerString: String!
        |   outerJson: Json! @unique
        |   todos: [Todo] $listInlineDirective
        |}
        |
        |type Todo{
        |   id: ID! @id
        |   innerString: String!
        |   innerJson: Json! @unique
        |   notes: [Note]
        |}"""
    }

    database.setup(project)

    val createResult = server.query(
      s"""mutation {
         |  createNote(
         |    data: {
         |      outerString: "Outer String"
         |      outerJson: $outerWhere
         |      todos: {
         |        create: [
         |        {innerString: "Inner String", innerJson: $innerWhere}
         |        ]
         |      }
         |    }
         |  ){
         |    id
         |  }
         |}""".stripMargin,
      project
    )

    server.query(
      s"""
         |mutation {
         |  updateNote(
         |    where: { outerJson: $outerWhere }
         |    data: {
         |      outerString: "Changed Outer String"
         |      todos: {
         |        update: [
         |        {where: { innerJson: $innerWhere },data:{ innerString: "Changed Inner String"}}
         |        ]
         |      }
         |    }
         |  ){
         |    id
         |  }
         |}
      """.stripMargin,
      project
    )

    server.query(s"""query{note(where:{outerJson:$outerWhere}){outerString}}""", project, dataContains = s"""{"note":{"outerString":"Changed Outer String"}}""")
    server.query(s"""query{todo(where:{innerJson:$innerWhere}){innerString}}""", project, dataContains = s"""{"todo":{"innerString":"Changed Inner String"}}""")
  }
}
