package com.heuristiq.identity.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the concrete DTO carried in {@code CommonRequestObject#getRequestObject()} for a
 * controller method, since that field is typed {@code Object} and Jackson resolves it at runtime
 * via {@link CommonRequestMapper#requireRequestObject}. {@link com.heuristiq.identity.config.RequestObjectSchemaCustomizer}
 * reads this to swap the generic OpenAPI schema for the real one, so Swagger UI shows the actual
 * field names and descriptions instead of an opaque object.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequestObjectType {
    Class<?> value();
}
