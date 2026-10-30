package com.example.studentmgmt.dto;

import java.time.Instant;

/** Course as shown in lists, with live seat numbers. */
public record CourseResponse(
        Long id,
        String code,
        String title,
        String description,
        int credits,
        int capacity,
        int enrolledCount,
        int availableSeats,
        Instant createdAt,
        Instant updatedAt
) {
}
