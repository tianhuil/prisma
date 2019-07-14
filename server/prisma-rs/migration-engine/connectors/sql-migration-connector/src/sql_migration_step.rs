use datamodel::ScalarType;
use migration_connector::DatabaseMigrationStepExt;
use serde::Serialize;

impl DatabaseMigrationStepExt for SqlMigrationStep {}

#[derive(Debug, Serialize)]
pub enum SqlMigrationStep {
    CreateTable(CreateTable),
    AlterTable(AlterTable),
    DropTable(DropTable),
}

#[derive(Debug, Serialize)]
pub struct CreateTable {
    pub name: String,
    pub columns: Vec<ColumnDescription>,
    pub primary_columns: Vec<String>,
}

#[derive(Debug, Serialize)]
pub struct DropTable {
    pub name: String,
}

#[derive(Debug, Serialize)]
pub struct AlterTable {
    pub table: String,
    pub changes: Vec<TableChange>,
}

#[derive(Debug, Serialize)]
pub enum TableChange {
    AddColumn(AddColumn),
    AlterColumn(AlterColumn),
    DropColumn(DropColumn),
}

#[derive(Debug, Serialize)]
pub struct AddColumn {
    pub column: ColumnDescription,
}

#[derive(Debug, Serialize)]
pub struct DropColumn {
    pub name: String,
}

#[derive(Debug, Serialize)]
pub struct AlterColumn {
    pub name: String,
    pub column: ColumnDescription,
}

#[derive(Debug, Serialize, Clone)]
pub struct ColumnDescription {
    pub name: String,
    pub tpe: ColumnType,
    pub required: bool,
}

#[derive(Debug, Copy, PartialEq, Eq, Clone, Serialize)]
pub enum ColumnType {
    Int,
    Float,
    Boolean,
    String,
    DateTime,
}
