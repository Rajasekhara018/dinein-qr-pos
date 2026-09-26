package com.heuristq.dinein.guest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Injects the caller's {@link GuestSession}; responds 401 {@code GUEST_SESSION_REQUIRED} when there is none. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentGuest {
}
