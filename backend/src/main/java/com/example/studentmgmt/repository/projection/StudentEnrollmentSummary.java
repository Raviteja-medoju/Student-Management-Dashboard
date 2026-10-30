package com.example.studentmgmt.repository.projection;

/** Aggregated enrollment numbers for one student (used by the paged student list). */
public record StudentEnrollmentSummary(Long studentId, Long courseCount, Long totalCredits) {
}
