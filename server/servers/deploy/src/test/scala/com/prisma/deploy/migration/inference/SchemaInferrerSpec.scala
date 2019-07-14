package com.prisma.deploy.migration.inference

import com.prisma.deploy.migration.validation.DataModelValidatorImpl
import com.prisma.deploy.specutils.DeploySpecBase
import com.prisma.shared.models.ConnectorCapability.{EmbeddedTypesCapability, MigrationsCapability, RelationLinkListCapability, RelationLinkTableCapability}
import com.prisma.shared.models.Manifestations.{EmbeddedRelationLink, FieldManifestation, ModelManifestation, RelationTable}
import com.prisma.shared.models.{ConnectorCapabilities, RelationSide, Schema}
import com.prisma.shared.schema_dsl.{SchemaDsl, TestProject}
import org.scalatest.{Matchers, WordSpec}

class SchemaInferrerSpec extends WordSpec with Matchers with DeploySpecBase {

  val emptyProject = TestProject.emptyV11

  "if a given relation does not exist yet, the inferrer" should {
    "infer relations with the given name if a relation directive is provided on both sides" in {
      val types =
        """
          |type Todo {
          |  id: ID! @id
          |  comments: [Comment] @relation(name:"MyNameForTodoToComments")
          |}
          |
          |type Comment {
          |  id: ID! @id
          |  todo: Todo! @relation(name:"MyNameForTodoToComments")
          |}
        """.stripMargin.trim()
      val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities.empty)

      val relation = schema.getRelationByName_!("MyNameForTodoToComments")
      relation.modelAName should equal("Comment")
      relation.modelBName should equal("Todo")
    }

    "infer relations with the given name if a relation directive is provided on both sides and there are two relations between models" in {
      val types =
        """
          |type User {
          |  id: ID! @id
          |  calls: [Call] @relation(name: "CallRequester")
          |  calls_member: [Call] @relation(name: "CallMembers")
          |}
          |type Call {
          |  id: ID! @id
          |  created_by: User! @relation(name: "CallRequester")
          |  members: [User] @relation(name: "CallMembers")
          |}""".stripMargin.trim()
      val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(RelationLinkTableCapability))

      val relation = schema.getRelationByName_!("CallRequester")
      relation.modelAName should equal("Call")
      relation.modelBName should equal("User")

