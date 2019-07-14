package com.prisma.integration

import com.prisma.IgnoreSQLite
import com.prisma.IgnoreMongo
import org.scalatest.{FlatSpec, Matchers}

class SeveralRelationsBetweenSameModelsIntegrationSpec extends FlatSpec with Matchers with IntegrationBaseSpec {

  "DeployMutation" should "be able to handle more than two relations between models" in {

    val schema =
      """type A {
        |  id: ID! @id
        |  title: String
        |  # b1: B @relation(name: "AB1")
        |  # b2: B @relation(name: "AB2")
        |  # b3: B @relation(name: "AB3")
        |}
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |  # a1: A @relation(name: "AB1")
        |  # a2: A @relation(name: "AB2")
        |  # a3: A @relation(name: "AB3")
        |}"""

    val (project, _) = setupProject(schema)

    project.schema.relations.size should be(0)

    val schema1 =
      """type A {
        |  id: ID! @id
        |  title: String
        |  b1: B @relation(name: "AB1", link: INLINE)
        |  b2: B @relation(name: "AB2", link: INLINE)
        |  # b3: B @relation(name: "AB3")
        |}
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |  a1: A @relation(name: "AB1")
        |  a2: A @relation(name: "AB2")
        |  # a3: A @relation(name: "AB3")
        |}"""

    val updatedProject = deployServer.deploySchema(project, schema1)

    apiServer.query("""mutation{createA(data:{title:"A1" b1:{create:{title: "B1"}}, b2:{create:{title: "B2"}}}){id}}""", updatedProject)

    updatedProject.schema.relations.size should be(2)
    updatedProject.schema.relations(0).name should be("""AB1""")
    updatedProject.schema.relations(1).name should be("""AB2""")

    val schema2 =
      """type A {
        |  id: ID! @id
        |  title: String
        |  b1: B @relation(name: "AB1", link: INLINE)
        |  b2: B @relation(name: "AB2", link: INLINE)
        |  b3: B @relation(name: "AB3", link: INLINE)
        |}
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |  a1: A @relation(name: "AB1")
        |  a2: A @relation(name: "AB2")
        |  a3: A @relation(name: "AB3")
        |}"""

    val updatedProject2 = deployServer.deploySchema(project, schema2)

    updatedProject2.schema.relations.size should be(3)
    updatedProject2.schema.relations(0).name should be("""AB1""")
    updatedProject2.schema.relations(1).name should be("""AB3""")
    updatedProject2.schema.relations(2).name should be("""AB2""")

    val unchangedRelationContent = apiServer.query("""{as{title, b1{title},b2{title},b3{title}}}""", updatedProject2)

    unchangedRelationContent.toString should be("""{"data":{"as":[{"title":"A1","b1":{"title":"B1"},"b2":{"title":"B2"},"b3":null}]}}""")
  }

  "DeployMutation" should "be able to handle setting a new relation with a name" in {

    val schema =
      """type A {
        |  id: ID! @id
        |  title: String
        | }
        |
        |type B {
        |  id: ID! @id
        |  title: String
        | }"""

    val (project, _) = setupProject(schema)

    project.schema.relations.size should be(0)

    val schema1 =
      """type A {
        |  id: ID! @id
        |  title: String
        |  b1: B @relation(name: "NewName", link: INLINE)
        |}
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |  a1: A @relation(name: "NewName")
        |}"""

    val updatedProject = deployServer.deploySchema(project, schema1)

    updatedProject.schema.relations.size should be(1)
    updatedProject.schema.relations.head.name should be("""NewName""")
  }

  "DeployMutation" should "be able to handle renaming relations that don't have a name yet" taggedAs (IgnoreSQLite) in {

    val schema =
      """type A {
        |  id: ID! @id
        |  title: String
        |  b1: B @relation(link: INLINE)
        | }
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |  a1: A
        | }"""

    val (project, _) = setupProject(schema)

    project.schema.relations.size should be(1)
    project.schema.relations.head.name should be("""AToB""")

    apiServer.query("""mutation{createA(data:{title:"A1" b1:{create:{title: "B1"}}}){id}}""", project)

    val schema1 =
      """type A {
        |  id: ID! @id
        |  title: String
        |  b1: B @relation(name: "NewName", link: INLINE)
        |}
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |  a1: A @relation(name: "NewName")
        |}"""

    val updatedProject = deployServer.deploySchema(project, schema1)

    updatedProject.schema.relations.size should be(1)
    updatedProject.schema.relations.head.name should be("""NewName""")

    val unchangedRelationContent = apiServer.query("""{as{title, b1{title}}}""", updatedProject)

    unchangedRelationContent.toString should be("""{"data":{"as":[{"title":"A1","b1":{"title":"B1"}}]}}""")
  }

