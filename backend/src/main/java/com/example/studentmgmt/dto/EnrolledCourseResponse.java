package com.example.studentmgmt.dto;

import java.time.LocalDate;

/** A course as seen from a student's enrollment list. */
public record EnrolledCourseResponse(
        Long courseId,
        String code,
        String title,
        int credits,
        LocalDate enrolledOn
) {
}
