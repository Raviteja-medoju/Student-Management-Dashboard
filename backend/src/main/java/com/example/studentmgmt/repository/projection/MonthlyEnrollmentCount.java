package com.example.studentmgmt.repository.projection;

/** Number of enrollments created in one calendar month. */
public record MonthlyEnrollmentCount(Integer year, Integer month, Long count) {
}
