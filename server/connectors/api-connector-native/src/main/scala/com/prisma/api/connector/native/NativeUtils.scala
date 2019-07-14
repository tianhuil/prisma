package com.prisma.api.connector.native
import com.prisma.api.connector._
import com.prisma.gc_values._
import com.prisma.rs.NodeResult
import com.prisma.shared.models.{Model, RelationField}
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.Json
import prisma.protocol
import prisma.protocol.GraphqlId.IdValue
import prisma.protocol.ValueContainer
import prisma.protocol.ValueContainer.PrismaValue

import scala.collection.mutable

object NativeUtils {
  def prismaNodeFromNodeResult(nodeResult: NodeResult): PrismaNode = PrismaNode(nodeResult.id, nodeResult.data)

  val isoFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

  def transformNode(node: (protocol.Node, Vector[String]), model: Model): PrismaNode = {
    val idField = model.idField_!
    val idIndex = node._2.indexWhere(idField.name == _)
    val idValue = toGcValue(node._1.values.toVector(idIndex).prismaValue)

    val rootMap = node._1.values.zipWithIndex.foldLeft(mutable.Map.empty[String, GCValue]) {
      case (acc, (value: protocol.ValueContainer, index: Int)) =>
        acc += (node._2(index) -> toGcValue(value.prismaValue))
    }

    PrismaNode(idValue.asInstanceOf[IdGCValue], RootGCValue(rootMap.toMap), typeName = Some(model.name))
  }

  def toGcValue(value: protocol.ValueContainer.PrismaValue): GCValue = {
    value match {
      case PrismaValue.Empty                => NullGCValue
      case PrismaValue.Boolean(b: Boolean)  => BooleanGCValue(b)
      case PrismaValue.DateTime(dt: String) => DateTimeGCValue(DateTime.parse(dt))
      case PrismaValue.Enum(e: String)      => EnumGCValue(e)
      case PrismaValue.Float(f)             => FloatGCValue(f)
      case PrismaValue.GraphqlId(id)        => toIdGcValue(id)
      case PrismaValue.Int(i: Int)          => IntGCValue(i)
      case PrismaValue.Json(j: String)      => JsonGCValue(Json.parse(j))
      case PrismaValue.Null(_)              => NullGCValue
      case PrismaValue.Relation(r: Long)    => ??? // What are we supposed to do here?
      case PrismaValue.String(s: String)    => StringGCValue(s)
      case PrismaValue.Uuid(uuid: String)   => UuidGCValue.parse(uuid).get
      case PrismaValue.List(values) =>
        val gcValues = values.values.map(x => toGcValue(x.prismaValue))
        ListGCValue(gcValues.toVector)
    }
  }

  def toIdGcValue(id: protocol.GraphqlId): IdGCValue = {
    id.idValue match {
      case IdValue.String(s) => StringIdGCValue(s)
      case IdValue.Uuid(s)   => UuidGCValue.parse_!(s)
      case IdValue.Int(i)    => IntGCValue(i.toInt)
      case _                 => sys.error("empty protobuf")
    }
  }

  def toValueContainer(value: GCValue): ValueContainer = protocol.ValueContainer(toPrismaValue(value))

  def toPrismaValue(value: GCValue): PrismaValue = {
    value match {
      case NullGCValue         => PrismaValue.Null(true)
      case BooleanGCValue(b)   => PrismaValue.Boolean(b)
      case DateTimeGCValue(dt) => PrismaValue.DateTime(isoFormatter.print(dt.withZone(DateTimeZone.UTC)))
      case EnumGCValue(e)      => PrismaValue.Enum(e)
      case FloatGCValue(f)     => PrismaValue.Float(f)
      case StringIdGCValue(s)  => PrismaValue.GraphqlId(prisma.protocol.GraphqlId(IdValue.String(s)))
      case UuidGCValue(uuid)   => PrismaValue.GraphqlId(prisma.protocol.GraphqlId(IdValue.Uuid(uuid.toString)))
      case IntGCValue(i)       => PrismaValue.GraphqlId(prisma.protocol.GraphqlId(IdValue.Int(i)))
      case JsonGCValue(j)      => PrismaValue.Json(j.toString())
      case StringGCValue(s)    => PrismaValue.String(s)
      case ListGCValue(values) =>
        val listValues = values.map(toValueContainer)
        PrismaValue.List(prisma.protocol.PrismaListValue(listValues));
      case _ => sys.error(s"Not supported: $value")
    }
  }

