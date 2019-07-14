package com.prisma.api.mutations.embedded.nestedMutations.nonEmbeddedToEmbedded

import com.prisma.api.{ApiSpecBase, TestDataModels}
import com.prisma.api.mutations.nonEmbedded.nestedMutations.SchemaBaseV11
import com.prisma.shared.models.ConnectorCapability.EmbeddedTypesCapability
import com.prisma.shared.schema_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class EmbeddedNestedDeleteMutationInsideUpdateSpec extends FlatSpec with Matchers with ApiSpecBase with SchemaBaseV11 {

  override def runOnlyForCapabilities = Set(EmbeddedTypesCapability)
  //Fixme
  //verify results using normal queries
  //test nestedDeleteMany (whereFilter instead of where) -> for no hit/ partial hit / full hit

  "a P1! relation " should "error due to the operation not being in the schema anymore" in {
    val project = SchemaDsl.fromStringV11() { embeddedP1req }

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
          |       c
          |    }
          |  }
          |}""",
        project
      )

    val parentId = res.pathAsString("data.createParent.id")

    server.queryThatMustFail(
      s"""
         |mutation {
         |  updateParent(
         |  where: {id: "$parentId"}
         |  data:{
         |    p: "p2"
         |    childReq: {delete: true}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 0,
      errorContains = "Argument 'data' expected type 'ParentUpdateInput!'"
    )

  }

  "a P1 relation " should "work through a nested mutation by id" in {
    val project = SchemaDsl.fromStringV11() { embeddedP1opt }

    database.setup(project)

    val existingDataRes = server
      .query(
        """mutation {
          |  createParent(data: {
          |    p: "existingParent"
          |    childOpt: {
          |      create: {c: "existingChild"}
          |    }
          |  }){
          |    id
          |    childOpt{
          |       c
          |    }
          |  }
          |}""",
        project
      )

    val existingParentId = existingDataRes.pathAsString("data.createParent.id")

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
          |       c
          |    }
          |  }
          |}""",
        project
      )

    val parentId = res.pathAsString("data.createParent.id")

    val res2 = server.query(
      s"""
         |mutation {
         |  updateParent(
         |  where:{id: "$parentId"}
         |  data:{
         |    p: "p2"
         |    childOpt: {delete: true}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """,
      project
    )

    res2.toString should be("""{"data":{"updateParent":{"childOpt":null}}}""")

    // Verify existing data

    server
      .query(
        s"""
         |{
         |  parent(where:{id: "$existingParentId"}){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """,
        project
      )
      .toString should be(s"""{"data":{"parent":{"childOpt":{"c":"existingChild"}}}}""")
  }

  "a P1 relation" should "error if there is no child connected" in {
    val project = SchemaDsl.fromStringV11() { embeddedP1opt }

    database.setup(project)

    val parent1Id = server
      .query(
        """mutation {
          |  createParent(data: {p: "p1"})
          |  {
          |    id
          |  }
          |}""",
        project
      )
      .pathAsString("data.createParent.id")

    val res = server.queryThatMustFail(
      s"""
         |mutation {
         |  updateParent(
         |  where:{id: "$parent1Id"}
         |  data:{
         |    p: "p2"
         |    childOpt: {delete: true}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 3041
    )

    res.toString should include(
      s"""The relation ChildToParent has no node for the model Parent with the value '$parent1Id' for the field 'id' connected to a node for the model Child on your mutation path.""")

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
  }

  "a PM relation " should "work" in {
    val project = SchemaDsl.fromStringV11() { embeddedPM }

    database.setup(project)

    val res1 = server.query(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      create: [{c: "c1"},{c: "c2"}]
        |    }
        |  }){
        |    childrenOpt{
        |       id
        |       c
        |    }
        |  }
        |}""",
      project
    )

    val array = res1.pathAsJsArray("data.createParent.childrenOpt")
    array.value should have(size(2))
    val idOfC1 = array.value.head.pathAsString("id")

    val res2 = server.query(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p1"}
         |    data:{
         |    childrenOpt: {delete: {id: "$idOfC1"}}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """,
      project
    )

    res2.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c2"}]}}}""")

    dataResolver(project).countByTable(project.schema.getModelByName_!("Parent").dbName).await should be(1)
  }

  "A PM relation" should "be deletable by any unique argument through a nested mutation" in {
    val project = SchemaDsl.fromStringV11() {
      """
        |type Todo{
        | id: ID! @id
        | comments: [Comment]
        |}
        |
        |type Comment @embedded{
        | id: ID! @id
        | text: String
        | alias: String!
        |}
      """
    }

    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createTodo(
        |    data: {
        |      comments: {
        |        create: [{text: "comment1", alias: "alias1"}, {text: "comment2", alias: "alias2"}]
        |      }
        |    }
        |  ){
        |    id
        |    comments { id }
        |  }
        |}""",
      project
    )
    val todoId     = createResult.pathAsString("data.createTodo.id")
    val commentId  = createResult.pathAsString("data.createTodo.comments.[0].id")
    val commentId2 = createResult.pathAsString("data.createTodo.comments.[1].id")

    val result = server.query(
      s"""mutation {
         |  updateTodo(
         |    where: {
         |      id: "$todoId"
         |    }
         |    data:{
         |      comments: {
         |        delete: [{id: "$commentId"}, {id: "$commentId2"}]
         |      }
         |    }
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.updateTodo.comments").toString, """[]""")
  }

  "A P1 relation where both exist and are connected" should "be deletable through a nested mutation" in {
    val project = SchemaDsl.fromStringV11() {
      """
        |type Note {
        | id: ID! @id
        | text: String
        | todo: Todo
        |}
        |
        |type Todo @embedded{
        | title: String
        |}
      """
    }

    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createNote(
        |    data: {
        |      todo: {
        |        create: { title: "the title" }
        |      }
        |    }
        |  ){
        |    id
        |    todo { title }
        |  }
        |}""",
      project
    )
    val noteId = createResult.pathAsString("data.createNote.id")

    val result = server.query(
      s"""
         |mutation {
         |  updateNote(
         |    where: {
         |      id: "$noteId"
         |    }
         |    data: {
         |      todo: {
         |        delete: true
         |      }
         |    }
         |  ){
         |    todo {
         |      title
         |    }
         |  }
         |}
      """,
      project
    )
    mustBeEqual(result.pathAsJsValue("data.updateNote").toString, """{"todo":null}""")
  }

  "A P1 relation  where both sides exist and are connected" should "be deletable through a nested mutation" in {
    val project = SchemaDsl.fromStringV11() {
      """
        |type Note {
        | id: ID! @id
        | text: String! @unique
        | todo: Todo
        |}
        |
        |type Todo @embedded{
        | title: String!
        |}
        |"""
    }
    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createNote(
        |    data: {
        |      text: "FirstUnique"
        |      todo: {
        |        create: { title: "the title" }
        |      }
        |    }
        |  ){
        |    text
        |  }
        |}""",
      project
    )

    val result = server.query(
      s"""
         |mutation {
         |  updateNote(
         |    where: {
         |      text: "FirstUnique"
         |    }
         |    data: {
         |      todo: {
         |        delete: true
         |      }
         |    }
         |  ){
         |    todo {
         |      title
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.updateNote").toString, """{"todo":null}""")

    val query = server.query("""{ notes { text }}""", project)
    mustBeEqual(query.toString, """{"data":{"notes":[{"text":"FirstUnique"}]}}""")
  }

  "A P1 relation" should "not do a nested delete if the nested node does not exist" in {
    val project = SchemaDsl.fromStringV11() {
      """
        |type Note {
        | id: ID! @id
        | text: String! @unique
        | todo: Todo
        |}
        |
        |type Todo @embedded{
        | title: String!
        |}"""
    }
    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createNote(
        |    data: {
        |      text: "Note"
        |    }
        |  ){
        |    id
        |    todo { title }
        |  }
        |}""",
      project
    )
    val noteId = createResult.pathAsString("data.createNote.id")

    val result = server.queryThatMustFail(
      s"""
         |mutation {
         |  updateNote(
         |    where: {id: "$noteId"}
         |    data: {
         |      todo: {
         |        delete: true
         |      }
         |    }
         |  ){
         |    todo {
         |      title
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 3041,
      errorContains =
        s"The relation NoteToTodo has no node for the model Note with the value '$noteId' for the field 'id' connected to a node for the model Todo on your mutation path."
    )

    val query2 = server.query("""{ notes { text }}""", project)
    mustBeEqual(query2.toString, """{"data":{"notes":[{"text":"Note"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only node edges on the path" in {
    val project = SchemaDsl.fromStringV11() { """type Top {
                                             |  id: ID! @id
                                             |  nameTop: String! @unique
                                             |  middles: [Middle]
                                             |}
                                             |
                                             |type Middle @embedded{
                                             |  id: ID! @id
                                             |  nameMiddle: String!
                                             |  bottoms: [Bottom]
                                             |}
                                             |
                                             |type Bottom @embedded{
                                             |  id: ID! @id
                                             |  nameBottom: String!
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottoms: {
        |            create: [{ nameBottom: "the bottom"}, { nameBottom: "the second bottom"}]
        |          }
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottoms: {
        |            create: [{nameBottom: "the third bottom"},{nameBottom: "the fourth bottom"}]
        |          }
        |        }
        |     ]
        |    }
        |  }) { 
        |     id
        |     middles {
        |       id
        |       bottoms {
        |         id
        |       }
        |    }
        |  }
        |}
      """

    val setupResult = server.query(createMutation, project)
    val middleId    = setupResult.pathAsString("data.createTop.middles.[0].id")
    val bottomId    = setupResult.pathAsString("data.createTop.middles.[0].bottoms.[0].id")

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: { id: "$middleId" },
         |              data:{  nameMiddle: "updated middle"
         |                      bottoms: {delete: [{id: "$bottomId"}]
         |              }
         |       }}]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles {
         |      nameMiddle
         |      bottoms {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottoms":[{"nameBottom":"the second bottom"}]},{"nameMiddle":"the second middle","bottoms":[{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}]}}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only node edges on the path and there are no backrelations" in {
    val project = SchemaDsl.fromStringV11() { """type Top {
                                             |  id: ID! @id
                                             |  nameTop: String! @unique
                                             |  middles: [Middle]
                                             |}
                                             |
                                             |type Middle @embedded{
                                             |  id: ID! @id
                                             |  nameMiddle: String!
                                             |  bottoms: [Bottom]
                                             |}
                                             |
                                             |type Bottom @embedded{
                                             |  id: ID! @id
                                             |  nameBottom: String!
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottoms: {
        |            create: [{ nameBottom: "the bottom"}, { nameBottom: "the second bottom"}]
        |          }
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottoms: {
        |            create: [{nameBottom: "the third bottom"},{nameBottom: "the fourth bottom"}]
        |          }
        |        }
        |     ]
        |    }
        |  }) {
        |     id
        |     middles {
        |       id
        |       bottoms {
        |         id
        |       }
        |     }
        |  }
        |}
      """

    val setupResult = server.query(createMutation, project)
    val middleId    = setupResult.pathAsString("data.createTop.middles.[0].id")
    val bottomId    = setupResult.pathAsString("data.createTop.middles.[0].bottoms.[0].id")

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: { id: "$middleId"},
         |              data:{  nameMiddle: "updated middle"
         |                      bottoms: {delete: [{ id: "$bottomId" }]
         |              }
         |       }}]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles {
         |      nameMiddle
         |      bottoms {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottoms":[{"nameBottom":"the second bottom"}]},{"nameMiddle":"the second middle","bottoms":[{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}]}}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are model and node edges on the path " in {
    val project = SchemaDsl.fromStringV11() { """type Top {
                                             |  id: ID! @id
                                             |  nameTop: String! @unique
                                             |  middles: [Middle]
                                             |}
                                             |
                                             |type Middle @embedded{
                                             |  id: ID! @id
                                             |  nameMiddle: String!
                                             |  bottom: Bottom
                                             |}
                                             |
                                             |type Bottom @embedded{
                                             |  id: ID! @id
                                             |  nameBottom: String!
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {create: { nameBottom: "the bottom"}}
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottom: {create: { nameBottom: "the second bottom"}}
        |        }
        |     ]
        |    }
        |  }) {
        |     id
        |     middles {
        |       id
        |     }
        |  }
        |}
      """

    val setupResult = server.query(createMutation, project)
    val middleId    = setupResult.pathAsString("data.createTop.middles.[0].id")

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: { id: "$middleId" },
         |              data:{  nameMiddle: "updated middle"
         |                      bottom: { delete: true }
         |              }
         |              }]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottom":null},{"nameMiddle":"the second middle","bottom":{"nameBottom":"the second bottom"}}]}}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are model and node edges on the path  and back relations are missing and node edges follow model edges" in {
    val project = SchemaDsl.fromStringV11() { """type Top {
                                             |  id: ID! @id
                                             |  nameTop: String! @unique
                                             |  middle: Middle
                                             |}
                                             |
                                             |type Middle @embedded{
                                             |  id: ID! @id
                                             |  nameMiddle: String!
                                             |  bottom: Bottom
                                             |}
                                             |
                                             |type Bottom @embedded{
                                             |  id: ID! @id
                                             |  nameBottom: String!
                                             |  below: [Below]
                                             |}
                                             |
                                             |type Below @embedded{
                                             |  id: ID! @id
                                             |  nameBelow: String!
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation a {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: { nameBottom: "the bottom"
        |            below: {
        |            create: [{ nameBelow: "below"}, { nameBelow: "second below"}]}
        |        }}}
        |        }
        |  }) {
        |   id
        |   middle {
        |     bottom {
        |       below {
        |         id
        |       }
        |     }
        |   }
        | }
        |}
      """

    val setupResult = server.query(createMutation, project)
    val belowId     = setupResult.pathAsString("data.createTop.middle.bottom.below.[0].id")

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |               nameMiddle: "updated middle"
         |               bottom: {
         |                update: {
         |                  nameBottom: "updated bottom"
         |                  below: { delete: { id: "$belowId"}
         |
         |          }
         |                }
         |          }
         |       }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |        below{
         |           nameBelow
         |        }
         |
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":{"nameBottom":"updated bottom","below":[{"nameBelow":"second below"}]}}}}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only model edges on the path" in {
    val project = SchemaDsl.fromStringV11() { """type Top {
                                             |  id: ID! @id
                                             |  nameTop: String! @unique
                                             |  middle: Middle
                                             |}
                                             |
                                             |type Middle @embedded{
                                             |  nameMiddle: String!
                                             |  bottom: Bottom
                                             |}
                                             |
                                             |type Bottom @embedded{
                                             |  nameBottom: String!
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: {
        |              nameBottom: "the bottom"
        |            }
        |          }
        |        }
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""
         |mutation  {
         |  updateTop(
         |    where: {
         |      nameTop: "the top"
         |    }
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |              nameMiddle: "updated middle"
         |              bottom: {delete: true}
         |      }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be("""{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":null}}}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only model edges on the path and there are no backrelations" in {
    val project = SchemaDsl.fromStringV11() { """type Top {
                                             |  id: ID! @id
                                             |  nameTop: String! @unique
                                             |  middle: Middle
                                             |}
                                             |
                                             |type Middle @embedded{
                                             |  nameMiddle: String!
                                             |  bottom: Bottom
                                             |}
                                             |
                                             |type Bottom @embedded{
                                             |  nameBottom: String!
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: {
        |              nameBottom: "the bottom"
        |            }
        |          }
        |        }
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""
         |mutation  {
         |  updateTop(
         |    where: {
         |      nameTop: "the top"
         |    }
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |              nameMiddle: "updated middle"
         |              bottom: {delete: true}
         |      }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be("""{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":null}}}}""")
  }

  //Fixme Think about Self Relations and embedded types
  // would need to be nested within a normal type
  "Nested delete on self relations" should "only delete the specified nodes" in {
    val project = SchemaDsl.fromStringV11() { """type User {
                                             |  id: ID! @id
                                             |  name: String! @unique
                                             |  follower: [User] @relation(name: "UserFollow" link: INLINE)
                                             |  following: [User] @relation(name: "UserFollow")
                                             |}""" }
    database.setup(project)

    server.query("""mutation  {createUser(data: {name: "X"}) {id}}""", project)
    server.query("""mutation  {createUser(data: {name: "Y"}) {id}}""", project)
    server.query("""mutation  {createUser(data: {name: "Z"}) {id}}""", project)

    val updateMutation =
      s""" mutation {
         |  updateUser(data:{
         |    following: {
         |      connect: [{ name: "Y" }, { name: "Z"}]
         |    }
         |  },where:{
         |    name:"X"
         |  }) {
         |    name
         |    following{
         |      name
         |    }
         |    follower{
         |      name
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be("""{"data":{"updateUser":{"name":"X","following":[{"name":"Y"},{"name":"Z"}],"follower":[]}}}""")

    val check = server.query("""query{users{name, following{name}}}""", project)

    check.toString should be(
      """{"data":{"users":[{"name":"X","following":[{"name":"Y"},{"name":"Z"}]},{"name":"Y","following":[]},{"name":"Z","following":[]}]}}""")

    val deleteMutation =
      s""" mutation {
         |  updateUser(data:{
         |    follower: {
         |      delete: [{ name: "X" }]
         |    }
         |  },where:{
         |    name:"Y"
         |  }) {
         |    name
         |    following{
         |      name
         |    }
         |  }
         |}
      """

    val result2 = server.query(deleteMutation, project)

    result2.toString should be("""{"data":{"updateUser":{"name":"Y","following":[]}}}""")

    val result3 = server.query("""query{users{name, following{name}}}""", project)

    result3.toString should be("""{"data":{"users":[{"name":"Y","following":[]},{"name":"Z","following":[]}]}}""")
  }

  "Deleting toOne relations" should "work" in {

    val project = SchemaDsl.fromStringV11() {
      """type Top {
        |   id: ID! @id
        |   unique: Int! @unique
        |   name: String!
        |   middle: Middle
        |}
        |
        |type Middle @embedded{
        |   int: Int!
        |   name: String!
        |}"""
    }

    database.setup(project)

    val res = server.query(
      s"""mutation {
         |   createTop(data: {
         |   unique: 1,
         |   name: "Top",
         |   middle: {create:{
         |      int: 11,
         |      name: "Middle"
         |   }
         |   }
         |}){
         |  unique,
         |  middle{
         |    int
         |  }
         |}}""".stripMargin,
      project
    )

    res.toString should be("""{"data":{"createTop":{"unique":1,"middle":{"int":11}}}}""")

    val res2 = server.query(
      s"""mutation {
         |   updateTop(
         |   where:{unique: 1}
         |   data: {
         |      name: "Top2",
         |      middle: {delete: true}
         |}){
         |  unique,
         |  middle{
         |    int
         |  }
         |}}""".stripMargin,
      project
    )

    res2.toString should be("""{"data":{"updateTop":{"unique":1,"middle":null}}}""")
  }

  "To many and toOne mixed relations deleting over two levels" should "work" in {

    val project = SchemaDsl.fromStringV11() {
      """type Top {
        |   id: ID! @id
        |   unique: Int! @unique
        |   name: String!
        |   middle: Middle
        |}
        |
        |type Middle @embedded {
        |   id: ID! @id
        |   unique: Int!
        |   name: String!
        |   bottom: [Bottom]
        |}
        |
        |type Bottom @embedded{
        |   id: ID! @id
        |   unique: Int!
        |   name: String!
        |}"""
    }

    database.setup(project)

    val res = server.query(
      s"""mutation {
         |   createTop(data: {
         |   unique: 1,
         |   name: "Top",
         |   middle: {create:{
         |      unique: 11,
         |      name: "Middle"
         |      bottom: {create:[
         |        {
         |          unique: 111,
         |          name: "Bottom"
         |        },
         |        {
         |          unique: 112,
         |          name: "Bottom"
         |        }
         |      ]
         |      }}
         |    }
         |}){
         |  unique,
         |  middle{
         |    unique,
         |    bottom{
         |      id
         |      unique
         |    }
         |  }
         |}}""".stripMargin,
      project
    )
    val bottomId = res.pathAsString("data.createTop.middle.bottom.[0].id")

    val res2 = server.query(
      s"""mutation {
         |   updateTop(
         |   where:{unique: 1}
         |   data: {
         |      name: "Top2",
         |      middle: {
         |        update: {
         |          bottom: {
         |            delete: { id:"$bottomId" }
         |          }
         |        }
         |     }
         |}){
         |  unique,
         |  middle{
         |    unique
         |    bottom{
         |      unique
         |    }
         |  }
         |}}""".stripMargin,
      project
    )

    res2.toString should be("""{"data":{"updateTop":{"unique":1,"middle":{"unique":11,"bottom":[{"unique":112}]}}}}""")
  }

  "To many and toOne mixedrelations deleting over two levels" should "error correctly" in {

    val project = SchemaDsl.fromStringV11() {
      """type Top {
        |   id: ID! @id
        |   unique: Int! @unique
        |   name: String!
        |   middle: Middle
        |}
        |
        |type Middle @embedded {
        |   id: ID! @id
        |   unique: Int!
        |   name: String!
        |   bottom: [Bottom]
        |}
        |
        |type Bottom @embedded{
        |   id: ID! @id
        |   unique: Int!
        |   name: String!
        |}"""
    }

    database.setup(project)

    val res = server.query(
      s"""mutation {
         |   createTop(data: {
         |   unique: 1,
         |   name: "Top",
         |   middle: {create:{
         |      unique: 11,
         |      name: "Middle"
         |      bottom: {create:[
         |        {
         |          unique: 111,
         |          name: "Bottom"
         |        },
         |        {
         |          unique: 112,
         |          name: "Bottom"
         |        }
         |      ]
         |      }}
         |    }
         |}){
         |  unique,
         |  middle{
         |    unique,
         |    bottom{
         |      unique
         |    }
         |  }
         |}}""".stripMargin,
      project
    )

    res.toString should be("""{"data":{"createTop":{"unique":1,"middle":{"unique":11,"bottom":[{"unique":111},{"unique":112}]}}}}""")

    server.queryThatMustFail(
      s"""mutation {
         |   updateTop(
         |   where:{unique: 1}
         |   data: {
         |      name: "Top2",
         |      middle: {
         |        update: {
         |           bottom: { delete: { id:"non-existent" } }
         |        }
         |     }
         |}){
         |  unique,
         |  middle{
         |    unique
         |    bottom{
         |      unique
         |    }
         |  }
         |}}""".stripMargin,
      project,
      errorCode = 3041,
      errorContains =
        """The relation BottomToMiddle has no node for the model Middle connected to a Node for the model Bottom with the value 'non-existent' for the field 'id'"""
    )
  }

  "To many relations deleting over two levels" should "work" in {

    val project = SchemaDsl.fromStringV11() {
      """type Top {
        |   id: ID! @id
        |   unique: Int! @unique
        |   name: String!
        |   middle: [Middle]
        |}
        |
        |type Middle @embedded {
        |   id: ID! @id
        |   unique: Int!
        |   name: String!
        |   bottom: [Bottom]
        |}
        |
        |type Bottom @embedded {
        |   id: ID! @id
        |   unique: Int!
        |   name: String!
        |}"""
    }

    database.setup(project)

    val res = server.query(
      s"""mutation {
         |   createTop(data: {
         |   unique: 1,
         |   name: "Top",
         |   middle: {create:[{
         |      unique: 11,
         |      name: "Middle"
         |      bottom: {create:{
         |          unique: 111,
         |          name: "Bottom"
         |      }}},
         |      {
         |      unique: 12,
         |      name: "Middle2"
         |      bottom: {create:{
         |          unique: 112,
         |          name: "Bottom2"
         |      }}
         |    }]
         |   }
         |}){
         |  unique,
         |  middle{
         |    id
         |    unique
         |    bottom{
         |      id
         |      unique
         |    }
         |  }
         |}}""".stripMargin,
      project
    )

    val middleId = res.pathAsString("data.createTop.middle.[0].id")
    val bottomId = res.pathAsString("data.createTop.middle.[0].bottom.[0].id")

    val res2 = server.query(
      s"""mutation {
         |   updateTop(
         |   where:{unique: 1}
         |   data: {
         |      name: "Top2",
         |      middle: {
         |        update:{
         |          where:{ id:"$middleId" }
         |          data: {
         |            name: "MiddleNew"
         |            bottom: {
         |              delete:{ id: "$bottomId" }
         |            }
         |          }
         |        }
         |     }
         |}){
         |  unique,
         |  middle{
         |    unique,
         |    name,
         |    bottom{
         |      unique,
         |    }
         |  }
         |}}""".stripMargin,
      project
    )

    res2.toString should be(
      """{"data":{"updateTop":{"unique":1,"middle":[{"unique":11,"name":"MiddleNew","bottom":[]},{"unique":12,"name":"Middle2","bottom":[{"unique":112}]}]}}}""")
  }

}
