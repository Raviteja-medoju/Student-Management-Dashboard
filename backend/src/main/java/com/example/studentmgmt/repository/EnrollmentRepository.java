package com.example.studentmgmt.repository;

import com.example.studentmgmt.entity.Enrollment;
import com.example.studentmgmt.repository.projection.CourseEnrollmentCount;
import com.example.studentmgmt.repository.projection.MonthlyEnrollmentCount;
import com.example.studentmgmt.repository.projection.StudentEnrollmentSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByStudentIdAndCourseId(Long studentId, Long courseId);

    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    long countByCourseId(Long courseId);

    /** Course count and total credits per student, for a page of students, in a single query. */
    @Query("""
            select new com.example.studentmgmt.repository.projection.StudentEnrollmentSummary(
                     e.student.id, count(e), coalesce(sum(e.course.credits), 0L))
            from Enrollment e
            where e.student.id in :studentIds
            group by e.student.id
            """)
    List<StudentEnrollmentSummary> summarizeByStudentIds(@Param("studentIds") Collection<Long> studentIds);

    /** Number of enrolled students per course, in a single query. */
    @Query("""
            select new com.example.studentmgmt.repository.projection.CourseEnrollmentCount(e.course.id, count(e))
            from Enrollment e
            where e.course.id in :courseIds
            group by e.course.id
            """)
    List<CourseEnrollmentCount> countByCourseIds(@Param("courseIds") Collection<Long> courseIds);

    @Query("select coalesce(sum(e.course.credits), 0L) from Enrollment e")
    long sumAllCredits();

    /** Course count and total credits for every student who has at least one enrollment. */
    @Query("""
            select new com.example.studentmgmt.repository.projection.StudentEnrollmentSummary(
                     e.student.id, count(e), coalesce(sum(e.course.credits), 0L))
            from Enrollment e
            group by e.student.id
            """)
    List<StudentEnrollmentSummary> summarizeAllStudents();

    /** Enrollments per calendar month since the given date (months without enrollments are absent). */
    @Query("""
            select new com.example.studentmgmt.repository.projection.MonthlyEnrollmentCount(
                     year(e.enrolledOn), month(e.enrolledOn), count(e))
            from Enrollment e
            where e.enrolledOn >= :from
            group by year(e.enrolledOn), month(e.enrolledOn)
            """)
    List<MonthlyEnrollmentCount> countPerMonthSince(@Param("from") LocalDate from);
}
