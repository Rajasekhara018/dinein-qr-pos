package com.heuristiq.identity.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A person's name: see {@link PersonNameConstants}. Checked on the trimmed value; {@code null} and blank are left to
 * {@code @NotBlank} so a missing name reports one error only.
 */
@Documented
@Constraint(validatedBy = PersonNameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPersonName {

    /** Default message (the validator replaces it with the precise one). */
    String message() default PersonNameConstants.INVALID_CHARACTERS_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
