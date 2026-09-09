package com.kairon.identity.domain;

/**
 * Account lifecycle. M1 only ever creates {@link #ACTIVE} accounts; {@link #PENDING}
 * exists for the later optional email-verification flow and {@link #DISABLED} for
 * operator lockout. Stored as {@code varchar} with a check constraint.
 */
public enum UserStatus {
    ACTIVE,
    PENDING,
    DISABLED
}
