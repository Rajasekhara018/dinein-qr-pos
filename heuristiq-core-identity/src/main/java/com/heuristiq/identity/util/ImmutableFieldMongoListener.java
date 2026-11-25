package com.heuristiq.identity.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class ImmutableFieldMongoListener extends AbstractMongoEventListener<Object> {

	@Autowired
	private MongoTemplate mongoTemplate;

	@Override
	public void onBeforeConvert(BeforeConvertEvent<Object> event) {
		Object incoming = event.getSource();
		if (incoming == null) {
			return;
		}
		// Dynamically resolve @Id field instead of assuming "id"
		Object id = extractIdValue(incoming);
		// If ID is null → this is CREATE operation
		if (id == null) {
			return;
		}
		// Fetch existing document from Mongo
		Object existing = mongoTemplate.findById(id, incoming.getClass());
		if (existing == null) {
			return;
		}
		// Enforce immutability
		enforceImmutableFields(incoming, existing);
	}

	/**
	 * Extract value of field annotated with @Id
	 */
	private Object extractIdValue(Object source) {
		Field idField = findFieldWithAnnotation(source.getClass(), Id.class);
		if (idField == null) {
			return null;
		}
		try {
			idField.setAccessible(true);
			return idField.get(source);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Unable to read @Id field", e);
		}
	}

	/**
	 * Core logic to restore immutable fields if modified
	 */
	private void enforceImmutableFields(Object incoming, Object existing) {
		List<Field> allFields = getAllFields(incoming.getClass());
		for (Field field : allFields) {
			if (!field.isAnnotationPresent(ImmutableAfterCreate.class)) {
				continue;
			}
			try {
				field.setAccessible(true);
				Object oldValue = field.get(existing);
				Object newValue = field.get(incoming);
				if (!Objects.equals(oldValue, newValue)) {
				    throw new IllegalStateException(
				        field.getName() + " cannot be updated once created"
				    );
				}
			} catch (IllegalAccessException e) {
				throw new RuntimeException("Failed enforcing immutable field: " + field.getName(), e);
			}
		}
	}

	/**
	 * Get fields including superclass fields (important for BaseDocumentEntity)
	 */
	private List<Field> getAllFields(Class<?> type) {
		List<Field> fields = new ArrayList<>();
		while (type != null && type != Object.class) {
			for (Field field : type.getDeclaredFields()) {
				fields.add(field);
			}
			type = type.getSuperclass();
		}
		return fields;
	}

	/**
	 * Find a field annotated with a specific annotation
	 */
	private Field findFieldWithAnnotation(Class<?> type, Class<?> annotation) {
		while (type != null && type != Object.class) {
			for (Field field : type.getDeclaredFields()) {
				if (field.isAnnotationPresent((Class) annotation)) {
					return field;
				}
			}
			type = type.getSuperclass();
		}
		return null;
	}
}

