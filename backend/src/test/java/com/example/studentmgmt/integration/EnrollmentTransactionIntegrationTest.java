package com.example.studentmgmt.integration;

import com.example.studentmgmt.dto.EnrolledCourseResponse;
import com.example.studentmgmt.exception.BusinessRuleException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.EnrollmentRepository;
import com.example.studentmgmt.repository.StudentRepository;
import com.example.studentmgmt.service.CourseService;
import com.example.studentmgmt.service.EnrollmentService;
import com.example.studentmgmt.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.example.studentmgmt.support.TestData.courseRequest;
import static com.example.studentmgmt.support.TestData.studentRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the all-or-nothing behaviour of {@code replaceEnrollments} against a real database
 * and real transactions (the test itself is NOT transactional, so every service call commits or rolls back).
 */
@SpringBootTest
@ActiveProfiles("test")
class EnrollmentTransactionIntegrationTest {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private Long algebra;
    private Long physics;
    private Long seminar;
    private Long student;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAllInBatch();
        studentRepository.deleteAllInBatch();
        courseRepository.deleteAllInBatch();

        algebra = courseService.create(courseRequest("MA210", 3, 10)).id();
        physics = courseService.create(courseRequest("PH110", 4, 10)).id();
        seminar = courseService.create(courseRequest("CS450", 3, 1)).id();

        Long other = studentService.create(studentRequest("Other", "Student", "other@university.edu")).id();
        enrollmentService.enroll(other, seminar); // the seminar is now full

        student = studentService.create(studentRequest("Ada", "Lovelace", "ada@university.edu")).id();
        enrollmentService.enroll(student, algebra);
    }

    @Test
    void replaceEnrollments_rollsBackEveryChange_whenOneCourseIsFull() {
        // Requested: drop algebra, add physics, add the full seminar.
        // Hibernate flushes the algebra delete and the physics insert before the seminar seat check fails,
        // so this only passes if the whole transaction is rolled back.
        assertThatThrownBy(() -> enrollmentService.replaceEnrollments(student, List.of(physics, seminar)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Course CS450 is full (1 of 1 seats taken)");

        assertThat(studentService.findById(student).courses())
                .extracting(EnrolledCourseResponse::code).containsExactly("MA210");
        assertThat(courseService.findById(physics).enrolledCount()).isZero();
        assertThat(enrollmentRepository.count()).isEqualTo(2);
    }

    @Test
    void replaceEnrollments_changesNothing_whenACourseDoesNotExist() {
        assertThatThrownBy(() -> enrollmentService.replaceEnrollments(student, List.of(physics, 987654L)))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(studentService.findById(student).courses())
                .extracting(EnrolledCourseResponse::code).containsExactly("MA210");
    }

    @Test
    void replaceEnrollments_commitsAllChanges_whenEverythingIsValid() {
        enrollmentService.replaceEnrollments(student, List.of(physics));

        assertThat(studentService.findById(student).courses())
                .extracting(EnrolledCourseResponse::code).containsExactly("PH110");
        assertThat(courseService.findById(algebra).enrolledCount()).isZero();
        assertThat(courseService.findById(physics).enrolledCount()).isEqualTo(1);
    }

    @Test
    void deletingACourse_removesItFromEveryStudent() {
        courseService.delete(algebra);

        assertThat(studentService.findById(student).courses()).isEmpty();
        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }
}