  "DeployMutation" should "be able to handle renaming relations that are already named" taggedAs (IgnoreSQLite) in {

    val schema =
      """type A {
        |  id: ID! @id
        |  title: String
        |  b1: B @relation(name: "AB1", link: INLINE)
        | }
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |  a1: A @relation(name: "AB1")
        | }"""

    val (project, _) = setupProject(schema)

    project.schema.relations.size should be(1)
    project.schema.relations.head.name should be("""AB1""")

    apiServer.query("""mutation{createA(data:{title:"A1" b1:{create:{title: "B1"}}}){id}}""", project)

    val schema1 =
      """type A {
        |  id: ID! @id
        |  title: String
        |  b1: B @relation(oldName: "AB1", name: "NewName", link: INLINE)
        |}
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |  a1: A @relation(oldName: "AB1", name: "NewName")
        |}"""

    val updatedProject = deployServer.deploySchema(project, schema1)

    updatedProject.schema.relations.size should be(1)
    updatedProject.schema.relations.head.name should be("""NewName""")

    val unchangedRelationContent = apiServer.query("""{as{title, b1{title}}}""", updatedProject)

    unchangedRelationContent.toString should be("""{"data":{"as":[{"title":"A1","b1":{"title":"B1"}}]}}""")
  }

  "Going from two named relations between the same models to one unnamed one" should "error due to ambiguity" in {

    val schema =
      """type A {
        |  id: ID! @id
        |  b: B @relation(name: "AB1", link: INLINE)
        |  b2: B @relation(name: "AB2", link: INLINE)
        | }
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |  a: A @relation(name: "AB1")
        |  a2: A @relation(name: "AB2")
        | }"""

    val (project, _) = setupProject(schema)

    project.schema.relations.size should be(2)
    project.schema.relations.head.name should be("""AB1""")
    project.schema.relations.last.name should be("""AB2""")

    val schema1 =
      """type A {
        |  id: ID! @id
        |  b: B @relation(link: INLINE)
        |}
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |}"""

    deployServer.deploySchemaThatMustErrorWithCode(project, schema1, errorCode = 3018)
  }

  "Going from two named relations between the same models to one named one without a backrelation" should "work" taggedAs (IgnoreMongo) in {

    val schema =
      """type A {
        |  id: ID! @id
        |  title: String
        |  b: B @relation(name: "AB1", link: INLINE)
        |  b2: B @relation(name: "AB2", link: INLINE)
        | }
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |  a: A @relation(name: "AB1")
        |  a2: A @relation(name: "AB2")
        | }"""

    val (project, _) = setupProject(schema)

    project.schema.relations.size should be(2)
    project.schema.relations.head.name should be("""AB1""")
    project.schema.relations.last.name should be("""AB2""")

    apiServer.query("""mutation{createA(data:{title:"A1" b:{create:{title: "B1"}}}){id}}""", project)

    val schema1 =
      """type A {
        |  id: ID! @id
        |  title: String
        |  b: B @relation(name: "AB1", link: INLINE)
        |}
        |
        |type B {
        |  id: ID! @id
        |  title: String
        |}"""

    val updatedProject = deployServer.deploySchema(project, schema1)

    updatedProject.schema.relations.size should be(1)
    updatedProject.schema.relations.head.name should be("""AB1""")

    val unchangedRelationContent = apiServer.query("""{as{title, b{title}}}""", updatedProject)

    unchangedRelationContent.toString should be("""{"data":{"as":[{"title":"A1","b":{"title":"B1"}}]}}""")
  }

  "Going from two named relations between the same models to one named one without a backrelation" should "work even when there is a rename" taggedAs (IgnoreMongo, IgnoreSQLite) in {

    val schema =
      """type A {
          |  id: ID! @id
          |  title: String
          |  b: B @relation(name: "AB1", link: INLINE)
          |  b2: B @relation(name: "AB2", link: INLINE)
          | }
          |
          |type B {
          |  id: ID! @id
          |  title: String
          |  a: A @relation(name: "AB1")
          |  a2: A @relation(name: "AB2")
          | }"""

    val (project, _) = setupProject(schema)

    project.schema.relations.size should be(2)
    project.schema.relations.head.name should be("""AB1""")
    project.schema.relations.last.name should be("""AB2""")

    apiServer.query("""mutation{createA(data:{title:"A1" b:{create:{title: "B1"}}}){id}}""", project)

    val schema1 =
      """type A {
          |  id: ID! @id
          |  title: String
          |  b: B @relation(name: "AB2" oldName: "AB1", link: INLINE)
          |}
          |
          |type B {
          |  id: ID! @id
          |  title: String
          |}"""

    val updatedProject = deployServer.deploySchema(project, schema1)

    updatedProject.schema.relations.size should be(1)
    updatedProject.schema.relations.head.name should be("""AB2""")

    val unchangedRelationContent = apiServer.query("""{as{title, b{title}}}""", updatedProject)

    unchangedRelationContent.toString should be("""{"data":{"as":[{"title":"A1","b":{"title":"B1"}}]}}""")
  }

