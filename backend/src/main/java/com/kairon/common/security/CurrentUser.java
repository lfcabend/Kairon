package com.kairon.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated {@link UserId} into a controller handler method:
 *
 * <pre>{@code
 * @GetMapping("/me")
 * MeResponse me(@CurrentUser UserId userId) { ... }
 * }</pre>
 *
 * Resolved by {@link CurrentUserArgumentResolver} from the validated access token.
 * A handler that declares this parameter always runs authenticated — an anonymous
 * request never reaches it because Spring Security rejects it first.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
