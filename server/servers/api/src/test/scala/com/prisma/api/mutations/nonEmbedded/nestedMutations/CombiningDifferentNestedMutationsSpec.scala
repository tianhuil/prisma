package com.prisma.api.mutations.nonEmbedded.nestedMutations

import com.prisma.api.ApiSpecBase
import com.prisma.shared.models.ConnectorCapability
import com.prisma.shared.models.ConnectorCapability.JoinRelationLinksCapability
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class CombiningDifferentNestedMutationsSpec extends FlatSpec with Matchers with ApiSpecBase with SchemaBaseV11 {
  override def runOnlyForCapabilities: Set[ConnectorCapability] = Set(JoinRelationLinksCapability)
  //hardcoded execution order
  //  nestedCreates
  //  nestedUpdates
  //  nestedUpserts
  //  nestedDeletes
  //  nestedConnects
  //  nestedSets
  //  nestedDisconnects
  //  nestedUpdateManys
  //  nestedDeleteManys
  // this could be extended to more combinations and to different schemata
  // the error behavior would be interesting to test, which error is returned, does rollback work

  "A create followed by an update" should "work" in {
    schemaPMToCM.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      val res = server.query(
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

      res.toString should be("""{"data":{"createParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

      val res2 = server.query(
        """mutation {
        |  updateParent(
        |  where:{p: "p1"}
        |  data: {
        |    childrenOpt: {
        |    create: [{c: "c3"},{c: "c4"}],
        |    update: [{where: {c: "c3"} data: {c: "cUpdated"}}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      res2.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"},{"c":"cUpdated"},{"c":"c4"}]}}}""")

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(4) }

      server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"}]},{"c":"c2","parentsOpt":[{"p":"p1"}]},{"c":"cUpdated","parentsOpt":[{"p":"p1"}]},{"c":"c4","parentsOpt":[{"p":"p1"}]}]}}""")

    }
  }

  "A create followed by a delete" should "work" in {
    schemaPMToCM.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      val res = server.query(
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

      res.toString should be("""{"data":{"createParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

      val res2 = server.query(
        """mutation {
        |  updateParent(
        |  where:{p: "p1"}
        |  data: {
        |    childrenOpt: {
        |    create: [{c: "c3"},{c: "c4"}],
        |    delete: [{c: "c3"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      res2.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"},{"c":"c4"}]}}}""")

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(3) }

      server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"}]},{"c":"c2","parentsOpt":[{"p":"p1"}]},{"c":"c4","parentsOpt":[{"p":"p1"}]}]}}""")

    }
  }

  "A create followed by a set" should "work" in {
    schemaPMToCM.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      val res = server.query(
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

      res.toString should be("""{"data":{"createParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

      val res2 = server.query(
        """mutation {
        |  updateParent(
        |  where:{p: "p1"}
        |  data: {
        |    childrenOpt: {
        |    create: [{c: "c3"},{c: "c4"}],
        |    set: [{c: "c3"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      res2.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c3"}]}}}""")

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(1) }

      server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"children":[{"c":"c1","parentsOpt":[]},{"c":"c2","parentsOpt":[]},{"c":"c3","parentsOpt":[{"p":"p1"}]},{"c":"c4","parentsOpt":[]}]}}""")

    }
  }

  "A create followed by an upsert" should "work" in {
    schemaPMToCM.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      val res = server.query(
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

      res.toString should be("""{"data":{"createParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

      val res2 = server.query(
        """mutation {
        |  updateParent(
        |  where:{p: "p1"}
        |  data: {
        |    childrenOpt: {
        |    create: [{c: "c3"},{c: "c4"}],
        |    upsert: [{where: {c: "c3"}
        |              create: {c: "should not matter"}
        |              update: {c: "cUpdated"}},
        |              {where: {c: "c5"}
        |              create: {c: "cNew"}
        |              update: {c: "should not matter"}}
        |              ]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      res2.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"},{"c":"cUpdated"},{"c":"c4"},{"c":"cNew"}]}}}""")

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(5) }

      server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"}]},{"c":"c2","parentsOpt":[{"p":"p1"}]},{"c":"cUpdated","parentsOpt":[{"p":"p1"}]},{"c":"c4","parentsOpt":[{"p":"p1"}]},{"c":"cNew","parentsOpt":[{"p":"p1"}]}]}}""")
    }
  }

  "A create followed by a disconnect" should "work" in {
    schemaPMToCM.test { dataModel =>
      val project = SchemaDsl.fromStringV11() { dataModel }
      database.setup(project)

      val res = server.query(
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

      res.toString should be("""{"data":{"createParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

      val res2 = server.query(
        """mutation {
        |  updateParent(
        |  where:{p: "p1"}
        |  data: {
        |    childrenOpt: {
        |    create: [{c: "c3"},{c: "c4"}],
        |    disconnect: [{c: "c3"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
        project
      )

      res2.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"},{"c":"c4"}]}}}""")

      ifConnectorIsActive { dataResolver(project).countByTable("_ChildToParent").await should be(3) }

      server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"}]},{"c":"c2","parentsOpt":[{"p":"p1"}]},{"c":"c3","parentsOpt":[]},{"c":"c4","parentsOpt":[{"p":"p1"}]}]}}""")

    }
  }

}
