package com.heuristiq.identity.config;

import com.heuristiq.identity.common.RequestObjectType;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestObjectSchemaCustomizerTest {

    public record SampleDto(String name, int age) {
    }

    /** Handlers the customizer inspects through {@link HandlerMethod}. */
    public static class Handlers {
        @RequestObjectType(SampleDto.class)
        public void typed() {
        }

        public void untyped() {
        }
    }

    private final RequestObjectSchemaCustomizer customizer = new RequestObjectSchemaCustomizer();
    private Components components;
    private Operation operation;
    private MediaType json;

    private static HandlerMethod handler(String method) throws Exception {
        return new HandlerMethod(new Handlers(), Handlers.class.getMethod(method));
    }

    @BeforeEach
    void setUp() {
        ObjectSchema envelope = new ObjectSchema();
        envelope.setDescription("envelope");
        envelope.setRequired(List.of("requestObject"));
        envelope.addProperty("requestObject", new Schema<>());
        envelope.addProperty("messageID", new StringSchema());
        components = new Components();
        components.addSchemas("CommonRequestObject", envelope);

        json = new MediaType().schema(new Schema<>().$ref("#/components/schemas/CommonRequestObject"));
        operation = new Operation().requestBody(new RequestBody().content(new Content().addMediaType("application/json", json)));
    }

    @Test
    void twoArgumentOverloadReturnsTheOperationUnchanged() throws Exception {
        assertThat(customizer.customize(operation, handler("typed"))).isSameAs(operation);
        assertThat(json.getSchema().get$ref()).endsWith("CommonRequestObject");
    }

    @Test
    void typedHandlerGetsAPerOperationEnvelopePointingAtTheRealDto() throws Exception {
        Operation out = customizer.customize(operation, components, handler("typed"));

        assertThat(out).isSameAs(operation);
        assertThat(json.getSchema().get$ref()).isEqualTo("#/components/schemas/CommonRequestObject_SampleDto");
        Schema<?> variant = components.getSchemas().get("CommonRequestObject_SampleDto");
        assertThat(variant.getDescription()).isEqualTo("envelope");
        assertThat(variant.getRequired()).containsExactly("requestObject");
        assertThat(variant.getProperties()).containsKeys("requestObject", "messageID");
        assertThat(((Schema<?>) variant.getProperties().get("requestObject")).get$ref()).isEqualTo("#/components/schemas/SampleDto");
        assertThat(components.getSchemas()).containsKey("SampleDto");
        // the shared envelope is left alone
        assertThat(((Schema<?>) components.getSchemas().get("CommonRequestObject").getProperties().get("requestObject")).get$ref()).isNull();
    }

    @Test
    void handlerWithoutAnnotationIsLeftAlone() throws Exception {
        customizer.customize(operation, components, handler("untyped"));
        assertThat(json.getSchema().get$ref()).isEqualTo("#/components/schemas/CommonRequestObject");
        assertThat(components.getSchemas()).doesNotContainKey("SampleDto");
    }

    @Test
    void operationsWithoutAJsonBodyAreLeftAlone() throws Exception {
        Operation noBody = new Operation();
        assertThat(customizer.customize(noBody, components, handler("typed"))).isSameAs(noBody);

        Operation noContent = new Operation().requestBody(new RequestBody());
        assertThat(customizer.customize(noContent, components, handler("typed"))).isSameAs(noContent);

        Operation xmlOnly = new Operation().requestBody(new RequestBody().content(new Content().addMediaType("application/xml", new MediaType())));
        customizer.customize(xmlOnly, components, handler("typed"));
        assertThat(components.getSchemas()).doesNotContainKey("SampleDto");
    }

    @Test
    void jsonBodyWithoutAReferencedSchemaIsLeftAlone() throws Exception {
        json.setSchema(null);
        customizer.customize(operation, components, handler("typed"));
        assertThat(json.getSchema()).isNull();

        json.setSchema(new StringSchema());
        customizer.customize(operation, components, handler("typed"));
        assertThat(json.getSchema()).isInstanceOf(StringSchema.class);
    }

    @Test
    void unknownOrPropertylessEnvelopeIsLeftAlone() throws Exception {
        json.setSchema(new Schema<>().$ref("#/components/schemas/Missing"));
        customizer.customize(operation, components, handler("typed"));
        assertThat(json.getSchema().get$ref()).endsWith("Missing");

        components.addSchemas("Bare", new ObjectSchema());
        json.setSchema(new Schema<>().$ref("#/components/schemas/Bare"));
        customizer.customize(operation, components, handler("typed"));
        assertThat(json.getSchema().get$ref()).endsWith("Bare");
    }
}
