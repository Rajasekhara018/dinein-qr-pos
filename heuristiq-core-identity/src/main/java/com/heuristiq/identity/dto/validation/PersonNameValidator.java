package com.heuristiq.uam.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link ValidPersonName}: trimmed length first, then the allowed characters. */
public class PersonNameValidator implements ConstraintValidator<ValidPersonName, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < PersonNameConstants.MIN_LENGTH || length > PersonNameConstants.MAX_LENGTH) {
            return reject(context, PersonNameConstants.INVALID_LENGTH_MESSAGE);
        }
        return PersonNameConstants.NAME_PATTERN.matcher(trimmed).matches()
                || reject(context, PersonNameConstants.INVALID_CHARACTERS_MESSAGE);
    }

    private static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
