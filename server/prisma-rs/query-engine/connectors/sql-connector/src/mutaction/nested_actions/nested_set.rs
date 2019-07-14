use super::*;
use crate::SqlResult;
use connector::mutaction::NestedSet;
use prisma_models::*;
use prisma_query::ast::*;

impl NestedActions for NestedSet {
    fn relation_field(&self) -> RelationFieldRef {
        self.relation_field.clone()
    }

    fn relation(&self) -> RelationRef {
        self.relation_field().relation()
    }

    fn required_check(&self, _: &GraphqlId) -> SqlResult<Option<(Select, ResultCheck)>> {
        Ok(None)
    }

    fn parent_removal(&self, _: &GraphqlId) -> Option<Query> {
        None
    }

    fn child_removal(&self, _: &GraphqlId) -> Option<Query> {
        None
    }
}
