package com.example.studentmgmt.service;

import com.example.studentmgmt.dto.StudentDetailResponse;

import java.util.List;

public interface EnrollmentService {

    /**
     * Makes the student's enrollments exactly match {@code courseIds}. All-or-nothing:
     * if any course is missing or full, nothing changes.
     */
    StudentDetailResponse replaceEnrollments(Long studentId, List<Long> courseIds);

    StudentDetailResponse enroll(Long studentId, Long courseId);

    void unenroll(Long studentId, Long courseId);
}
