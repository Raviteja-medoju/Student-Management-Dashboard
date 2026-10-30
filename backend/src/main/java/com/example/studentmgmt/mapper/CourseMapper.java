package com.example.studentmgmt.mapper;

import com.example.studentmgmt.dto.CourseDetailResponse;
import com.example.studentmgmt.dto.CourseRequest;
import com.example.studentmgmt.dto.CourseResponse;
import com.example.studentmgmt.dto.EnrolledStudentResponse;
import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Enrollment;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Converts between course entities and API DTOs. */
@Component
public class CourseMapper {

    public Course toEntity(CourseRequest request) {
        return new Course(
                normalizeCode(request.code()),
                request.title().trim(),
                blankToNull(request.description()),
                request.credits(),
                request.capacity());
    }

    public void updateEntity(Course course, CourseRequest request) {
        course.setCode(normalizeCode(request.code()));
        course.setTitle(request.title().trim());
        course.setDescription(blankToNull(request.description()));
        course.setCredits(request.credits());
        course.setCapacity(request.capacity());
    }

    public CourseResponse toResponse(Course course, long enrolledCount) {
        int enrolled = (int) enrolledCount;
        return new CourseResponse(
                course.getId(),
                course.getCode(),
                course.getTitle(),
                course.getDescription(),
                course.getCredits(),
                course.getCapacity(),
                enrolled,
                Math.max(0, course.getCapacity() - enrolled),
                course.getCreatedAt(),
                course.getUpdatedAt());
    }

    /** Detail view: expects enrollments (and their students) to be loaded already. */
    public CourseDetailResponse toDetailResponse(Course course) {
        List<EnrolledStudentResponse> students = course.getEnrollments().stream()
                .map(CourseMapper::toEnrolledStudent)
                .sorted(Comparator.comparing(EnrolledStudentResponse::fullName))
                .toList();
        int enrolled = students.size();
        return new CourseDetailResponse(
                course.getId(),
                course.getCode(),
                course.getTitle(),
                course.getDescription(),
                course.getCredits(),
                course.getCapacity(),
                enrolled,
                Math.max(0, course.getCapacity() - enrolled),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                students);
    }

    public static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static EnrolledStudentResponse toEnrolledStudent(Enrollment enrollment) {
        var student = enrollment.getStudent();
        return new EnrolledStudentResponse(student.getId(), student.getFullName(), student.getEmail(),
                enrollment.getEnrolledOn());
    }
}
