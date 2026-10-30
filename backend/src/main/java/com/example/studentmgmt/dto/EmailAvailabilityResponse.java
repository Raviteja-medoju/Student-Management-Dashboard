package com.example.studentmgmt.dto;

/** Answer for the live "is this email free?" check in the student form. */
public record EmailAvailabilityResponse(String email, boolean available) {
}
