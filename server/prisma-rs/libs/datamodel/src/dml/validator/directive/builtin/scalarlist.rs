use crate::dml;
use crate::dml::validator::directive::{Args, DirectiveValidator, Error};

pub struct ScalarListDirectiveValidator {}

impl DirectiveValidator<dml::Field> for ScalarListDirectiveValidator {
    fn directive_name(&self) -> &'static str {
        &"scalarList"
    }
    fn validate_and_apply(&self, args: &Args, obj: &mut dml::Field) -> Option<Error> {
        // TODO: Throw when field is not of type scalar and arity is list.
        // TODO: We can probably lift this pattern to a macro.

        if let Ok(strategy) = args.arg("strategy").as_constant_literal() {
            match strategy.parse::<dml::ScalarListStrategy>() {
                Ok(strategy) => obj.scalar_list_strategy = Some(strategy),
                Err(err) => return self.parser_error(&err),
            }
        }

        return None;
    }
}