  def toPrismaId(value: IdGCValue): protocol.GraphqlId = value match {
    case StringIdGCValue(s) => protocol.GraphqlId(IdValue.String(s))
    case IntGCValue(i)      => protocol.GraphqlId(IdValue.Int(i))
    case UuidGCValue(u)     => protocol.GraphqlId(IdValue.Uuid(u.toString))
  }

  def toPrismaSelectedFields(selectedFields: SelectedFields): prisma.protocol.SelectedFields = {
    val fields = selectedFields.fields.foldLeft(Vector[prisma.protocol.SelectedField]()) { (acc, selectedField) =>
      selectedField match {
        case SelectedScalarField(f) => {
          val field = prisma.protocol.SelectedField(
            prisma.protocol.SelectedField.Field.Scalar(f.name)
          )

          acc :+ field
        }
        case SelectedRelationField(f, sf) => {
          val field = prisma.protocol.SelectedField(
            prisma.protocol.SelectedField.Field.Relational(
              prisma.protocol.RelationalField(
                f.name,
                toPrismaSelectedFields(sf)
              ))
          )

          acc :+ field
        }
      }
    }
    prisma.protocol.SelectedFields(fields)
  }

  def toPrismaCondition(scalarCondition: ScalarCondition): protocol.ScalarFilter.Condition = {
    scalarCondition match {
      case Equals(value) =>
        protocol.ScalarFilter.Condition.Equals(protocol.ValueContainer(toPrismaValue(value)))
      case NotEquals(value) =>
        protocol.ScalarFilter.Condition.NotEquals(protocol.ValueContainer(toPrismaValue(value)))
      case Contains(value) =>
        protocol.ScalarFilter.Condition.Contains(protocol.ValueContainer(toPrismaValue(value)))
      case NotContains(value) =>
        protocol.ScalarFilter.Condition.NotContains(protocol.ValueContainer(toPrismaValue(value)))
      case StartsWith(value) =>
        protocol.ScalarFilter.Condition.StartsWith(protocol.ValueContainer(toPrismaValue(value)))
      case NotStartsWith(value) =>
        protocol.ScalarFilter.Condition.NotStartsWith(protocol.ValueContainer(toPrismaValue(value)))
      case EndsWith(value) =>
        protocol.ScalarFilter.Condition.EndsWith(protocol.ValueContainer(toPrismaValue(value)))
      case NotEndsWith(value) =>
        protocol.ScalarFilter.Condition.NotEndsWith(protocol.ValueContainer(toPrismaValue(value)))
      case LessThan(value) =>
        protocol.ScalarFilter.Condition.LessThan(protocol.ValueContainer(toPrismaValue(value)))
      case LessThanOrEquals(value) =>
        protocol.ScalarFilter.Condition.LessThanOrEquals(protocol.ValueContainer(toPrismaValue(value)))
      case GreaterThan(value) =>
        protocol.ScalarFilter.Condition.GreaterThan(protocol.ValueContainer(toPrismaValue(value)))
      case GreaterThanOrEquals(value) =>
        protocol.ScalarFilter.Condition.GreaterThanOrEquals(protocol.ValueContainer(toPrismaValue(value)))
      case In(values) =>
        protocol.ScalarFilter.Condition.In(protocol.MultiContainer(values.map(v => protocol.ValueContainer(toPrismaValue(v)))))
      case NotIn(values) =>
        protocol.ScalarFilter.Condition.NotIn(protocol.MultiContainer(values.map(v => protocol.ValueContainer(toPrismaValue(v)))))
    }
  }

  def toPrismaListCondition(scalarListCondition: ScalarListCondition): protocol.ScalarListCondition = {
    val condition = scalarListCondition match {
      case ListContains(value) =>
        protocol.ScalarListCondition.Condition.Contains(protocol.ValueContainer(toPrismaValue(value)))
      case ListContainsEvery(values) =>
        protocol.ScalarListCondition.Condition.ContainsEvery(protocol.MultiContainer(values.map(v => protocol.ValueContainer(toPrismaValue(v)))))
      case ListContainsSome(values) =>
        protocol.ScalarListCondition.Condition.ContainsSome(protocol.MultiContainer(values.map(v => protocol.ValueContainer(toPrismaValue(v)))))
    }

    protocol.ScalarListCondition(condition)
  }

