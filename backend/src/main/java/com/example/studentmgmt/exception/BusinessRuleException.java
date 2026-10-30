package com.example.studentmgmt.exception;

/** Thrown when a request is well-formed but breaks a domain rule (e.g. course is full). Mapped to HTTP 409. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
