package com.prisma.deploy.connector.jdbc

import com.prisma.connector.shared.jdbc.{MySqlDialect, PostgresDialect, SlickDatabase}
import com.prisma.deploy.connector._
import com.prisma.deploy.connector.jdbc.DatabaseInspectorBase.{IntrospectedColumn, IntrospectedForeignKey, IntrospectedSequence}
import com.prisma.shared.models.TypeIdentifier.ScalarTypeIdentifier
import slick.dbio.DBIO
import slick.jdbc.GetResult

import scala.concurrent.{ExecutionContext, Future}

trait DatabaseInspectorBase extends DatabaseInspector {
  val db: SlickDatabase
  implicit def ec: ExecutionContext
  import db.profile.api.actionBasedSQLInterpolation

  override def inspect(schema: String): Future[DatabaseSchema] = db.database.run(action(schema))

  def action(schema: String): DBIO[DatabaseSchema] = {
    for {
      tableNames <- getTableNames(schema)
      tables     <- DBIO.sequence(tableNames.map(name => getTable(schema, name)))
    } yield {
      DatabaseSchema(tables)
    }
  }

  private def getTableNames(schema: String): DBIO[Vector[String]] = {
    sql"""
         |SELECT
         |  table_name
         |FROM
         |  information_schema.tables
         |WHERE
         |  table_schema = $schema AND
         |  -- Views are not supported yet
         |  table_type = 'BASE TABLE'
       """.stripMargin.as[String]
  }

  private def getTable(schema: String, table: String): DBIO[Table] = {
    for {
      introspectedColumns     <- getColumns(schema, table)
      introspectedForeignKeys <- foreignKeyConstraints(schema, table)
      introspectedIndexes     <- indexes(schema, table)
      sequences               <- getSequences(schema, table)
    } yield {
      val columns = introspectedColumns.map { col =>
        // this needs to be extended further in the future if we support arbitrary SQL types
        val typeIdentifier = typeIdentifierForTypeName(col.udtName).getOrElse {
          sys.error(s"Encountered unknown SQL type ${col.udtName} with column ${col.name}. $col")
        }
        val fk = introspectedForeignKeys.find(fk => fk.column == col.name).map { fk =>
          ForeignKey(fk.referencedTable, fk.referencedColumn)
        }
        val sequence = sequences.find(_.column == col.name).map { mseq =>
          Sequence(mseq.name, mseq.current)
        }
        Column(
          name = col.name,
          tpe = col.udtName,
          typeIdentifier = typeIdentifier,
          isRequired = !col.isNullable,
          foreignKey = fk,
          sequence = sequence
        )(_)
      }
      Table(table, columns, indexes = introspectedIndexes)
    }
  }

  private def getColumns(schema: String, table: String): DBIO[Vector[IntrospectedColumn]] = {
    sql"""
         |SELECT
         |  cols.ordinal_position,
         |  cols.column_name,
         |  cols.#$dataTypeColumn,
         |  cols.column_default,
         |  cols.is_nullable = 'YES' as is_nullable
         |FROM
         |  information_schema.columns AS cols
         |WHERE
         |  cols.table_schema = $schema
         |  AND cols.table_name  = $table
          """.stripMargin.as[IntrospectedColumn]
  }

  protected def typeIdentifierForTypeName(typeName: String): Option[ScalarTypeIdentifier]

  protected def foreignKeyConstraints(schema: String, table: String): DBIO[Vector[IntrospectedForeignKey]]

  protected def getSequences(schema: String, table: String): DBIO[Vector[IntrospectedSequence]]

  protected def indexes(schema: String, table: String): DBIO[Vector[Index]]

  /**
    * RESULT CONVERTERS
    */
  implicit lazy val introspectedColumnGetResult: GetResult[IntrospectedColumn] = GetResult { ps =>
    IntrospectedColumn(
      name = ps.rs.getString("column_name"),
      udtName = ps.rs.getString(dataTypeColumn),
      default = ps.rs.getString("column_default"),
      isNullable = ps.rs.getBoolean("is_nullable")
    )
  }

  /**
    * Other Helpers
    */
  private val dataTypeColumn: String = db.prismaDialect match {
    case PostgresDialect => "udt_name"
    case MySqlDialect    => "DATA_TYPE"
    case x               => sys.error(s"$x is not implemented yet.")
  }
}

object DatabaseInspectorBase {

  // intermediate helper classes
  case class IntrospectedColumn(name: String, udtName: String, default: String, isNullable: Boolean)
  case class IntrospectedForeignKey(name: String, table: String, column: String, referencedTable: String, referencedColumn: String)
  case class IntrospectedSequence(column: String, name: String, current: Int)

  implicit val introspectedForeignKeyGetResult: GetResult[IntrospectedForeignKey] = GetResult { pr =>
    IntrospectedForeignKey(
      name = pr.rs.getString("fkConstraintName"),
      table = pr.rs.getString("fkTableName"),
      column = pr.rs.getString("fkColumnName"),
      referencedTable = pr.rs.getString("referencedTableName"),
      referencedColumn = pr.rs.getString("referencedColumnName")
    )
  }

  implicit val indexGetResult: GetResult[Index] = GetResult { pr =>
    val columns = pr.rs.getArray("column_names").getArray.asInstanceOf[Array[String]]
    Index(
      name = pr.rs.getString("index_name"),
      columns = columns.toVector,
      unique = pr.rs.getBoolean("is_unique")
    )
  }

}
