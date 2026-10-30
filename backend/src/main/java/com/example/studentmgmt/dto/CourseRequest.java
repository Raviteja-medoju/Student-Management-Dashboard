package com.example.studentmgmt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Payload for creating or updating a course. The Angular form mirrors these rules. */
public record CourseRequest(
        @Schema(example = "CS101", description = "2-4 letters followed by 3 digits")
        @NotBlank(message = "Course code is required")
        @Pattern(regexp = "^[A-Za-z]{2,4}\\d{3}$", message = "Course code must be 2-4 letters followed by 3 digits, e.g. CS101")
        String code,

        @Schema(example = "Introduction to Programming")
        @NotBlank(message = "Title is required")
        @Size(max = 100, message = "Title must be at most 100 characters")
        String title,

        @Schema(example = "Variables, control flow, functions and basic data structures.")
        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @Schema(example = "4")
        @NotNull(message = "Credits are required")
        @Min(value = 1, message = "Credits must be between 1 and 6")
        @Max(value = 6, message = "Credits must be between 1 and 6")
        Integer credits,

        @Schema(example = "30")
        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be between 1 and 500")
        @Max(value = 500, message = "Capacity must be between 1 and 500")
        Integer capacity
) {
}
