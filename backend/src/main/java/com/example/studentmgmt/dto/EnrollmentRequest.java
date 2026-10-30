package com.example.studentmgmt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The complete set of courses a student should be enrolled in after the call. */
public record EnrollmentRequest(
        @Schema(example = "[1, 3, 5]")
        @NotNull(message = "courseIds is required")
        @Size(max = 20, message = "A student can be enrolled in at most 20 courses")
        List<@NotNull(message = "Course id must not be null") Long> courseIds
) {
}
