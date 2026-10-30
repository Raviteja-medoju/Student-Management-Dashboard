package com.example.studentmgmt.mapper;

import com.example.studentmgmt.dto.EnrolledCourseResponse;
import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.dto.StudentRequest;
import com.example.studentmgmt.dto.StudentResponse;
import com.example.studentmgmt.entity.Enrollment;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.repository.projection.StudentEnrollmentSummary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Converts between student entities and API DTOs. Entities never leave the service layer. */
@Component
public class StudentMapper {

    public Student toEntity(StudentRequest request) {
        return new Student(
                request.firstName().trim(),
                request.lastName().trim(),
                normalizeEmail(request.email()),
                request.dateOfBirth());
    }

    public void updateEntity(Student student, StudentRequest request) {
        student.setFirstName(request.firstName().trim());
        student.setLastName(request.lastName().trim());
        student.setEmail(normalizeEmail(request.email()));
        student.setDateOfBirth(request.dateOfBirth());
    }

    /** List view: enrollment totals come from one aggregate query instead of loading every collection. */
    public StudentResponse toResponse(Student student, StudentEnrollmentSummary summary) {
        int courseCount = summary == null ? 0 : summary.courseCount().intValue();
        int totalCredits = summary == null ? 0 : summary.totalCredits().intValue();
        return new StudentResponse(
                student.getId(),
                student.getFirstName(),
                student.getLastName(),
                student.getFullName(),
                student.getEmail(),
                student.getDateOfBirth(),
                courseCount,
                totalCredits,
                student.getCreatedAt(),
                student.getUpdatedAt());
    }

    /** Detail view: expects enrollments (and their courses) to be loaded already. */
    public StudentDetailResponse toDetailResponse(Student student) {
        List<EnrolledCourseResponse> courses = student.getEnrollments().stream()
                .map(StudentMapper::toEnrolledCourse)
                .sorted(Comparator.comparing(EnrolledCourseResponse::code))
                .toList();
        int totalCredits = courses.stream().mapToInt(EnrolledCourseResponse::credits).sum();
        return new StudentDetailResponse(
                student.getId(),
                student.getFirstName(),
                student.getLastName(),
                student.getFullName(),
                student.getEmail(),
                student.getDateOfBirth(),
                courses.size(),
                totalCredits,
                student.getCreatedAt(),
                student.getUpdatedAt(),
                courses);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static EnrolledCourseResponse toEnrolledCourse(Enrollment enrollment) {
        var course = enrollment.getCourse();
        return new EnrolledCourseResponse(course.getId(), course.getCode(), course.getTitle(),
                course.getCredits(), enrollment.getEnrolledOn());
    }
}
