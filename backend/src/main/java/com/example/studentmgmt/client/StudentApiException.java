package com.example.studentmgmt.client;

import com.example.studentmgmt.exception.ApiError;
import org.springframework.http.HttpStatusCode;

/** An error response from the Student API, with the parsed {@link ApiError} body when available. */
public class StudentApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final transient ApiError apiError;

    public StudentApiException(HttpStatusCode status, ApiError apiError, Throwable cause) {
        super(apiError != null ? apiError.message() : "Student API call failed with status " + status, cause);
        this.status = status;
        this.apiError = apiError;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public ApiError getApiError() {
        return apiError;
    }
}
