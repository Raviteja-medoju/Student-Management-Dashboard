package com.example.studentmgmt.controller;

import com.example.studentmgmt.dto.EnrollmentRequest;
import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/{studentId}/courses")
@Tag(name = "Enrollments", description = "Manage which courses a student is enrolled in")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PutMapping
    @Operation(summary = "Replace a student's course set",
            description = "All-or-nothing: if any course is missing (404) or full (409), no enrollment changes")
    public StudentDetailResponse replace(@PathVariable Long studentId, @Valid @RequestBody EnrollmentRequest request) {
        return enrollmentService.replaceEnrollments(studentId, request.courseIds());
    }

    @PostMapping("/{courseId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enroll a student in one course")
    public StudentDetailResponse enroll(@PathVariable Long studentId, @PathVariable Long courseId) {
        return enrollmentService.enroll(studentId, courseId);
    }

    @DeleteMapping("/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a student from one course")
    public void unenroll(@PathVariable Long studentId, @PathVariable Long courseId) {
        enrollmentService.unenroll(studentId, courseId);
    }
}