      val relation2 = schema.getRelationByName_!("CallMembers")
      relation2.modelAName should equal("Call")
      relation2.modelBName should equal("User")
    }

    "infer relations with an auto generated name if no relation directive is given" in {
      val types =
        """
          |type Todo {
          |  id: ID! @id
          |  comments: [Comment]
          |}
          |
          |type Comment {
          |  id: ID! @id
          |  todo: Todo!
          |}
        """.stripMargin.trim()
      val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities.empty)
      schema.relations.foreach(println(_))

      val relation = schema.getRelationByName_!("CommentToTodo")
      relation.modelAName should equal("Comment")
      relation.modelBName should equal("Todo")

      val field1 = schema.getModelByName_!("Todo").getRelationFieldByName_!("comments")
      field1.isList should be(true)
      field1.relation should be(relation)

      val field2 = schema.getModelByName_!("Comment").getRelationFieldByName_!("todo")
      field2.isList should be(false)
      field2.relation should be(relation)
    }

    "For mongoRelations the correct side should have the id inlined 1" in {
      val types =
        """
          |type Todo {
          |  id: ID! @id
          |  comments: [Comment] @relation(link: INLINE)
          |}
          |
          |type Comment {
          |  id: ID! @id
          |  todo: Todo!
          |}
        """.stripMargin.trim()

      val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(EmbeddedTypesCapability, RelationLinkListCapability))
      schema.relations.foreach(println(_))

      val relation = schema.getRelationByName_!("CommentToTodo")
      relation.modelAName should equal("Comment")
      relation.modelBName should equal("Todo")
      relation.manifestation should be(EmbeddedRelationLink("Todo", "comments"))

      val field1 = schema.getModelByName_!("Todo").getRelationFieldByName_!("comments")
      field1.isList should be(true)
      field1.relation should be(relation)

      val field2 = schema.getModelByName_!("Comment").getRelationFieldByName_!("todo")
      field2.isList should be(false)
      field2.relation should be(relation)

      field1.relationIsInlinedInParent should be(true)
    }

    "For mongoRelations the correct side should have the id inlined 2" in {
      val types =
        """
          |type Todo {
          |  id: ID! @id
          |  comments: [Comment]
          |}
          |
          |type Comment {
          |  id: ID! @id
          |  todo: Todo! @relation(link: INLINE)
          |}
        """.stripMargin.trim()

      val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(EmbeddedTypesCapability, RelationLinkListCapability))
      schema.relations.foreach(println(_))

      val relation = schema.getRelationByName_!("CommentToTodo")
      relation.modelAName should equal("Comment")
      relation.modelBName should equal("Todo")

      val field1 = schema.getModelByName_!("Todo").getRelationFieldByName_!("comments")
      field1.isList should be(true)
      field1.relation should be(relation)

      val field2 = schema.getModelByName_!("Comment").getRelationFieldByName_!("todo")
      field2.isList should be(false)
      field2.relation should be(relation)

      field2.relationIsInlinedInParent should be(true)
    }

  }

  "if a given relation does already exist, the inferer" should {
    val project = SchemaDsl.fromStringV11() {
      s"""
        |type Todo {
        |  id: ID! @id
        |  comments: [Comment] @relation(name: "CommentToTodo" $listInlineArgument)
        |}
        |
        |type Comment {
        |  id: ID! @id
        |  todo: Todo @relation(name: "CommentToTodo")
        |}
      """.stripMargin
    }

    "infer the existing relation and update it accordingly when the type names change" in {
      val types =
        """
          |type TodoNew {
          |  id: ID! @id
          |  comments: [CommentNew]
          |}
          |
          |type CommentNew {
          |  id: ID! @id
          |  todo: TodoNew!
          |}
        """.stripMargin

      val renames = SchemaMapping(
        models = Vector(
          Mapping(previous = "Todo", next = "TodoNew"),
          Mapping(previous = "Comment", next = "CommentNew")
        )
      )

      val newSchema = infer(project.schema, types, renames, capabilities = ConnectorCapabilities.empty)
      newSchema.relations.foreach(println(_))

      val relation = newSchema.getRelationByName_!("CommentNewToTodoNew")
      relation.modelAName should be("CommentNew")
      relation.modelBName should be("TodoNew")

      val field1 = newSchema.getModelByName_!("TodoNew").getRelationFieldByName_!("comments")
      field1.isList should be(true)
      field1.relation should be(relation)

      val field2 = newSchema.getModelByName_!("CommentNew").getRelationFieldByName_!("todo")
      field2.isList should be(false)
      field2.relation should be(relation)
    }

    "infer the existing relation although the type and field names changed" in {
      val types =
        """
          |type TodoNew {
          |  id: ID! @id
          |  commentsNew: [CommentNew]
          |}
          |
          |type CommentNew {
          |  id: ID! @id
          |  todoNew: TodoNew!
          |}
        """.stripMargin

      val renames = SchemaMapping(
        models = Vector(
          Mapping(previous = "Todo", next = "TodoNew"),
          Mapping(previous = "Comment", next = "CommentNew")
        ),
        fields = Vector(
          FieldMapping(previousModel = "Todo", previousField = "comments", nextModel = "TodoNew", nextField = "commentsNew"),
          FieldMapping(previousModel = "Comment", previousField = "todo", nextModel = "CommentNew", nextField = "todoNew")
        )
      )

      val newSchema = infer(project.schema, types, renames, capabilities = ConnectorCapabilities.empty)
      newSchema.relations.foreach(println(_))

      val relation = newSchema.getRelationByName_!("CommentNewToTodoNew")
      relation.modelAName should be("CommentNew")
      relation.modelBName should be("TodoNew")

      val field1 = newSchema.getModelByName_!("TodoNew").getRelationFieldByName_!("commentsNew")
      field1.isList should be(true)
      field1.relation should be(relation)

      val field2 = newSchema.getModelByName_!("CommentNew").getRelationFieldByName_!("todoNew")
      field2.isList should be(false)
      field2.relation should be(relation)
    }
  }

  "if a model already exists and it gets renamed, the inferrer" should {
    "infer the next model with the stable identifier of the existing model" in {
      val project = SchemaDsl.fromStringV11() {
        """
          |type Todo {
          |  id: ID! @id
          |  title: String
          |}
        """.stripMargin
      }
      val types =
        """
          |type TodoNew {
          |  id: ID! @id
          |  title: String
          |}
        """.stripMargin

      val renames = SchemaMapping(
        models = Vector(
          Mapping(previous = "Todo", next = "TodoNew")
        )
      )

      val newSchema = infer(project.schema, types, renames, capabilities = ConnectorCapabilities.empty)

      val previousModel = project.schema.getModelByName_!("Todo")
      val nextModel     = newSchema.getModelByName_!("TodoNew")

      previousModel.stableIdentifier should equal(nextModel.stableIdentifier)
    }
  }

  "For self-relations the inferer" should {
    "assign fieldA to the field with the lower lexicographic order" in {
      val types =
        """|type Technology {
           |  id: ID! @id
           |  name: String! @unique
           |  childTechnologies: [Technology] @relation(name: "ChildTechnologies")
           |  parentTechnologies: [Technology] @relation(name: "ChildTechnologies")
           |}""".stripMargin.trim()
      val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(RelationLinkTableCapability))

      schema.relations should have(size(1))
      val relation = schema.getRelationByName_!("ChildTechnologies")
      relation.modelAName should equal("Technology")
      relation.modelBName should equal("Technology")
      relation.modelAField.name should be("childTechnologies")
      relation.modelBField.name should be("parentTechnologies")

    }

    "keep assignments after renames" in {
      val capas = ConnectorCapabilities(RelationLinkTableCapability)
      val types =
        """|type Technology {
           |  id: ID! @id
           |  name: String! @unique
           |  childTechnologies: [Technology] @relation(name: "ChildTechnologies")
           |  parentTechnologies: [Technology] @relation(name: "ChildTechnologies")
           |}""".stripMargin.trim()
      val schema = infer(emptyProject.schema, types, capabilities = capas)

      schema.relations should have(size(1))
      val relation = schema.getRelationByName_!("ChildTechnologies")
      relation.modelAName should equal("Technology")
      relation.modelBName should equal("Technology")
      relation.modelAField.name should be("childTechnologies")
      relation.modelBField.name should be("parentTechnologies")

      val newTypes =
        """|type NewTechnology {
           |  id: ID! @id
           |  name: String! @unique
           |  xTechnologies: [NewTechnology] @relation(name: "ChildTechnologies")
           |  parentTechnologies: [NewTechnology] @relation(name: "ChildTechnologies")
           |}""".stripMargin.trim()

      val renames = SchemaMapping(
        models = Vector(Mapping(previous = "Technology", next = "NewTechnology")),
        fields =
          Vector(FieldMapping(previousModel = "Technology", previousField = "childTechnologies", nextModel = "NewTechnology", nextField = "xTechnologies"))
      )

      val newSchema = infer(schema, newTypes, renames, capabilities = capas)
      newSchema.relations.foreach(println(_))

      val newRelation = newSchema.getRelationByName_!("ChildTechnologies")
      newRelation.modelAName should be("NewTechnology")
      newRelation.modelBName should be("NewTechnology")

      val field1 = newSchema.getModelByName_!("NewTechnology").getRelationFieldByName_!("xTechnologies")
      field1.relation should be(newRelation)
      field1.relationSide.toString should be("A")

      val field2 = newSchema.getModelByName_!("NewTechnology").getRelationFieldByName_!("parentTechnologies")
      field2.relation should be(newRelation)
      field2.relationSide.toString should be("B")
    }
  }

  "repair invalid assignments" in {
    val capas = ConnectorCapabilities(RelationLinkTableCapability)
    val types =
      """|type Technology {
         |  id: ID! @id
         |  name: String! @unique
         |  childTechnologies: [Technology] @relation(name: "ChildTechnologies")
         |  parentTechnologies: [Technology] @relation(name: "ChildTechnologies")
         |}""".stripMargin.trim()
    val schema = infer(emptyProject.schema, types, capabilities = capas)

    schema.relations should have(size(1))
    val relation = schema.getRelationByName_!("ChildTechnologies")
    relation.modelAName should equal("Technology")
    relation.modelBName should equal("Technology")
    relation.modelAField.name should be("childTechnologies")
    relation.modelBField.name should be("parentTechnologies")

    val techModel   = schema.models.head
    val parentField = techModel.getFieldByName_!("parentTechnologies")

    val updatedModel = techModel.copy(
      fieldTemplates = techModel.fields.filter(_ != parentField).map(_.template) :+ parentField.template.copy(relationSide = Some(RelationSide.A))
    )
    val invalidSchema = schema.copy(modelTemplates = List(updatedModel))

    val newSchema = infer(invalidSchema, types, capabilities = capas)
    newSchema.relations.foreach(println(_))

    val newRelation = newSchema.getRelationByName_!("ChildTechnologies")
    newRelation.modelAName should be("Technology")
    newRelation.modelBName should be("Technology")

    val field1 = newSchema.getModelByName_!("Technology").getRelationFieldByName_!("childTechnologies")
    field1.relation should be(newRelation)
    field1.relationSide.toString should be("A")

    val field2 = newSchema.getModelByName_!("Technology").getRelationFieldByName_!("parentTechnologies")
    field2.relation should be(newRelation)
    field2.relationSide.toString should be("B")
  }

  "handle optional backrelations" in {
    val types =
      """|type Technology {
         |  id: ID! @id
         |  name: String! @unique
         |  childTechnologies: [Technology]
         |}""".stripMargin.trim()
    val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(RelationLinkTableCapability))

    schema.relations should have(size(1))
    val relation = schema.relations.head
    relation.modelAName should equal("Technology")
    relation.modelBName should equal("Technology")
    relation.modelAField.name should be("childTechnologies")
  }

  "handle database manifestations for models" in {
    val types =
      """|type Todo @db(name:"todo_table"){
         |  id: ID! @id
         |  name: String!
         |}""".stripMargin
    val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities.empty)

    val model = schema.getModelByName_!("Todo")
    model.manifestation should equal(Some(ModelManifestation("todo_table")))
  }

  "handle pg database manifestations for fields" in {
    val types =
      """|type Todo {
         |  id: ID! @id
         |  name: String! @db(name: "my_name_column")
         |}""".stripMargin
    val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities.empty)

    val field = schema.getModelByName_!("Todo").getScalarFieldByName_!("name")
    field.manifestation should equal(Some(FieldManifestation("my_name_column")))
  }

  "handle inline relation manifestations on Mongo" in {
    val types =
      """
         |type List {
         |  id: ID! @id
         |  todos: [Todo]
         |}
         |
         |type Todo {
         |  id: ID! @id
         |  name: String!
         |  list: List @relation(link: INLINE)
         |}
         |""".stripMargin
    val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(RelationLinkListCapability))

    val relation = schema.getModelByName_!("List").getRelationFieldByName_!("todos").relation

    val expectedManifestation = EmbeddedRelationLink(inTableOfModelName = "Todo", referencingColumn = "list")
    relation.manifestation should equal(expectedManifestation)
  }

  "handle inline relation manifestations on the SQL" in {
    val types =
      """
        |type List {
        |  id: ID! @id
        |  todos: [Todo]
        |}
        |
        |type Todo {
        |  id: ID! @id
        |  name: String!
        |  list: List @relation(link: INLINE)
        |}
        |""".stripMargin
    val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities.empty)

    val relation = schema.getModelByName_!("List").getRelationFieldByName_!("todos").relation

    val expectedManifestation = EmbeddedRelationLink(inTableOfModelName = "Todo", referencingColumn = "list")
    relation.manifestation should equal(expectedManifestation)
  }

  "Do not add hidden fields if isActive is true" in {
    val types =
      """|type Todo {
         |  id: ID! @id
         |  name: String!
         |}""".stripMargin
    val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(MigrationsCapability))

    val model = schema.getModelByName_!("Todo")
    model.fields should have(size(2))
    model.fields.map(_.name) should equal(List("id", "name"))
  }

  "do not add hidden reserved fields if isActive is false" in {
    val types =
      """|type Todo {
         |  id: ID! @id
         |  name: String!
         |}""".stripMargin
    val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities.empty)

    val model = schema.getModelByName_!("Todo")
    model.fields should have(size(2))
    model.fields.map(_.name) should equal(List("id", "name"))
  }

  "should not blow up when no @relation is used on SQL" in {
    val types =
      """|type Todo {
         |  id: ID! @id
         |  name: String!
         |  comments: [Comment]
         |}
         |type Comment {
         |  id: ID! @id
         |  text: String
         |}
         |""".stripMargin
    val schema                = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(RelationLinkTableCapability))
    val relationField         = schema.getModelByName_!("Todo").getRelationFieldByName_!("comments")
    val expectedManifestation = RelationTable(table = "_CommentToTodo", modelAColumn = "A", modelBColumn = "B")
    relationField.relation.manifestation should be(expectedManifestation)
  }

  "should not blow up when no @relation is used on Mongo" in {
    val types =
      """|type Todo {
         |  id: ID! @id
         |  name: String!
         |  comments: [Comment] @relation(link: INLINE)
         |}
         |type Comment {
         |  id: ID! @id
         |  text: String
         |}
         |""".stripMargin
    val schema                = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(RelationLinkListCapability))
    val relationField         = schema.getModelByName_!("Todo").getRelationFieldByName_!("comments")
    val expectedManifestation = EmbeddedRelationLink("Todo", "comments")
    relationField.relation.manifestation should be(expectedManifestation)
  }

  "should make a field tagged with @id unique" in {
    val types =
      """|type Todo {
         |  id: ID! @id
         |}""".stripMargin

    val schema  = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities.empty)
    val idField = schema.getModelByName_!("Todo").getFieldByName_!("id")
    idField.isUnique should be(true)
  }

  "should support link tables in the most explicit notation" in {
    val types =
      """
        |type Model {
        |  id: ID! @id
        |  model: Model @relation(name: "ModelToModelRelation", link: TABLE)
        |}
        |
        |type ModelToModelRelation @relationTable {
        |  firstColumn: Model!
        |  secondColumn: Model!
        |}
      """.stripMargin
    val schema   = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(RelationLinkTableCapability))
    val relation = schema.relations.head
    relation.name should be("ModelToModelRelation")
    val manifestation = relation.manifestation.asInstanceOf[RelationTable]
    manifestation.modelAColumn should be("firstColumn")
    manifestation.modelBColumn should be("secondColumn")
    manifestation.idColumn should be(None)
  }

  "should support forcing link tables with only the link argument" in {
    val types =
      """|type Todo {
         |  id: ID! @id
         |  name: String!
         |  list: List!
         |}
         |
         |type List {
         |  id: ID! @id
         |  todos: [Todo] @relation(link: TABLE)
         |}
         |""".stripMargin
    val schema = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(RelationLinkTableCapability))

    val relation = schema.getModelByName_!("List").getRelationFieldByName_!("todos").relation
    // assert model ids to make sure that the generated manifestation refers to the right modelAColumn/modelBColumn
    relation.modelAName should equal("List")
    relation.modelBName should equal("Todo")

    val expectedManifestation = RelationTable(table = "_ListToTodo", modelAColumn = "A", modelBColumn = "B")
    relation.manifestation should equal(expectedManifestation)
  }

  "should support link tables if the link argument is missing by matching relation name and link table name" in {
    val types =
      """
        |type Model {
        |  id: ID! @id
        |  model: Model @relation(name: "ModelToModelRelation")
        |}
        |
        |type ModelToModelRelation @relationTable {
        |  firstColumn: Model!
        |  secondColumn: Model!
        |}
      """.stripMargin
    val schema   = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(RelationLinkTableCapability))
    val relation = schema.relations.head
    relation.name should be("ModelToModelRelation")
    val manifestation = relation.manifestation.asInstanceOf[RelationTable]
    manifestation.modelAColumn should be("firstColumn")
    manifestation.modelBColumn should be("secondColumn")
    manifestation.idColumn should be(None)
  }

  "should support the legacy style link tables" in {
    val types =
      """
        |type Model {
        |  id: ID! @id
        |  model: Model @relation(name: "ModelToModelRelation", link: TABLE)
        |}
        |
        |type ModelToModelRelation @relationTable {
        |  idColumn: ID! @id @db(name: "id_column")
        |  firstColumn: Model!
        |  secondColumn: Model!
        |}
      """.stripMargin
    val schema   = infer(emptyProject.schema, types, capabilities = ConnectorCapabilities(RelationLinkTableCapability))
    val relation = schema.relations.head
    relation.name should be("ModelToModelRelation")
    val manifestation = relation.manifestation.asInstanceOf[RelationTable]
    manifestation.modelAColumn should be("firstColumn")
    manifestation.modelBColumn should be("secondColumn")
    manifestation.idColumn should be(Some("id_column"))
  }

  def infer(schema: Schema, types: String, mapping: SchemaMapping = SchemaMapping.empty, capabilities: ConnectorCapabilities): Schema = {
    val prismaSdl = DataModelValidatorImpl.validate(types, capabilities).get.dataModel
    SchemaInferrer(capabilities).infer(schema, mapping, prismaSdl)
  }
}
