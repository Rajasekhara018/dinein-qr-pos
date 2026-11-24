package com.heuristiq.phone.validation;

import com.heuristiq.phone.PhoneNumbers;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implements {@link ValidPhone}. */
public class ValidPhoneValidator implements ConstraintValidator<ValidPhone, CharSequence> {

    private String region;

    @Override
    public void initialize(ValidPhone constraint) {
        this.region = constraint.region();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.toString().isBlank()) {
            return true;
        }
        return PhoneNumbers.isValid(value.toString(), region);
    }
}
