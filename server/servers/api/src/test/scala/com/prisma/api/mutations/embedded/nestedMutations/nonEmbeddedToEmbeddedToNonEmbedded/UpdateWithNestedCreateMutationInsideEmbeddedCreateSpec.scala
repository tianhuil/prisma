package com.prisma.api.mutations.embedded.nestedMutations.nonEmbeddedToEmbeddedToNonEmbedded

import com.prisma.api.ApiSpecBase
import com.prisma.api.mutations.nonEmbedded.nestedMutations.SchemaBaseV11
import com.prisma.shared.models.ConnectorCapability.EmbeddedTypesCapability
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class UpdateWithNestedCreateMutationInsideEmbeddedCreateSpec extends FlatSpec with Matchers with ApiSpecBase with SchemaBaseV11 {
  override def runOnlyForCapabilities = Set(EmbeddedTypesCapability)

  "a FriendReq relation" should "be possible" in {

    val project = SchemaDsl.fromStringV11() { embedddedToJoinFriendReq }

    database.setup(project)

    val create = server
      .query(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |  }){
          |    p
          |    children{
          |       c
          |       friendReq{
          |         f
          |       }
          |
          |    }
          |  }
          |}""",
        project
      )

    create.toString should be("""{"data":{"createParent":{"p":"p1","children":[]}}}""")

    val update = server
      .query(
        """mutation {
          |  updateParent(
          |  where:{p:"p1"}
          |  data: {
          |    children: {create:{
          |       c: "c1"
          |       friendReq:{create:{f: "f1"}}
          |    }}
          |  }){
          |    p
          |    children{
          |       c
          |       friendReq{
          |         f
          |       }
          |
          |    }
          |  }
          |}""",
        project
      )

    update.toString should be("""{"data":{"updateParent":{"p":"p1","children":[{"c":"c1","friendReq":{"f":"f1"}}]}}}""")
  }

  "a FriendOpt relation" should "be possible" in {

    val project = SchemaDsl.fromStringV11() { embedddedToJoinFriendOpt }

    database.setup(project)

    val create = server
      .query(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |  }){
          |    p
          |    children{
          |       c
          |       friendOpt{
          |         f
          |       }
          |
          |    }
          |  }
          |}""",
        project
      )

    create.toString should be("""{"data":{"createParent":{"p":"p1","children":[]}}}""")

    val update = server
      .query(
        """mutation {
          |  updateParent(
          |  where:{p:"p1"}
          |  data: {
          |    children: {create:{
          |       c: "c1"
          |       friendOpt:{create:{f: "f1"}}
          |    }}
          |  }){
          |    p
          |    children{
          |       c
          |       friendOpt{
          |         f
          |       }
          |
          |    }
          |  }
          |}""",
        project
      )

    update.toString should be("""{"data":{"updateParent":{"p":"p1","children":[{"c":"c1","friendOpt":{"f":"f1"}}]}}}""")
  }

  "a FriendsOpt relation" should "be possible" in {

    val project = SchemaDsl.fromStringV11() { embedddedToJoinFriendsOpt }

    database.setup(project)

    val create = server
      .query(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |  }){
          |    p
          |    children{
          |       id
          |       c
          |       friendsOpt{
          |         f
          |       }
          |
          |    }
          |  }
          |}""",
        project
      )

    val update = server
      .query(
        """mutation {
          |  updateParent(
          |  where:{p:"p1"}
          |  data: {
          |    children: {create:{
          |       c: "c1"
          |       friendsOpt:{create:[{f: "f1"}, {f: "f2"}]}
          |    }}
          |  }){
          |    p
          |    children{
          |       c
          |       friendsOpt{
          |         f
          |       }
          |
          |    }
          |  }
          |}""",
        project
      )

    update.toString should be("""{"data":{"updateParent":{"p":"p1","children":[{"c":"c1","friendsOpt":[{"f":"f1"},{"f":"f2"}]}]}}}""")
  }

}
