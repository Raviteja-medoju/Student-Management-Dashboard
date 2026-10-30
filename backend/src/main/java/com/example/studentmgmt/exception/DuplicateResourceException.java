package com.example.studentmgmt.exception;

/**
 * Thrown when a create/update would break a uniqueness rule (e.g. email, course code).
 * Mapped to HTTP 409; {@link #getField()} lets the UI highlight the offending form field.
 */
public class DuplicateResourceException extends RuntimeException {

    private final String field;

    public DuplicateResourceException(String message, String field) {
        super(message);
        this.field = field;
    }

    public DuplicateResourceException(String message) {
        this(message, null);
    }

    public String getField() {
        return field;
    }
}
