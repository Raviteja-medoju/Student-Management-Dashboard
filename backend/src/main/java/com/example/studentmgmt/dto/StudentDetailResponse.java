package com.example.studentmgmt.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A single student with the courses they are enrolled in. */
public record StudentDetailResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        LocalDate dateOfBirth,
        int enrolledCourseCount,
        int totalCredits,
        Instant createdAt,
        Instant updatedAt,
        List<EnrolledCourseResponse> courses
) {
}
