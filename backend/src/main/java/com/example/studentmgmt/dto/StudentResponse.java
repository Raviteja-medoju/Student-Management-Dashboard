package com.example.studentmgmt.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Student as shown in lists: profile fields plus enrollment totals. */
public record StudentResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        LocalDate dateOfBirth,
        int enrolledCourseCount,
        int totalCredits,
        Instant createdAt,
        Instant updatedAt
) {
}
