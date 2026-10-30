package com.example.studentmgmt.service;

import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.exception.BusinessRuleException;
import com.example.studentmgmt.exception.DuplicateResourceException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.mapper.StudentMapper;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.EnrollmentRepository;
import com.example.studentmgmt.repository.StudentRepository;
import com.example.studentmgmt.service.impl.EnrollmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.studentmgmt.support.TestData.course;
import static com.example.studentmgmt.support.TestData.student;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    private EnrollmentServiceImpl service;

    private Student student;
    private Course cs101;
    private Course cs201;
    private Course ma101;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);
        service = new EnrollmentServiceImpl(studentRepository, courseRepository, enrollmentRepository,
                new StudentMapper(), fixedClock);

        student = student(1L, "Priya", "Patel");
        cs101 = course(10L, "CS101", 4, 30);
        cs201 = course(20L, "CS201", 4, 30);
        ma101 = course(30L, "MA101", 3, 2);
        student.enrollIn(cs101, LocalDate.of(2026, 9, 1));
    }

    // ---------- replaceEnrollments ----------

    @Test
    void replace_addsNewCourses_removesDroppedOnes_andKeepsExisting() {
        student.enrollIn(cs201, LocalDate.of(2026, 9, 1));
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findAllByIdForUpdate(Set.of(10L, 30L))).thenReturn(List.of(cs101, ma101));
        when(enrollmentRepository.countByCourseId(30L)).thenReturn(1L);

        StudentDetailResponse result = service.replaceEnrollments(1L, List.of(10L, 30L, 30L));

        assertThat(result.courses()).extracting("code").containsExactly("CS101", "MA101");
        assertThat(result.courses()).filteredOn("code", "MA101").extracting("enrolledOn").containsExactly(TODAY);
        assertThat(result.courses()).filteredOn("code", "CS101").extracting("enrolledOn")
                .containsExactly(LocalDate.of(2026, 9, 1));
        assertThat(result.totalCredits()).isEqualTo(7);
        verify(enrollmentRepository, never()).countByCourseId(10L); // already enrolled: no seat check
        verify(studentRepository).flush();
    }

    @Test
    void replace_failsWhenAnyCourseIsMissing_andChangesNothing() {
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findAllByIdForUpdate(Set.of(20L, 99L))).thenReturn(List.of(cs201));

        assertThatThrownBy(() -> service.replaceEnrollments(1L, List.of(20L, 99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Course(s) not found: [99]");

        assertThat(student.getEnrollments()).extracting("course.code").containsExactly("CS101");
        verify(enrollmentRepository, never()).countByCourseId(anyLong());
    }

    @Test
    void replace_failsWhenACourseIsFull() {
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findAllByIdForUpdate(Set.of(30L))).thenReturn(List.of(ma101));
        when(enrollmentRepository.countByCourseId(30L)).thenReturn(2L);

        assertThatThrownBy(() -> service.replaceEnrollments(1L, List.of(30L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Course MA101 is full (2 of 2 seats taken)");
        verify(studentRepository, never()).flush();
    }

    @Test
    void replace_withEmptyList_removesAllEnrollments_withoutQueryingCourses() {
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));

        StudentDetailResponse result = service.replaceEnrollments(1L, List.of());

        assertThat(result.courses()).isEmpty();
        assertThat(result.totalCredits()).isZero();
        verify(courseRepository, never()).findAllByIdForUpdate(anyCollection());
    }

    @Test
    void replace_throwsNotFound_whenStudentIsMissing() {
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceEnrollments(1L, List.of(10L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Student with id 1 was not found");
    }

    // ---------- enroll ----------

    @Test
    void enroll_addsCourseDatedToday() {
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findAllByIdForUpdate(List.of(20L))).thenReturn(List.of(cs201));
        when(enrollmentRepository.countByCourseId(20L)).thenReturn(0L);

        StudentDetailResponse result = service.enroll(1L, 20L);

        assertThat(result.courses()).extracting("code").containsExactly("CS101", "CS201");
        assertThat(result.courses().get(1).enrolledOn()).isEqualTo(TODAY);
    }

    @Test
    void enroll_rejectsDuplicateEnrollment() {
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findAllByIdForUpdate(List.of(10L))).thenReturn(List.of(cs101));

        assertThatThrownBy(() -> service.enroll(1L, 10L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Priya Patel is already enrolled in CS101");
    }

    @Test
    void enroll_rejectsFullCourse() {
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findAllByIdForUpdate(List.of(30L))).thenReturn(List.of(ma101));
        when(enrollmentRepository.countByCourseId(30L)).thenReturn(2L);

        assertThatThrownBy(() -> service.enroll(1L, 30L)).isInstanceOf(BusinessRuleException.class);
        assertThat(student.getEnrollments()).hasSize(1);
    }

    @Test
    void enroll_throwsNotFound_whenCourseIsMissing() {
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findAllByIdForUpdate(List.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.enroll(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Course with id 99 was not found");
    }

    // ---------- unenroll ----------

    @Test
    void unenroll_removesTheEnrollment() {
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));

        service.unenroll(1L, 10L);

        assertThat(student.getEnrollments()).isEmpty();
    }

    @Test
    void unenroll_throwsNotFound_whenStudentIsNotEnrolled() {
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.unenroll(1L, 20L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Student 1 is not enrolled in course 20");
        verify(enrollmentRepository, never()).delete(any());
    }
}
