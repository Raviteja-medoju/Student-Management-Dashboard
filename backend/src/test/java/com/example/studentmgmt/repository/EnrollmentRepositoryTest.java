package com.example.studentmgmt.repository;

import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.repository.projection.CourseEnrollmentCount;
import com.example.studentmgmt.repository.projection.MonthlyEnrollmentCount;
import com.example.studentmgmt.repository.projection.StudentEnrollmentSummary;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class EnrollmentRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private EntityManager entityManager;

    private Course cs101;
    private Course ma101;
    private Student ada;
    private Student alan;

    @BeforeEach
    void setUp() {
        cs101 = courseRepository.save(new Course("CS101", "Intro", null, 4, 30));
        ma101 = courseRepository.save(new Course("MA101", "Calculus", null, 3, 30));
        ada = new Student("Ada", "Lovelace", "ada@university.edu", LocalDate.of(2000, 1, 1));
        ada.enrollIn(cs101, DAY);
        ada.enrollIn(ma101, DAY);
        alan = new Student("Alan", "Turing", "alan@university.edu", LocalDate.of(2000, 1, 1));
        alan.enrollIn(cs101, DAY);
        studentRepository.saveAllAndFlush(List.of(ada, alan));
    }

    @Test
    void studentCannotBeEnrolledTwiceInTheSameCourse() {
        ada.enrollIn(cs101, DAY.plusDays(1));

        assertThatThrownBy(() -> studentRepository.saveAndFlush(ada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void summarizeByStudentIds_returnsCourseCountAndCredits() {
        Student nobody = studentRepository.save(new Student("No", "Courses", "none@university.edu", DAY.minusYears(20)));

        List<StudentEnrollmentSummary> summaries =
                enrollmentRepository.summarizeByStudentIds(List.of(ada.getId(), alan.getId(), nobody.getId()));

        assertThat(summaries).containsExactlyInAnyOrder(
                new StudentEnrollmentSummary(ada.getId(), 2L, 7L),
                new StudentEnrollmentSummary(alan.getId(), 1L, 4L));
    }

    @Test
    void countByCourseIds_andCountByCourseId() {
        assertThat(enrollmentRepository.countByCourseIds(List.of(cs101.getId(), ma101.getId())))
                .containsExactlyInAnyOrder(
                        new CourseEnrollmentCount(cs101.getId(), 2L),
                        new CourseEnrollmentCount(ma101.getId(), 1L));
        assertThat(enrollmentRepository.countByCourseId(cs101.getId())).isEqualTo(2);
    }

    @Test
    void summarizeAllStudents_coversEveryEnrolledStudent() {
        assertThat(enrollmentRepository.summarizeAllStudents()).containsExactlyInAnyOrder(
                new StudentEnrollmentSummary(ada.getId(), 2L, 7L),
                new StudentEnrollmentSummary(alan.getId(), 1L, 4L));
    }

    @Test
    void countPerMonthSince_groupsByCalendarMonth() {
        Student late = new Student("Late", "Joiner", "late@university.edu", DAY.minusYears(20));
        late.enrollIn(cs101, LocalDate.of(2026, 10, 3));
        late.enrollIn(ma101, LocalDate.of(2026, 10, 20));
        Student old = new Student("Old", "Timer", "old@university.edu", DAY.minusYears(20));
        old.enrollIn(cs101, LocalDate.of(2025, 1, 15));
        studentRepository.saveAllAndFlush(List.of(late, old));

        assertThat(enrollmentRepository.countPerMonthSince(LocalDate.of(2026, 5, 1))).containsExactlyInAnyOrder(
                new MonthlyEnrollmentCount(2026, 9, 3L),
                new MonthlyEnrollmentCount(2026, 10, 2L));
    }

    @Test
    void sumAllCredits_addsCreditsOfEveryEnrollment() {
        assertThat(enrollmentRepository.sumAllCredits()).isEqualTo(4 + 3 + 4);
    }

    @Test
    void existsAndFindByStudentAndCourse() {
        assertThat(enrollmentRepository.existsByStudentIdAndCourseId(alan.getId(), cs101.getId())).isTrue();
        assertThat(enrollmentRepository.existsByStudentIdAndCourseId(alan.getId(), ma101.getId())).isFalse();
        assertThat(enrollmentRepository.findByStudentIdAndCourseId(ada.getId(), ma101.getId())).isPresent();
    }

    @Test
    void deletingCourse_cascadesToEnrollments_butKeepsStudents() {
        entityManager.clear(); // start from a clean persistence context, like a new request would
        Course course = courseRepository.findById(cs101.getId()).orElseThrow();

        courseRepository.delete(course);
        courseRepository.flush();

        assertThat(enrollmentRepository.count()).isEqualTo(1);
        assertThat(studentRepository.count()).isEqualTo(2);
    }
}
