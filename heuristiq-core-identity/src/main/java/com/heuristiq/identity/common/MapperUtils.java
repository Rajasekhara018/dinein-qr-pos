package com.heuristiq.uam.common;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * Utility class for Jackson ObjectMapper operations.
 * Uses thread-safe singleton ObjectMapper instances for optimal performance.
 * ObjectMapper instances are immutable once configured and safe for concurrent use.
 */
public class MapperUtils {

	// Thread-safe singleton instances for better performance
	private static final ObjectMapper MAPPER_IGNORE_ANNOTATION = createMapperForIgnoreAnnotation();
	private static final ObjectMapper CORE_MAPPER = createCoreObjectMapper();
	private static final ObjectMapper CORE_MAPPER_NON_NULL = createCoreObjectMapperNonNull();
	private static final ObjectMapper MAPPER_IGNORE_ANNOTATION_NON_NULL = createMapperForIgnoreAnnotationNonNull();


	// Reusable TypeReference for LinkedHashMap conversion
	private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE_REFERENCE =
			new TypeReference<LinkedHashMap<String, Object>>() {};

	private MapperUtils() {
		throw new IllegalStateException("Utility class");
	}

	private static ObjectMapper createMapperForIgnoreAnnotation() {
	ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new ParameterNamesModule())
			.registerModule(new Jdk8Module())
			.registerModule(new JavaTimeModule());
	objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
	objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
	objectMapper.disable(MapperFeature.USE_ANNOTATIONS);
	return objectMapper;
}

	private static ObjectMapper createCoreObjectMapper() {
		ObjectMapper objectMapper = new ObjectMapper()
				.registerModule(new ParameterNamesModule())
				.registerModule(new Jdk8Module())
				.registerModule(new JavaTimeModule());
		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return objectMapper;
	}

	/**
	 * Returns the singleton ObjectMapper instance configured to ignore annotations.
	 * This mapper is thread-safe and can be reused across the application.
	 *
	 * @return ObjectMapper with annotations disabled
	 */
	public static ObjectMapper buildMapperForIgnoreAnnotation() {
		return MAPPER_IGNORE_ANNOTATION;
	}

	/**
	 * Converts an object to LinkedHashMap using the annotation-ignoring mapper.
	 *
	 * @param req the object to convert
	 * @return LinkedHashMap representation of the object
	 */
	public static LinkedHashMap<String, Object> getLinkedHashMap(Object req) {
		return MAPPER_IGNORE_ANNOTATION.convertValue(req, MAP_TYPE_REFERENCE);
	}

	/**
	 * Returns the singleton core ObjectMapper instance.
	 * This mapper is thread-safe and can be reused across the application.
	 *
	 * @return ObjectMapper with core configuration
	 */
	public static ObjectMapper buildCoreObjectMapper() {
		return CORE_MAPPER;
	}

	/**
	 * Converts an object to the specified class type using the annotation-ignoring mapper.
	 * This is a convenience method that uses the singleton ObjectMapper for better performance.
	 *
	 * @param <T> the target type
	 * @param source the source object to convert
	 * @param targetClass the target class type
	 * @return converted object of type T
	 */
	public static <T> T convertValue(Object source, Class<T> targetClass) {
		return MAPPER_IGNORE_ANNOTATION.convertValue(source, targetClass);
	}

	/**
	 * Converts an object to the specified type using TypeReference with the annotation-ignoring mapper.
	 * This is useful for complex generic types.
	 *
	 * @param <T> the target type
	 * @param source the source object to convert
	 * @param typeReference the TypeReference for the target type
	 * @return converted object of type T
	 */
	public static <T> T convertValue(Object source, TypeReference<T> typeReference) {
		return MAPPER_IGNORE_ANNOTATION.convertValue(source, typeReference);
	}

	/**
	 * Converts an object to the specified class type using the core mapper.
	 * This is a convenience method for conversions that need annotation processing.
	 *
	 * @param <T> the target type
	 * @param source the source object to convert
	 * @param targetClass the target class type
	 * @return converted object of type T
	 */
	public static <T> T convertValueWithAnnotations(Object source, Class<T> targetClass) {
		return CORE_MAPPER.convertValue(source, targetClass);
	}
	
	/**
	 * Reads a JSON string and converts it to the specified class type using the
	 * annotation-ignoring mapper.
	 *
	 * @param <T>         the target type
	 * @param json        the JSON string
	 * @param targetClass the target class
	 * @return deserialized object of type T
	 * @throws JsonProcessingException if JSON parsing fails
	 */
	public static <T> T readValue(String json, Class<T> targetClass) throws JsonProcessingException {
		return MAPPER_IGNORE_ANNOTATION.readValue(json, targetClass);
	}
 
	/**
	 * Reads a JSON string and converts it to the specified generic type using
	 * TypeReference and the annotation-ignoring mapper.
	 *
	 * @param <T>           the target type
	 * @param json          the JSON string
	 * @param typeReference the TypeReference of the target type
	 * @return deserialized object of type T
	 * @throws JsonProcessingException if JSON parsing fails
	 */
	public static <T> T readValue(String json, TypeReference<T> typeReference) throws JsonProcessingException {
		return MAPPER_IGNORE_ANNOTATION.readValue(json, typeReference);
	}
 
	/**
	 * Reads JSON bytes and converts them to the specified class type using the
	 * annotation-ignoring mapper.
	 *
	 * @param <T>         the target type
	 * @param bytes       the JSON bytes
	 * @param targetClass the target class
	 * @return deserialized object of type T
	 * @throws IOException if deserialization fails
	 */
	public static <T> T readValue(byte[] bytes, Class<T> targetClass) throws IOException {
		return MAPPER_IGNORE_ANNOTATION.readValue(bytes, targetClass);
	}
 
	/**
	 * Reads JSON bytes and converts them to the specified generic type using
	 * TypeReference and the annotation-ignoring mapper.
	 *
	 * @param <T>           the target type
	 * @param bytes         the JSON bytes
	 * @param typeReference the TypeReference of the target type
	 * @return deserialized object of type T
	 * @throws IOException if deserialization fails
	 */
	public static <T> T readValue(byte[] bytes, TypeReference<T> typeReference) throws IOException {
		return MAPPER_IGNORE_ANNOTATION.readValue(bytes, typeReference);
	}
 
	// Optional: if you want readValue with annotations
	public static <T> T readValueWithAnnotations(String json, Class<T> targetClass) throws JsonProcessingException {
		return CORE_MAPPER.readValue(json, targetClass);
	}
 
	// ------------------------------------------------------------------
	// Factory methods - called only once at class load
	// ------------------------------------------------------------------
 
	private static ObjectMapper createCoreObjectMapperNonNull() {
		ObjectMapper mapper = createCoreObjectMapper();
		mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		return mapper;
	}
 
	private static ObjectMapper createMapperForIgnoreAnnotationNonNull() {
		ObjectMapper mapper = createMapperForIgnoreAnnotation();
		mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		return mapper;
	}
 
	public static String toJson(Object obj) {
		try {
			return Optional.of(CORE_MAPPER.writeValueAsString(obj)).get();
		} catch (JsonProcessingException e) {
			return null;
		}
	}
 
	public static String toJsonIgnoreNull(Object obj) {
		try {
			return Optional.of(CORE_MAPPER_NON_NULL.writeValueAsString(obj)).get();
		} catch (JsonProcessingException e) {
			return null;
		}
	}
 
	public static String toJsonIgnoreAnnotations(Object obj) {
		try {
			return Optional.of(MAPPER_IGNORE_ANNOTATION.writeValueAsString(obj)).get();
		} catch (JsonProcessingException e) {
			return null;
		}
	}
 
	public static String toJsonIgnoreAnnotationsAndNull(Object obj) {
		try {
			return Optional.of(MAPPER_IGNORE_ANNOTATION_NON_NULL.writeValueAsString(obj)).get();
		} catch (JsonProcessingException e) {
			return null;
		}
	}
	

}
