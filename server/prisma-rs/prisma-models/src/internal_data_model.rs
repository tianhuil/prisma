use crate::prelude::*;
use once_cell::sync::OnceCell;
use std::sync::{Arc, Weak};

pub type InternalDataModelRef = Arc<InternalDataModel>;
pub type InternalDataModelWeakRef = Weak<InternalDataModel>;

#[derive(Debug, Deserialize, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct InternalDataModelTemplate {
    pub models: Vec<ModelTemplate>,
    pub relations: Vec<RelationTemplate>,
    pub enums: Vec<InternalEnum>,
    pub version: Option<String>,
}

#[derive(DebugStub)]
pub struct InternalDataModel {
    pub enums: Vec<InternalEnum>,
    pub version: Option<String>,
    pub db_name: String,

    models: OnceCell<Vec<ModelRef>>,
    relations: OnceCell<Vec<RelationRef>>,
    relation_fields: OnceCell<Vec<RelationFieldRef>>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InternalEnum {
    pub name: String,
    pub values: Vec<String>,
}

impl InternalDataModelTemplate {
    pub fn build(self, db_name: String) -> InternalDataModelRef {
        let internal_data_model = Arc::new(InternalDataModel {
            models: OnceCell::new(),
            relations: OnceCell::new(),
            enums: self.enums,
            version: self.version,
            db_name: db_name,
            relation_fields: OnceCell::new(),
        });

        let models = self
            .models
            .into_iter()
            .map(|mt| mt.build(Arc::downgrade(&internal_data_model)))
            .collect();

        internal_data_model.models.set(models).unwrap();

        let relations = self
            .relations
            .into_iter()
            .map(|rt| rt.build(Arc::downgrade(&internal_data_model)))
            .collect();

        internal_data_model.relations.set(relations).unwrap();
        internal_data_model
    }
}

impl InternalDataModel {
    pub fn models(&self) -> &[ModelRef] {
        self.models.get().unwrap()
    }

    pub fn relations(&self) -> &[RelationRef] {
        self.relations.get().unwrap().as_slice()
    }

    pub fn find_model(&self, name: &str) -> DomainResult<ModelRef> {
        self.models
            .get()
            .and_then(|models| models.iter().find(|model| model.name == name))
            .cloned()
            .ok_or_else(|| DomainError::ModelNotFound { name: name.to_string() })
    }

    pub fn find_relation(&self, name: &str) -> DomainResult<RelationWeakRef> {
        self.relations
            .get()
            .and_then(|relations| relations.iter().find(|relation| relation.name == name))
            .map(|relation| Arc::downgrade(&relation))
            .ok_or_else(|| DomainError::RelationNotFound { name: name.to_string() })
    }

    pub fn is_legacy(&self) -> bool {
        self.version.is_none()
    }

    pub fn fields_requiring_model(&self, model: ModelRef) -> Vec<RelationFieldRef> {
        self.relation_fields()
            .into_iter()
            .filter(|rf| rf.related_model() == model)
            .filter(|f| f.is_required && !f.is_list)
            .map(|f| Arc::clone(f))
            .collect()
    }

    pub fn relation_fields(&self) -> &[RelationFieldRef] {
        self.relation_fields
            .get_or_init(|| {
                self.models()
                    .iter()
                    .flat_map(|model| model.fields().relation())
                    .collect()
            })
            .as_slice()
    }
}