  def toRelationFilterCondition(condition: RelationCondition): protocol.RelationFilter.Condition = {
    condition match {
      case EveryRelatedNode      => protocol.RelationFilter.Condition.EVERY_RELATED_NODE
      case AtLeastOneRelatedNode => protocol.RelationFilter.Condition.AT_LEAST_ONE_RELATED_NODE
      case NoRelatedNode         => protocol.RelationFilter.Condition.NO_RELATED_NODE
      case ToOneRelatedNode      => protocol.RelationFilter.Condition.TO_ONE_RELATED_NODE
    }
  }

  def toProtocolFilter(filter: Filter): protocol.Filter = {
    filter match {
      case AndFilter(filters) =>
        protocol.Filter(
          protocol.Filter.Type.And(
            protocol.AndFilter(filters.map(toProtocolFilter))
          )
        )
      case OrFilter(filters) =>
        protocol.Filter(
          protocol.Filter.Type.Or(
            protocol.OrFilter(filters.map(toProtocolFilter))
          )
        )
      case NotFilter(filters) =>
        protocol.Filter(
          protocol.Filter.Type.Not(
            protocol.NotFilter(filters.map(toProtocolFilter))
          )
        )
      case ScalarFilter(field, scalarCondition) =>
        protocol.Filter(
          protocol.Filter.Type.Scalar(
            protocol.ScalarFilter(field.name, toPrismaCondition(scalarCondition))
          )
        )
      case ScalarListFilter(field, scalarListCondition) =>
        protocol.Filter(
          protocol.Filter.Type.ScalarList(protocol.ScalarListFilter(field.name, toPrismaListCondition(scalarListCondition)))
        )
      case OneRelationIsNullFilter(field) =>
        protocol.Filter(
          protocol.Filter.Type.OneRelationIsNull(protocol.RelationalField(field.name, protocol.SelectedFields(Vector.empty)))
        )
      case RelationFilter(field, nestedFilter, condition) =>
        protocol.Filter(
          protocol.Filter.Type.Relation(
            protocol.RelationFilter(
              protocol.RelationalField(field.name, protocol.SelectedFields(Vector.empty)),
              toProtocolFilter(nestedFilter),
              toRelationFilterCondition(condition)
            )
          )
        )
      case NodeSubscriptionFilter =>
        protocol.Filter(
          protocol.Filter.Type.NodeSubscription(true)
        )
      case TrueFilter =>
        protocol.Filter(
          protocol.Filter.Type.BoolFilter(true)
        )
      case FalseFilter =>
        protocol.Filter(
          protocol.Filter.Type.BoolFilter(false)
        )
    }
  }

  def toPrismaOrderBy(orderBy: OrderBy): protocol.OrderBy = {
    protocol.OrderBy(orderBy.field.name, orderBy.sortOrder match {
      case SortOrder.Asc  => protocol.OrderBy.SortOrder.ASC
      case SortOrder.Desc => protocol.OrderBy.SortOrder.DESC
    })
  }

  def toPrismaArguments(queryArguments: QueryArguments): protocol.QueryArguments = {
    protocol.QueryArguments(
      queryArguments.skip,
      queryArguments.after.map(toPrismaId),
      queryArguments.first,
      queryArguments.before.map(toPrismaId),
      queryArguments.last,
      queryArguments.filter.map(toProtocolFilter),
      queryArguments.orderBy.map(toPrismaOrderBy)
    )
  }

  def toRelationalField(field: RelationField): protocol.RelationalField = {
    protocol.RelationalField(field.name, protocol.SelectedFields(Vector.empty))
  }

  def toNodeSelector(where: NodeSelector): protocol.NodeSelector = {
    protocol.NodeSelector(
      modelName = where.model.name,
      fieldName = where.field.name,
      value = toValueContainer(where.fieldGCValue),
    )
  }
}
