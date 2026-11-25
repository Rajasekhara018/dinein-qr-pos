package com.heuristiq.identity.cache;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * Wraps a repository port so that every write method ({@link UamCacheConstants#WRITE_METHODS}) evicts the dependent cache
 * entries after it returns (also when it throws, since a failed write may still have committed). Because every adapter of the
 * port is wrapped, a write made by any service, bootstrap, import or org-structure flow is covered. Reads pass through untouched.
 */
final class WriteEvictingProxy {

    /** Evicts for one write; {@code result} is null for void methods. */
    @FunctionalInterface
    interface Eviction {
        void apply(Object[] args, Object result);
    }

    @SuppressWarnings("unchecked")
    static <T> T wrap(Class<T> port, T target, Eviction eviction) {
        InvocationHandler handler = (proxy, method, args) -> {
            Object[] safeArgs = args == null ? new Object[0] : args;
            boolean write = UamCacheConstants.WRITE_METHODS.contains(method.getName());
            Object result = null;
            try {
                result = method.invoke(target, safeArgs);
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            } finally {
                if (write) {
                    Object written = result;
                    CommitAwareEviction.run(() -> eviction.apply(safeArgs, written));
                }
            }
        };
        return (T) Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[]{port}, handler);
    }

    private WriteEvictingProxy() {
    }
}
