package com.heuristiq.uam.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapperUtilsTest {

    public static class Sample {
        @JsonProperty("renamed")
        public String name = "n";
        @JsonIgnore
        public String secret = "s";
        public String empty = null;
        public LocalDate day = LocalDate.of(2025, 1, 2);
    }

    /** A bean the mapper cannot serialise (self reference), to exercise the null-on-failure paths. */
    public static class Cyclic {
        public Cyclic self = this;
    }

    @Test
    void utilityClassCannotBeInstantiated() throws Exception {
        Constructor<MapperUtils> constructor = MapperUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void annotationAwareJsonHonoursIgnoreAndRename() {
        String json = MapperUtils.toJson(new Sample());
        assertThat(json).contains("\"renamed\":\"n\"").doesNotContain("secret").contains("\"empty\":null").contains("2025-01-02");
        assertThat(MapperUtils.toJsonIgnoreNull(new Sample())).doesNotContain("empty");
    }

    @Test
    void annotationIgnoringJsonExposesEveryFieldUnderItsJavaName() {
        String json = MapperUtils.toJsonIgnoreAnnotations(new Sample());
        assertThat(json).contains("\"name\":\"n\"").contains("\"secret\":\"s\"").contains("\"empty\":null");
        assertThat(MapperUtils.toJsonIgnoreAnnotationsAndNull(new Sample())).contains("secret").doesNotContain("empty");
    }

    @Test
    void serialisationFailureYieldsNull() {
        assertThat(MapperUtils.toJson(new Cyclic())).isNull();
        assertThat(MapperUtils.toJsonIgnoreNull(new Cyclic())).isNull();
        assertThat(MapperUtils.toJsonIgnoreAnnotations(new Cyclic())).isNull();
        assertThat(MapperUtils.toJsonIgnoreAnnotationsAndNull(new Cyclic())).isNull();
    }

    @Test
    void convertsToMapAndTypes() {
        LinkedHashMap<String, Object> map = MapperUtils.getLinkedHashMap(new Sample());
        assertThat(map).containsEntry("name", "n").containsEntry("secret", "s");

        Sample back = MapperUtils.convertValue(Map.of("name", "x", "unknown", 1), Sample.class);
        assertThat(back.name).isEqualTo("x");

        List<Integer> numbers = MapperUtils.convertValue(List.of("1", "2"), new TypeReference<List<Integer>>() { });
        assertThat(numbers).containsExactly(1, 2);

        Sample annotated = MapperUtils.convertValueWithAnnotations(Map.of("renamed", "r"), Sample.class);
        assertThat(annotated.name).isEqualTo("r");
    }

    @Test
    void readsJsonFromStringsAndBytes() throws Exception {
        assertThat(MapperUtils.readValue("{\"name\":\"a\"}", Sample.class).name).isEqualTo("a");
        assertThat(MapperUtils.readValue("[1,2]", new TypeReference<List<Integer>>() { })).containsExactly(1, 2);
        byte[] bytes = "{\"name\":\"b\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(MapperUtils.readValue(bytes, Sample.class).name).isEqualTo("b");
        assertThat(MapperUtils.readValue("[3]".getBytes(StandardCharsets.UTF_8), new TypeReference<List<Integer>>() { }))
                .containsExactly(3);
        assertThat(MapperUtils.readValueWithAnnotations("{\"renamed\":\"c\"}", Sample.class).name).isEqualTo("c");
        assertThatThrownBy(() -> MapperUtils.readValue("{not json", Sample.class)).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void mappersAreSharedSingletons() {
        assertThat(MapperUtils.buildCoreObjectMapper()).isSameAs(MapperUtils.buildCoreObjectMapper());
        assertThat(MapperUtils.buildMapperForIgnoreAnnotation()).isSameAs(MapperUtils.buildMapperForIgnoreAnnotation());
    }
}
