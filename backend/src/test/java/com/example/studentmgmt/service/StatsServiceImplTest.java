package com.example.studentmgmt.service;

import com.example.studentmgmt.dto.StatsResponse;
import com.example.studentmgmt.mapper.CourseMapper;
import com.example.studentmgmt.mapper.StudentMapper;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.EnrollmentRepository;
import com.example.studentmgmt.repository.StudentRepository;
import com.example.studentmgmt.repository.projection.CourseEnrollmentCount;
import com.example.studentmgmt.repository.projection.MonthlyEnrollmentCount;
import com.example.studentmgmt.repository.projection.StudentEnrollmentSummary;
import com.example.studentmgmt.service.impl.StatsServiceImpl;
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

import static com.example.studentmgmt.support.TestData.course;
import static com.example.studentmgmt.support.TestData.student;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceImplTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    private StatsServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);
        service = new StatsServiceImpl(studentRepository, courseRepository, enrollmentRepository,
                new StudentMapper(), new CourseMapper(), clock);
    }

    @Test
    void getStats_computesTotalsAndRanksCoursesByEnrollment() {
        when(studentRepository.count()).thenReturn(3L);
        when(enrollmentRepository.count()).thenReturn(4L);
        when(enrollmentRepository.sumAllCredits()).thenReturn(14L);
        when(courseRepository.findAll()).thenReturn(List.of(
                course(1L, "CS101", 4, 10), course(2L, "MA101", 3, 10)));
        when(enrollmentRepository.countByCourseIds(List.of(1L, 2L))).thenReturn(List.of(
                new CourseEnrollmentCount(1L, 1L), new CourseEnrollmentCount(2L, 3L)));
        when(studentRepository.findTop5ByOrderByCreatedAtDescIdDesc()).thenReturn(List.of(student(3L, "Ava", "Kim")));
        when(enrollmentRepository.summarizeByStudentIds(List.of(3L)))
                .thenReturn(List.of(new StudentEnrollmentSummary(3L, 2L, 7L)));
        when(enrollmentRepository.countPerMonthSince(LocalDate.of(2026, 5, 1))).thenReturn(List.of(
                new MonthlyEnrollmentCount(2026, 9, 3L), new MonthlyEnrollmentCount(2026, 10, 1L)));
        when(enrollmentRepository.summarizeAllStudents()).thenReturn(List.of(
                new StudentEnrollmentSummary(1L, 1L, 3L),
                new StudentEnrollmentSummary(2L, 2L, 7L),
                new StudentEnrollmentSummary(3L, 4L, 19L)));

        StatsResponse stats = service.getStats();

        assertThat(stats.totalStudents()).isEqualTo(3);
        assertThat(stats.totalCourses()).isEqualTo(2);
        assertThat(stats.totalEnrollments()).isEqualTo(4);
        assertThat(stats.averageCreditsPerStudent()).isEqualTo(4.7);
        assertThat(stats.seatUtilizationPercent()).isEqualTo(20);
        assertThat(stats.popularCourses()).extracting("code").containsExactly("MA101", "CS101");
        assertThat(stats.recentStudents()).singleElement()
                .satisfies(s -> assertThat(s.totalCredits()).isEqualTo(7));
        assertThat(stats.enrollmentsPerCourse()).extracting("code").containsExactly("MA101", "CS101");
    }

    @Test
    void getStats_fillsSixMonths_includingEmptyOnes() {
        when(courseRepository.findAll()).thenReturn(List.of());
        when(studentRepository.findTop5ByOrderByCreatedAtDescIdDesc()).thenReturn(List.of());
        when(enrollmentRepository.countPerMonthSince(LocalDate.of(2026, 5, 1)))
                .thenReturn(List.of(new MonthlyEnrollmentCount(2026, 9, 3L)));

        StatsResponse stats = service.getStats();

        assertThat(stats.enrollmentsByMonth()).extracting("month")
                .containsExactly("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");
        assertThat(stats.enrollmentsByMonth()).extracting("enrollments").containsExactly(0L, 0L, 0L, 0L, 3L, 0L);
    }

    @Test
    void getStats_groupsStudentsIntoCreditBands_includingTheNotEnrolled() {
        when(studentRepository.count()).thenReturn(5L);
        when(courseRepository.findAll()).thenReturn(List.of());
        when(studentRepository.findTop5ByOrderByCreatedAtDescIdDesc()).thenReturn(List.of());
        when(enrollmentRepository.summarizeAllStudents()).thenReturn(List.of(
                new StudentEnrollmentSummary(1L, 1L, 3L),
                new StudentEnrollmentSummary(2L, 3L, 12L),
                new StudentEnrollmentSummary(3L, 6L, 19L)));

        StatsResponse stats = service.getStats();

        assertThat(stats.creditLoad()).extracting("label").containsExactly(
                "Not enrolled", "1–6 credits", "7–11 credits", "12–17 credits", "18+ credits");
        assertThat(stats.creditLoad()).extracting("students").containsExactly(2L, 1L, 0L, 1L, 1L);
    }

    @Test
    void getStats_onEmptyDatabase_returnsZeros() {
        when(courseRepository.findAll()).thenReturn(List.of());
        when(studentRepository.findTop5ByOrderByCreatedAtDescIdDesc()).thenReturn(List.of());

        StatsResponse stats = service.getStats();

        assertThat(stats.totalStudents()).isZero();
        assertThat(stats.averageCreditsPerStudent()).isZero();
        assertThat(stats.seatUtilizationPercent()).isZero();
        assertThat(stats.popularCourses()).isEmpty();
        assertThat(stats.recentStudents()).isEmpty();
    }
}
