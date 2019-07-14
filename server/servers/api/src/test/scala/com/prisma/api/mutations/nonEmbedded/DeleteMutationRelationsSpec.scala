package com.prisma.api.mutations.nonEmbedded

import com.prisma.api.{ApiSpecBase, TestDataModels}
import com.prisma.api.mutations.nonEmbedded.nestedMutations.SchemaBaseV11
import com.prisma.shared.models.ConnectorCapability.JoinRelationLinksCapability
import com.prisma.shared.models.ConnectorCapability
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class DeleteMutationRelationsSpec extends FlatSpec with Matchers with ApiSpecBase with SchemaBaseV11 {
  override def runOnlyForCapabilities: Set[ConnectorCapability] = Set(JoinRelationLinksCapability)

  "a P1! to C1! relation " should "error when deleting the parent2" in {
    val testDataModels = {
      val dm1 = """type Parent {
                  |  id: ID! @id
                  |  p: String! @unique
                  |  child: Child! @relation(link: INLINE)
                  |}
                  |
                  |type Child {
                  |  id: ID! @id
                  |  c: String! @unique
                  |  parentReq: Parent!
                  |}
                """.stripMargin

      val dm2 = """type Parent {
                  |  id: ID! @id
                  |  p: String! @unique
                  |  child: Child!
                  |}
                  |
                  |type Child {
                  |  id: ID! @id
                  |  c: String! @unique
                  |  parentReq: Parent! @relation(link: INLINE)
                  |}
                """.stripMargin

      TestDataModels(mongo = Vector(dm1, dm2), sql = Vector(dm1, dm2))
    }

    testDataModels.testV11 { project =>
      server
        .query(
          """mutation {
          |  createChild(data: {
          |    c: "c1"
          |    parentReq: {
          |      create: {p: "p1"}
          |    }
          |  }){
          |    id
          |  }
          |}""".stripMargin,
          project
        )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

      server.queryThatMustFail(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |  p
         |  }
         |}
      """.stripMargin,
        project,
        errorCode = 3042,
        errorContains = "The change you are trying to make would violate the required relation 'ChildToParent' between Child and Parent"
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
    }
  }

  "a P1! to C1! relation " should "error when deleting the parent" in {
    schemaP1reqToC1req.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      val res = server
        .query(
          """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childReq: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    id
          |    childReq{
          |       id
          |    }
          |  }
          |}""".stripMargin,
          project
        )
      val childId  = res.pathAsString("data.createParent.childReq.id")
      val parentId = res.pathAsString("data.createParent.id")

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

      server.queryThatMustFail(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {id: "$parentId"}
         |  ){
         |  p
         |  }
         |}
      """.stripMargin,
        project,
        errorCode = 3042,
        errorContains = "The change you are trying to make would violate the required relation 'ChildToParent' between Child and Parent"
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
    }
  }

  "a P1! to C1 relation" should "succeed when trying to delete the parent" in {
    schemaP1reqToC1opt.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      val res = server
        .query(
          """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childReq: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |  id
          |    childReq{
          |       id
          |    }
          |  }
          |}""".stripMargin,
          project
        )

      val parentId = res.pathAsString("data.createParent.id")

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {id: "$parentId"}
         |  ){
         |  p
         |  }
         |}
      """.stripMargin,
        project
      )
      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }
  }

  "a P1 to C1  relation " should "succeed when trying to delete the parent" in {
    schemaP1optToC1opt.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      val res = server
        .query(
          """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childOpt: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    id
          |    childOpt{
          |       id
          |    }
          |  }
          |}""".stripMargin,
          project
        )

      val parentId = res.pathAsString("data.createParent.id")

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {id: "$parentId"}
         |  ){
         |  p
         |  }
         |}
      """.stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }
  }

  "a P1 to C1  relation " should "succeed when trying to delete the parent if there are no children" in {
    schemaP1optToC1opt.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server
        .query(
          """mutation {
          |  createParent(data: {
          |    p: "p1"
          |  }){
          |    id
          |  }
          |}""".stripMargin,
          project
        )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |  p
         |  }
         |}
      """.stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }
  }

  "a PM to C1!  relation " should "error when deleting the parent" in {
    schemaPMToC1req.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server.query(
        """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

      server.queryThatMustFail(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |  p
         |  }
         |}
      """.stripMargin,
        project,
        errorCode = 3042,
        errorContains = "The change you are trying to make would violate the required relation 'ChildToParent' between Child and Parent"
      )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
    }
  }

  "a PM to C1!  relation " should "succeed if no child exists that requires the parent" in {
    schemaPMToC1req.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server.query(
        """mutation {
        |  createParent(data: {
        |    p: "p1"
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |  p
         |  }
         |}
      """.stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }

  }

  "a P1 to C1!  relation " should "error when trying to delete the parent" in {
    schemaP1optToC1req.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server.query(
        """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

      server.queryThatMustFail(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |  p
         |  }
         |}
      """.stripMargin,
        project,
        errorCode = 3042,
        errorContains = "The change you are trying to make would violate the required relation 'ChildToParent' between Child and Parent"
      )
      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }
    }
  }

  "a P1 to C1!  relation " should "succeed when trying to delete the parent if there is no child" in {
    schemaP1optToC1req.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server.query(
        """mutation {
        |  createParent(data: {
        |    p: "p1"
        |
        |  }){
        |    p
        |  }
        |}""".stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |  p
         |  }
         |}
      """.stripMargin,
        project
      )
      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }
  }

  "a PM to C1 " should "succeed in deleting the parent" in {
    schemaPMToC1opt.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server
        .query(
          """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childrenOpt: {
          |      create: [{c: "c1"}, {c: "c2"}]
          |    }
          |  }){
          |    childrenOpt{
          |       c
          |    }
          |  }
          |}""".stripMargin,
          project
        )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(2) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: { p: "p1"}
         |  ){
         |    p
         |  }
         |}
      """.stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(2)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }
  }

  "a PM to C1 " should "succeed in deleting the parent if there is no child" in {
    schemaPMToC1opt.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server
        .query(
          """mutation {
          |  createParent(data: {
          |    p: "p1"
          |  }){
          |    p
          |  }
          |}""".stripMargin,
          project
        )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: { p: "p1"}
         |  ){
         |    p
         |  }
         |}
      """.stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }
  }

  "a P1! to CM  relation" should "should succeed in deleting the parent " in {
    schemaP1reqToCM.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server.query(
        """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childReq: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childReq{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |    p
         |  }
         |}
      """.stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }
  }

  "a P1 to CM  relation " should " should succeed in deleting the parent" in {
    schemaP1optToCM.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server.query(
        """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |    p
         |  }
         |}
      """.stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(1)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }
  }

  "a P1 to CM  relation " should " should succeed in deleting the parent if there is no child" in {
    schemaP1optToCM.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server.query(
        """mutation {
        |  createParent(data: {
        |    p: "p1"
        |
        |  }){
        |    p
        |  }
        |}""".stripMargin,
        project
      )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |    p
         |  }
         |}
      """.stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }
  }

  "a PM to CM  relation" should "succeed in deleting the parent" in {
    schemaPMToCM.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server.query(
        """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      create: [{c: "c1"},{c: "c2"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(2) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |    p
         |  }
         |}
      """.stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(2)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }
  }

  "a PM to CM  relation" should "succeed in deleting the parent if there is no child" in {
    schemaPMToCM.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      server.query(
        """mutation {
        |  createParent(data: {
        |    p: "p1"
        |
        |  }){
        |    p
        |  }
        |}""".stripMargin,
        project
      )

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: {p: "p1"}
         |  ){
         |    p
         |  }
         |}
      """.stripMargin,
        project
      )

      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(0)
      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(0) }
    }

  }

  "a PM to CM  relation" should "delete the parent from other relations as well" in {
    val testDataModels = {
      val dm1 = """type Parent {
                  | id: ID! @id
                  | p: String! @unique
                  | childrenOpt: [Child] @relation(link: INLINE)
                  | stepChildOpt: StepChild @relation(link: INLINE)
                  |}
                  |
                  |type Child {
                  | id: ID! @id
                  | c: String! @unique
                  | parentsOpt: [Parent]
                  |}
                  |
                  |type StepChild {
                  | id: ID! @id
                  | s: String! @unique
                  | parentOpt: Parent
                  |}
                """.stripMargin

      val dm2 = """type Parent {
                  | id: ID! @id
                  | p: String! @unique
                  | childrenOpt: [Child]
                  | stepChildOpt: StepChild
                  |}
                  |
                  |type Child {
                  | id: ID! @id
                  | c: String! @unique
                  | parentsOpt: [Parent] @relation(link: INLINE)
                  |}
                  |
                  |type StepChild {
                  | id: ID! @id
                  | s: String! @unique
                  | parentOpt: Parent @relation(link: INLINE)
                  |}
                """.stripMargin

      val dm3 = """type Parent {
                  | id: ID! @id
                  | p: String! @unique
                  | childrenOpt: [Child]
                  | stepChildOpt: StepChild
                  |}
                  |
                  |type Child {
                  | id: ID! @id
                  | c: String! @unique
                  | parentsOpt: [Parent]
                  |}
                  |
                  |type StepChild {
                  | id: ID! @id
                  | s: String! @unique
                  | parentOpt: Parent @relation(link: INLINE)
                  |}
                """.stripMargin

      val dm4 = """type Parent {
                  | id: ID! @id
                  | p: String! @unique
                  | childrenOpt: [Child]
                  | stepChildOpt: StepChild @relation(link: INLINE)
                  |}
                  |
                  |type Child {
                  | id: ID! @id
                  | c: String! @unique
                  | parentsOpt: [Parent]
                  |}
                  |
                  |type StepChild {
                  | id: ID! @id
                  | s: String! @unique
                  | parentOpt: Parent
                  |}
                """.stripMargin

      TestDataModels(mongo = Vector(dm1, dm2), sql = Vector(dm3, dm4))
    }
    testDataModels.testV11 { project =>
      server.query(
        """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      create: [{c: "c1"},{c: "c2"}]
        |    }
        |    stepChildOpt: {
        |      create: {s: "s1"}
        |    }
        |  }){
        |    p
        |  }
        |}""".stripMargin,
        project
      )

      ifConnectorIsActive {
        dataResolver(project).countByTable("_ParentToStepChild").await should be(1)
        dataResolver(project).countByTable("_ChildToParent").await should be(2)
      }

      server.query(
        s"""
         |mutation {
         |  deleteParent(
         |  where: { p: "p1"}
         | ){
         |  p
         |  }
         |}
      """.stripMargin,
        project
      )

      ifConnectorIsActive {
        dataResolver(project).countByTable("_ChildToParent").await should be(0)
        dataResolver(project).countByTable("_ParentToStepChild").await should be(0)
      }
      dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(0)
      dataResolver(project).countByTable(project.schema.getModelByName_!("Child").dbName).await should be(2)
      dataResolver(project).countByTable(project.schema.getModelByName_!("StepChild").dbName).await should be(1)
    }
  }
}
