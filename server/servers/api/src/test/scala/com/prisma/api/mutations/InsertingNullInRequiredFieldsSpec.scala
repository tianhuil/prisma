package com.prisma.api.mutations

import com.prisma.api.ApiSpecBase
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class InsertingNullInRequiredFieldsSpec extends FlatSpec with Matchers with ApiSpecBase {

  "Updating a required value to null" should "throw a proper error" in {
    val project = SchemaDsl.fromStringV11() {
      """type A {
        |  id: ID! @id
        |  b: String! @unique
        |  key: String!
        |}
      """
    }
    database.setup(project)

    server.query(
      """mutation a {
        |  createA(data: {
        |    b: "abc"
        |    key: "abc"
        |  }) {
        |    id
        |  }
        |}""",
      project
    )

    server.queryThatMustFail(
      """mutation b {
        |  updateA(
        |    where: { b: "abc" }
        |    data: {
        |      key: null
        |    }) {
        |    id
        |  }
        |}""",
      project,
      3020
    )
  }

  "Creating a required value as null" should "throw a proper error" in {
    val project = SchemaDsl.fromStringV11() {
      """type A {
        |  id: ID! @id
        |  b: String! @unique
        |  key: String!
        |}
      """
    }
    database.setup(project)

    server.queryThatMustFail(
      """mutation a {
        |  createA(data: {
        |    b: "abc"
        |    key: null
        |  }) {
        |    id
        |  }
        |}""",
      project,
      errorCode = 0,
      errorContains = """Argument 'data' expected type 'ACreateInput!' but got: {b: \"abc\", key: null}. Reason: 'key' String value expected"""
    )
  }

  "Updating an optional value to null" should "work" in {
    val project = SchemaDsl.fromStringV11() {
      """type A {
        |  id: ID! @id
        |  b: String! @unique
        |  key: String @unique
        |}
      """
    }
    database.setup(project)

    server.query(
      """mutation a {
        |  createA(data: {
        |    b: "abc"
        |    key: "abc"
        |  }) {
        |    id
        |  }
        |}""",
      project
    )

    server.query(
      """mutation b {
        |  updateA(
        |    where: { b: "abc" }
        |    data: {
        |      key: null
        |    }) {
        |    id
        |  }
        |}""",
      project
    )

    server.query("""query{as{b,key}}""", project).toString should be("""{"data":{"as":[{"b":"abc","key":null}]}}""")
  }

  "Creating an optional value as null" should "work" in {
    val project = SchemaDsl.fromStringV11() {
      """type A {
        |  id: ID! @id
        |  b: String! @unique
        |  key: String
        |}
      """
    }
    database.setup(project)

    server.query(
      """mutation a {
        |  createA(data: {
        |    b: "abc"
        |    key: null
        |  }) {
        |    b,
        |    key
        |  }
        |}""",
      project,
      dataContains = """{"createA":{"b":"abc","key":null}}"""
    )
  }

}
