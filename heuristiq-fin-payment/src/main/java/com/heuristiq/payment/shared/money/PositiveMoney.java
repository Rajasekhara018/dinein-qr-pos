package com.heuristiq.paymentservice.shared.money;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A {@link Money} whose value is greater than zero; null is valid (combine with {@code @NotNull} when required). */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PositiveMoney.Validator.class)
public @interface PositiveMoney {

    /** @return the message when the amount is zero or negative */
    String message() default "must be greater than 0";

    /** @return validation groups */
    Class<?>[] groups() default {};

    /** @return payload */
    Class<? extends Payload>[] payload() default {};

    /** Checks the value of the amount. */
    class Validator implements ConstraintValidator<PositiveMoney, Money> {
        @Override
        public boolean isValid(Money money, ConstraintValidatorContext context) {
            return money == null || money.value().signum() > 0;
        }
    }
}
