package com.example.studentmgmt.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every endpoint, so the frontend can handle all errors the same way.
 *
 * @param fieldErrors per-field problems (validation failures, duplicate values); omitted when empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldError(String field, String message, Object rejectedValue) {
    }
}
