package com.heuristiq.uam.testsupport;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Page;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives a persistence adapter (Mongo or JPA) through every public port method against mocked Spring Data
 * repositories / templates that return fully populated entities or documents, then checks that the adapter
 * actually delegated and that every field of the returned domain objects matches the stored shape.
 * This verifies the entity/document to domain mapping (and the reverse for {@code save}) without hand-written setters.
 */
public final class AdapterMappingVerifier {

    private AdapterMappingVerifier() {
    }

    /** Result of exercising one adapter. */
    public record Outcome(int methodsInvoked, int mappedResults) {
    }

    /**
     * Builds the adapter with populating mocks and exercises each public method declared on it.
     *
     * @param adapterType     the (package-private) adapter class
     * @param ignoredFields   domain field names that the adapter legitimately transforms (not compared)
     */
    public static Outcome verify(Class<?> adapterType, Set<String> ignoredFields) {
        List<Object> produced = new ArrayList<>();
        Answer<Object> answer = populating(produced);
        Constructor<?> ctor = adapterType.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object[] deps = new Object[ctor.getParameterCount()];
        for (int i = 0; i < deps.length; i++) {
            deps[i] = dependency(ctor.getParameterTypes()[i], answer);
        }
        Object adapter;
        try {
            adapter = ctor.newInstance(deps);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build " + adapterType, e);
        }
        int invoked = 0;
        int mapped = 0;
        for (Method method : adapterType.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()) {
                continue;
            }
            produced.clear();
            Object[] args = new Object[method.getParameterCount()];
            for (int i = 0; i < args.length; i++) {
                args[i] = BeanPopulator.value(ResolvableType.forType(method.getGenericParameterTypes()[i]), new Object[0]);
            }
            Object result;
            try {
                method.setAccessible(true);
                result = method.invoke(adapter, args);
            } catch (InvocationTargetException e) {
                throw new AssertionError("Adapter method " + adapterType.getSimpleName() + "." + method.getName()
                        + " threw " + e.getCause(), e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            invoked++;
            for (Object domain : flatten(result)) {
                if (domain == null || isSimple(domain)) {
                    continue;
                }
                assertThat(produced).as("%s.%s returned a domain object that no store produced", adapterType.getSimpleName(), method.getName())
                        .isNotEmpty();
                assertMatchesSomeStored(adapterType, method, domain, produced, ignoredFields);
                mapped++;
            }
            if ("save".equals(method.getName()) && result != null && args.length == 1 && !isSimple(result)) {
                assertThat(result).as("save round trip of %s", adapterType.getSimpleName())
                        .usingRecursiveComparison().ignoringFields(ignoredFields.toArray(String[]::new))
                        .isEqualTo(args[0]);
            }
        }
        long interactions = java.util.Arrays.stream(deps).filter(d -> Mockito.mockingDetails(d).isMock())
                .mapToLong(d -> Mockito.mockingDetails(d).getInvocations().size()).sum();
        assertThat(interactions).as("%s must delegate to its store", adapterType.getSimpleName()).isGreaterThanOrEqualTo(invoked == 0 ? 0 : 1);
        assertThat(invoked).as("%s public methods exercised", adapterType.getSimpleName()).isPositive();
        return new Outcome(invoked, mapped);
    }

    private static Object dependency(Class<?> type, Answer<Object> answer) {
        if (type == Clock.class) {
            return Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
        }
        if (type == com.fasterxml.jackson.databind.ObjectMapper.class) {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
        return Mockito.mock(type, answer);
    }

    private static Answer<Object> populating(List<Object> produced) {
        return (InvocationOnMock inv) -> {
            Method m = inv.getMethod();
            if (m.getReturnType() == void.class) {
                return null;
            }
            if (m.getName().startsWith("save") && inv.getArguments().length >= 1) {
                Object arg = inv.getArgument(0);
                produced.addAll(flatten(arg));
                return arg;
            }
            if (m.getDeclaringClass() == Object.class) {
                return inv.callRealMethod();
            }
            ResolvableType rt = ResolvableType.forMethodReturnType(m, inv.getMock().getClass());
            Class<?> raw = rt.resolve();
            if (raw != null && raw.isPrimitive()) {
                return Mockito.RETURNS_DEFAULTS.answer(inv);
            }
            Object v = BeanPopulator.value(rt, inv.getArguments());
            produced.addAll(flatten(v));
            return v;
        };
    }

    private static boolean isSimple(Object o) {
        return o instanceof CharSequence || o instanceof Number || o instanceof Boolean || o instanceof Enum<?>
                || o.getClass().getName().startsWith("java.") || o.getClass().getName().startsWith("org.mockito")
                || o.getClass().getName().contains("$MockitoMock$");
    }

    private static List<Object> flatten(Object o) {
        List<Object> out = new ArrayList<>();
        if (o == null) {
            return out;
        }
        if (o instanceof Optional<?> opt) {
            opt.ifPresent(v -> out.addAll(flatten(v)));
        } else if (o instanceof Page<?> page) {
            page.getContent().forEach(v -> out.addAll(flatten(v)));
        } else if (o instanceof Collection<?> c) {
            c.forEach(v -> out.addAll(flatten(v)));
        } else if (o instanceof Map<?, ?> map) {
            // maps (e.g. count-by-key) are not domain beans
            return out;
        } else if (o instanceof java.util.stream.Stream<?> s) {
            s.forEach(v -> out.addAll(flatten(v)));
        } else if (!o.getClass().isArray()) {
            out.add(o);
        }
        return out;
    }

    private static void assertMatchesSomeStored(Class<?> adapterType, Method method, Object domain, List<Object> stored, Set<String> ignored) {
        List<String> failures = new ArrayList<>();
        for (Object candidate : stored) {
            if (candidate == null || isSimple(candidate)) {
                continue;
            }
            List<String> mismatches = mismatches(domain, candidate, ignored);
            if (mismatches != null && mismatches.isEmpty()) {
                return;
            }
            if (mismatches != null) {
                failures.addAll(mismatches);
            }
        }
        throw new AssertionError(adapterType.getSimpleName() + "." + method.getName() + " lost or altered fields when mapping "
                + domain.getClass().getSimpleName() + ": " + failures);
    }

    /** Returns the shared-field mismatches, or null when the two share no field at all. */
    private static List<String> mismatches(Object domain, Object stored, Set<String> ignored) {
        Map<String, Field> storedFields = fields(stored.getClass());
        List<String> bad = new ArrayList<>();
        int shared = 0;
        for (Map.Entry<String, Field> e : fields(domain.getClass()).entrySet()) {
            Field other = storedFields.get(e.getKey());
            if (other == null || ignored.contains(e.getKey())) {
                continue;
            }
            shared++;
            try {
                Object a = e.getValue().get(domain);
                Object b = other.get(stored);
                if (!Objects.equals(a, b)) {
                    bad.add(e.getKey() + ": " + a + " != " + b);
                }
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return shared == 0 ? null : bad;
    }

    private static Map<String, Field> fields(Class<?> type) {
        Map<String, Field> out = new java.util.LinkedHashMap<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                    continue;
                }
                f.setAccessible(true);
                out.putIfAbsent(f.getName(), f);
            }
        }
        return out;
    }
}
