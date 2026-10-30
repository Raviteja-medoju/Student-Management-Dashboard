package com.example.studentmgmt.repository.projection;

/** Number of enrolled students for one course. */
public record CourseEnrollmentCount(Long courseId, Long enrolledCount) {
}
