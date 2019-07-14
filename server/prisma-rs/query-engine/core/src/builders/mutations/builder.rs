//! Providing an interface to build WriteQueries

use crate::{builders::utils, CoreError, CoreResult, WriteQuery};
use connector::mutaction::{CreateNode, DeleteNode, DeleteNodes, TopLevelDatabaseMutaction, UpdateNode, UpsertNode};
use graphql_parser::query::{Field, Value};
use prisma_models::{InternalDataModelRef, ModelRef, PrismaArgs, PrismaValue};

use crate::Inflector;
use rust_inflector::Inflector as RustInflector;

use std::collections::BTreeMap;
use std::sync::Arc;

/// A TopLevelMutation builder
///
/// It takes a graphql field and internal_data_model
/// and builds a mutation tree from it
#[derive(Debug)]
pub struct MutationBuilder<'field> {
    field: &'field Field,
    internal_data_model: InternalDataModelRef,
}

type PrismaListArgs = Vec<(String, Option<Vec<PrismaValue>>)>;

impl<'field> MutationBuilder<'field> {
    pub fn new(internal_data_model: InternalDataModelRef, field: &'field Field) -> Self {
        Self {
            field,
            internal_data_model,
        }
    }

    pub fn build(self) -> CoreResult<WriteQuery> {
        let (non_list_args, list_args) = get_mutation_args(&self.field.arguments);
        let (op, model) = parse_model_action(
            self.field.alias.as_ref().unwrap_or_else(|| &self.field.name),
            Arc::clone(&self.internal_data_model),
        )?;

        let inner = match op {
            Operation::Create => TopLevelDatabaseMutaction::CreateNode(CreateNode {
                model,
                non_list_args,
                list_args,
                nested_mutactions: Default::default(),
            }),
            Operation::Update => TopLevelDatabaseMutaction::UpdateNode(UpdateNode {
                where_: utils::extract_node_selector(self.field, Arc::clone(&model))?,
                non_list_args,
                list_args,
                nested_mutactions: Default::default(),
            }),
            Operation::Delete => TopLevelDatabaseMutaction::DeleteNode(DeleteNode {
                where_: utils::extract_node_selector(self.field, Arc::clone(&model))?,
            }),
            Operation::DeleteMany => TopLevelDatabaseMutaction::DeleteNodes(DeleteNodes {
                model,
                filter: unsafe { std::mem::uninitialized() }, // BOOM
            }),
            Operation::Upsert => TopLevelDatabaseMutaction::UpsertNode(UpsertNode {
                where_: utils::extract_node_selector(self.field, Arc::clone(&model))?,
                create: CreateNode {
                    model: Arc::clone(&model),
                    non_list_args: non_list_args.clone(),
                    list_args: list_args.clone(),
                    nested_mutactions: Default::default(),
                },
                update: UpdateNode {
                    where_: utils::extract_node_selector(self.field, Arc::clone(&model))?,
                    non_list_args,
                    list_args,
                    nested_mutactions: Default::default(),
                },
            }),
            _ => unimplemented!(),
        };

        // FIXME: Cloning is unethical and should be avoided
        Ok(WriteQuery {
            inner,
            field: self.field.clone(),
            nested: vec![],
        })
    }
}

/// Extract String-Value pairs into usable mutation arguments
fn get_mutation_args(args: &Vec<(String, Value)>) -> (PrismaArgs, PrismaListArgs) {
    let (args, lists) = args
        .iter()
        .fold((BTreeMap::new(), vec![]), |(mut map, mut vec), (_, v)| {
            match v {
                Value::Object(o) => o.iter().for_each(|(k, v)| {
                    // If the child is an object, we are probably dealing with ScalarList values
                    match v {
                        Value::Object(o) if o.contains_key("set") => {
                            vec.push((
                                k.clone(),
                                match o.get("set") {
                                    Some(Value::List(l)) => Some(
                                        l.iter()
                                            .map(|v| PrismaValue::from_value(v))
                                            .collect::<Vec<PrismaValue>>(),
                                    ),
                                    None => None,
                                    _ => unimplemented!(), // or unreachable? dunn duuuuun!
                                },
                            ));
                        }
                        v => {
                            map.insert(k.clone(), PrismaValue::from_value(v));
                        }
                    }
                }),
                _ => panic!("Unknown argument structure!"),
            }

            (map, vec)
        });
    (args.into(), lists)
}

/// A simple enum to discriminate top-level actions
#[allow(dead_code)] // FIXME: Remove!
enum Operation {
    Create,
    Update,
    Delete,
    Upsert,
    UpdateMany,
    DeleteMany,
    Reset,
}

impl From<&str> for Operation {
    fn from(s: &str) -> Self {
        match s {
            "create" => Operation::Create,
            "update" => Operation::Update,
            "updateMany" => Operation::UpdateMany,
            "delete" => Operation::Delete,
            "deleteMany" => Operation::DeleteMany,
            "upsert" => Operation::Upsert,
            _ => unimplemented!(),
        }
    }
}

/// Parse the mutation name into an action and the model it should operate on
fn parse_model_action(name: &String, internal_data_model: InternalDataModelRef) -> CoreResult<(Operation, ModelRef)> {
    let actions = vec!["create", "updateMany", "update", "deleteMany", "delete", "upsert"];

    let action = match actions.iter().find(|action| name.starts_with(*action)) {
        Some(a) => a,
        None => return Err(CoreError::QueryValidationError(format!("Unknown action: {}", name))),
    };
    let split: Vec<&str> = name.split(action).collect();
    let model_name = match split.get(1) {
        Some(mn) => mn,
        None => {
            return Err(CoreError::QueryValidationError(format!(
                "No model name for action `{}`",
                name
            )))
        }
    };

    let normalized = dbg!(Inflector::singularize(model_name).to_pascal_case());
    let model = match internal_data_model.models().iter().find(|m| m.name == normalized) {
        Some(m) => m,
        None => {
            return Err(CoreError::QueryValidationError(format!(
                "Model not found for mutation {}",
                name
            )))
        }
    };

    Ok((Operation::from(*action), Arc::clone(&model)))
}
