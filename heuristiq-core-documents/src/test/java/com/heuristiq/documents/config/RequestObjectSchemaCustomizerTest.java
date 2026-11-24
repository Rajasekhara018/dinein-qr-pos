package com.railcab.dms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestObjectSchemaCustomizerTest {

    private static final String PATH = "/documents/metadata/search";
    private final RequestObjectSchemaCustomizer customizer = new RequestObjectSchemaCustomizer();

    private static OpenAPI api(Operation post, Components components) {
        PathItem item = new PathItem();
        item.setPost(post);
        return new OpenAPI().paths(new Paths().addPathItem(PATH, item)).components(components);
    }

    private static Operation operationWithRef(String ref) {
        MediaType media = new MediaType().schema(new Schema<>().$ref(ref));
        return new Operation().requestBody(new RequestBody().content(new Content().addMediaType("application/json", media)));
    }

    @Test
    void ignoresSpecsWithoutTheSearchOperation() {
        customizer.customise(new OpenAPI());
        customizer.customise(new OpenAPI().paths(new Paths()));
        PathItem noPost = new PathItem();
        customizer.customise(new OpenAPI().paths(new Paths().addPathItem(PATH, noPost)));
    }

    @Test
    void leavesOperationAloneWhenPrerequisitesAreMissing() {
        // no components
        Operation op = operationWithRef("#/components/schemas/Env");
        customizer.customise(api(op, null));
        assertEquals("#/components/schemas/Env", op.getRequestBody().getContent().get("application/json").getSchema().get$ref());

        // no request body
        customizer.customise(api(new Operation(), new Components()));

        // no json media type
        Operation other = new Operation().requestBody(new RequestBody().content(new Content()));
        customizer.customise(api(other, new Components()));

        // schema without $ref
        Operation inline = new Operation().requestBody(new RequestBody().content(new Content()
                .addMediaType("application/json", new MediaType().schema(new ObjectSchema()))));
        customizer.customise(api(inline, new Components()));
        assertEquals(null, inline.getRequestBody().getContent().get("application/json").getSchema().get$ref());

        // envelope schema unknown or without properties
        Operation unknown = operationWithRef("#/components/schemas/Env");
        customizer.customise(api(unknown, new Components()));
        assertEquals("#/components/schemas/Env", unknown.getRequestBody().getContent().get("application/json").getSchema().get$ref());
        Components noProps = new Components().addSchemas("Env", new ObjectSchema());
        Operation op2 = operationWithRef("#/components/schemas/Env");
        customizer.customise(api(op2, noProps));
        assertEquals("#/components/schemas/Env", op2.getRequestBody().getContent().get("application/json").getSchema().get$ref());
    }

    @Test
    void replacesRequestObjectWithTypedVariant() {
        Schema<?> envelope = new ObjectSchema().addProperty("requestObject", new Schema<>().type("object"))
                .addProperty("page", new Schema<>().type("integer"));
        envelope.setDescription("env");
        Components components = new Components().addSchemas("CommonRequestObject", envelope);
        Operation op = operationWithRef("#/components/schemas/CommonRequestObject");

        customizer.customise(api(op, components));

        String ref = op.getRequestBody().getContent().get("application/json").getSchema().get$ref();
        assertEquals("#/components/schemas/CommonRequestObject_DocumentUploadRequest", ref);
        Schema<?> variant = components.getSchemas().get("CommonRequestObject_DocumentUploadRequest");
        assertNotNull(variant);
        assertEquals("env", variant.getDescription());
        assertEquals("#/components/schemas/DocumentUploadRequest", variant.getProperties().get("requestObject").get$ref());
        assertTrue(variant.getProperties().containsKey("page"));
        assertTrue(components.getSchemas().containsKey("DocumentUploadRequest"));
    }
}