  "Several missing backrelations on the same type" should "work when there are relation directives provided" in {

    val schema =
      """type TeamMatch {
        |  id: ID! @id
        |  name: String! @unique
        |}
        |
        |type Match {
        |  id: ID! @id
        |  number: Int @unique
        |}"""

    val (project, _) = setupProject(schema)

    val schema1 =
      """type Team {
        |  id: ID! @id
        |  name: String! @unique
        |}
        |
        |type Match {
        |  id: ID! @id
        |  number: Int @unique
        |  teamLeft: Team @relation(name: "TeamMatchLeft", link: INLINE)
        |  teamRight: Team @relation(name: "TeamMatchRight", link: INLINE)
        |  winner: Team @relation(name: "TeamMatchWinner", link: INLINE)
        |}"""
    val updatedProject = deployServer.deploySchema(project, schema1)

    updatedProject.schema.relations.size should be(3)

    apiServer.query(
      """mutation{createMatch(data:{
        |                           number:1
        |                           teamLeft:{create:{name: "Bayern"}},
        |                           teamRight:{create:{name: "Real"}},
        |                           winner:{create:{name: "Real2"}}
        |                           }                           
        |){number}}""",
      updatedProject
    )

    val matches = apiServer.query("""{matches{number, teamLeft{name},teamRight{name},winner{name}}}""", updatedProject)
    matches.toString should be("""{"data":{"matches":[{"number":1,"teamLeft":{"name":"Bayern"},"teamRight":{"name":"Real"},"winner":{"name":"Real2"}}]}}""")

    val teams = apiServer.query("""{teams{name}}""", updatedProject)
    teams.toString should be("""{"data":{"teams":[{"name":"Bayern"},{"name":"Real"},{"name":"Real2"}]}}""")

  }

  // should move to schemavalidationspec

  "One missing backrelation and one unnamed relation on the other side" should "error" in {

    val (project, _) = setupProject(basicTypesGql)
    val schema1 =
      """type TeamMatch {
        |  id: ID! @id
        |  key: String! @unique
        |  match: Match
        |}
        |
        |type Match {
        |  id: ID! @id
        |  number: Int @unique
        |  teamLeft: TeamMatch @relation(name: "TeamMatchLeft")
        |}"""

    val res = deployServer.deploySchemaThatMustError(project, schema1)
    res.toString should be(
      """{"data":{"deploy":{"migration":null,"errors":[{"description":"You are trying to set the relation 'TeamMatchLeft' from `Match` to `TeamMatch` and are only providing a relation directive with a name on `Match`. Please also provide the same named relation directive on the relation field on `TeamMatch` pointing towards `Match`."}],"warnings":[]}}}""")
  }

  "Several missing backrelations on the same type and one unnamed relation on the other side" should "error" in {
    val (project, _) = setupProject(basicTypesGql)

    val schema1 =
      """type TeamMatch {
        |  id: ID! @id
        |  key: String! @unique
        |  match: Match
        |}
        |
        |type Match {
        |  id: ID! @id
        |  number: Int @unique
        |  teamLeft: TeamMatch @relation(name: "TeamMatchLeft")
        |  teamRight: TeamMatch @relation(name: "TeamMatchRight")
        |  winner: TeamMatch @relation(name: "TeamMatchWinner")
        |}"""

    val res = deployServer.deploySchemaThatMustError(project, schema1)
    res.toString should be(
      """{"data":{"deploy":{"migration":null,"errors":[{"description":"You are trying to set the relation 'TeamMatchLeft' from `Match` to `TeamMatch` and are only providing a relation directive with a name on `Match`. Please also provide the same named relation directive on the relation field on `TeamMatch` pointing towards `Match`."},{"description":"You are trying to set the relation 'TeamMatchRight' from `Match` to `TeamMatch` and are only providing a relation directive with a name on `Match`. Please also provide the same named relation directive on the relation field on `TeamMatch` pointing towards `Match`."},{"description":"You are trying to set the relation 'TeamMatchWinner' from `Match` to `TeamMatch` and are only providing a relation directive with a name on `Match`. Please also provide the same named relation directive on the relation field on `TeamMatch` pointing towards `Match`."}],"warnings":[]}}}""")
  }

  "Several missing backrelation to different models" should "work" in {

    val (project, _) = setupProject(basicTypesGql)
    val schema1 =
      """type TeamMatch {
        |  id: ID! @id
        |  key: String! @unique
        |}
        |
        |type TeamMatch2 {
        |  id: ID! @id
        |  key: String! @unique
        |}
        |
        |type Match {
        |  id: ID! @id
        |  number: Int @unique
        |  teamLeft: TeamMatch @relation(name: "TeamMatchLeft", link: INLINE)
        |  teamLeft2: TeamMatch2 @relation(name: "TeamMatchLeft2", link: INLINE)
        |}"""

    deployServer.deploySchemaThatMustSucceed(project, schema1, 3)
  }

}
