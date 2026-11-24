package com.railcab.nms.infrastructure.config;

import java.util.LinkedHashMap;

import org.springdoc.core.customizers.GlobalOperationComponentsCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import com.railcab.nms.shared.RequestObjectType;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Every write endpoint in this service accepts the same {@code CommonRequestObject} envelope,
 * whose actual per-endpoint payload travels in the {@code Object requestObject} field and is
 * resolved server-side by {@code CommonRequestMapper}. Because that field's static type is
 * {@code Object}, springdoc has no way to document its real shape -- Swagger UI just renders it
 * as an opaque placeholder.
 *
 * <p>For every operation whose handler method carries {@link RequestObjectType}, this customizer
 * builds a per-operation copy of the envelope schema with {@code requestObject} pointed at the
 * real DTO's schema, and repoints that operation's request body at the copy. The shared
 * {@code CommonRequestObject} component schema (used by every other endpoint) is left untouched.
 */
@Component
public class RequestObjectSchemaCustomizer implements GlobalOperationComponentsCustomizer {

    // Springdoc's AbstractOpenApiResource always prefers this 3-arg overload for a
    // GlobalOperationComponentsCustomizer, so the 2-arg method below is never actually invoked --
    // it only exists to satisfy the (redundant) OperationCustomizer supertype method.
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        return operation;
    }

    @Override
    public Operation customize(Operation operation, Components components, HandlerMethod handlerMethod) {
        RequestObjectType requestObjectType = handlerMethod.getMethodAnnotation(RequestObjectType.class);
        if (requestObjectType == null || operation.getRequestBody() == null
                || operation.getRequestBody().getContent() == null) {
            return operation;
        }
        MediaType mediaType = operation.getRequestBody().getContent().get("application/json");
        if (mediaType == null || mediaType.getSchema() == null || mediaType.getSchema().get$ref() == null) {
            return operation;
        }

        String envelopeRef = mediaType.getSchema().get$ref();
        String envelopeName = envelopeRef.substring(envelopeRef.lastIndexOf('/') + 1);
        Schema<?> envelopeSchema = components.getSchemas() == null ? null : components.getSchemas().get(envelopeName);
        if (envelopeSchema == null || envelopeSchema.getProperties() == null) {
            return operation;
        }

        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(requestObjectType.value()));
        if (resolved.schema == null) {
            return operation;
        }
        components.getSchemas().putIfAbsent(resolved.schema.getName(), resolved.schema);
        if (resolved.referencedSchemas != null) {
            resolved.referencedSchemas.forEach((name, schema) -> components.getSchemas().putIfAbsent(name, schema));
        }

        Schema<Object> variantSchema = new Schema<>();
        variantSchema.setType(envelopeSchema.getType());
        variantSchema.setDescription(envelopeSchema.getDescription());
        variantSchema.setRequired(envelopeSchema.getRequired());
        variantSchema.setProperties(new LinkedHashMap<>(envelopeSchema.getProperties()));
        variantSchema.getProperties().put("requestObject",
                new Schema<>().$ref("#/components/schemas/" + resolved.schema.getName()));

        String variantName = envelopeName + "_" + resolved.schema.getName();
        components.getSchemas().put(variantName, variantSchema);
        mediaType.setSchema(new Schema<>().$ref("#/components/schemas/" + variantName));

        return operation;
    }
}
