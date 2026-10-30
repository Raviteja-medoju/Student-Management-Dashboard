package com.example.studentmgmt.dto;

import java.time.Instant;
import java.util.List;

/** A single course with the students enrolled in it. */
public record CourseDetailResponse(
        Long id,
        String code,
        String title,
        String description,
        int credits,
        int capacity,
        int enrolledCount,
        int availableSeats,
        Instant createdAt,
        Instant updatedAt,
        List<EnrolledStudentResponse> students
) {
}
