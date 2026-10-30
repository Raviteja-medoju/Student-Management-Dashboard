package com.example.studentmgmt.dto;

import java.time.LocalDate;

/** A student as seen from a course's roster. */
public record EnrolledStudentResponse(
        Long studentId,
        String fullName,
        String email,
        LocalDate enrolledOn
) {
}
