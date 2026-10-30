package com.example.studentmgmt.dto;

import java.util.List;

/** Numbers and chart data for the dashboard. */
public record StatsResponse(
        long totalStudents,
        long totalCourses,
        long totalEnrollments,
        double averageCreditsPerStudent,
        int seatUtilizationPercent,
        List<CourseResponse> popularCourses,
        List<StudentResponse> recentStudents,
        /* Every course with its seat numbers, most enrolled first (bar chart). */
        List<CourseResponse> enrollmentsPerCourse,
        /* New enrollments per month for the last six months, oldest first (column chart). */
        List<MonthlyCount> enrollmentsByMonth,
        /* How many students fall in each credit-load band (donut chart). */
        List<CreditBand> creditLoad
) {

    /** @param month ISO year-month, e.g. "2026-09" */
    public record MonthlyCount(String month, long enrollments) {
    }

    /** @param maxCredits null for the open-ended top band */
    public record CreditBand(String label, int minCredits, Integer maxCredits, long students) {
    }
}
