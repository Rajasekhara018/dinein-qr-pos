package com.heuristiq.phone.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The text must be a valid phone number of any country: written with a country code (+1 415 555 2671), or in the national
 * format of the default region ({@link com.heuristiq.phone.PhoneNumbers#defaultRegion()}) when it has none. A null or blank
 * value is valid; combine with {@code @NotBlank} where the number is required.
 */
@Documented
@Constraint(validatedBy = ValidPhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    String message() default "must be a valid phone number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Region (ISO 3166 alpha-2) a number without a country code is read in; empty means the default region. */
    String region() default "";
}
