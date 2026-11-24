package com.railcab.dms.config;

import java.util.LinkedHashMap;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

import com.railcab.dms.document.dto.DocumentUploadRequest;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

/**
 * DMS's only endpoint that hides a typed payload behind the generic {@code CommonRequestObject}
 * envelope is {@code POST /documents/metadata/search} (requestObject -> DocumentUploadRequest).
 * Because that field's static type is {@code Object}, springdoc has no way to document its real
 * shape by default -- Swagger UI just renders it as an opaque placeholder.
 *
 * <p>This service is pinned to springdoc 2.6.0 (Boot 3.3.5 -- see build.gradle), which doesn't
 * have the {@code GlobalOperationComponentsCustomizer} hook that UAM/NMS/PaymentAPI use (their
 * per-operation, annotation-driven variant of this fix): here we go through {@link GlobalOpenApiCustomizer}
 * instead, which only gets the fully-built {@link OpenAPI} tree, not a per-handler-method callback.
 * With a single affected endpoint, targeting it directly by path/method is simpler and safer than
 * reimplementing handler-method-to-operation matching against an OpenAPI version this service
 * deliberately isn't on. If DMS grows more such endpoints, revisit with the annotation-based
 * approach used elsewhere.
 */
@Component
public class RequestObjectSchemaCustomizer implements GlobalOpenApiCustomizer {

    private static final String SEARCH_PATH = "/documents/metadata/search";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        PathItem pathItem = openApi.getPaths().get(SEARCH_PATH);
        if (pathItem == null || pathItem.getPost() == null) {
            return;
        }
        overrideRequestObjectSchema(pathItem.getPost(), openApi.getComponents(), DocumentUploadRequest.class);
    }

    private void overrideRequestObjectSchema(Operation operation, Components components, Class<?> dtoType) {
        if (components == null || operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return;
        }
        MediaType mediaType = operation.getRequestBody().getContent().get(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
        if (mediaType == null || mediaType.getSchema() == null || mediaType.getSchema().get$ref() == null) {
            return;
        }

        String envelopeRef = mediaType.getSchema().get$ref();
        String envelopeName = envelopeRef.substring(envelopeRef.lastIndexOf('/') + 1);
        Schema<?> envelopeSchema = components.getSchemas() == null ? null : components.getSchemas().get(envelopeName);
        if (envelopeSchema == null || envelopeSchema.getProperties() == null) {
            return;
        }

        ResolvedSchema resolved = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(dtoType));
        if (resolved.schema == null) {
            return;
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
    }
}
