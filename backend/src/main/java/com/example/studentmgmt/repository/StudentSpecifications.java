package com.example.studentmgmt.repository;

import com.example.studentmgmt.dto.StudentFilter;
import com.example.studentmgmt.entity.Enrollment;
import com.example.studentmgmt.entity.Student;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Composable query pieces for the student list. Each filter is a small {@link Specification};
 * {@link #matching(StudentFilter)} combines only the ones the caller actually set.
 */
public final class StudentSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private StudentSpecifications() {
    }

    public static Specification<Student> matching(StudentFilter filter) {
        List<Specification<Student>> parts = new ArrayList<>();
        if (StringUtils.hasText(filter.search())) {
            parts.add(search(filter.search()));
        }
        if (filter.courseId() != null) {
            parts.add(enrolledIn(filter.courseId()));
        }
        if (filter.enrolled() != null) {
            parts.add(filter.enrolled() ? hasEnrollments() : Specification.not(hasEnrollments()));
        }
        if (filter.minCredits() != null && filter.minCredits() > 0) {
            parts.add(minCredits(filter.minCredits()));
        }
        return parts.isEmpty() ? Specification.unrestricted() : Specification.allOf(parts);
    }

    /** Case-insensitive match on first, last or full name, or email. % and _ are matched literally. */
    static Specification<Student> search(String term) {
        String pattern = "%" + escapeLike(term.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Expression<String> fullName = cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"));
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("lastName")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(fullName), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE));
        };
    }

    static Specification<Student> enrolledIn(Long courseId) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Enrollment> enrollment = sub.from(Enrollment.class);
            sub.select(enrollment.get("id")).where(
                    cb.equal(enrollment.get("student"), root),
                    cb.equal(enrollment.get("course").get("id"), courseId));
            return cb.exists(sub);
        };
    }

    static Specification<Student> hasEnrollments() {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Enrollment> enrollment = sub.from(Enrollment.class);
            sub.select(enrollment.get("id")).where(cb.equal(enrollment.get("student"), root));
            return cb.exists(sub);
        };
    }

    static Specification<Student> minCredits(int minCredits) {
        return (root, query, cb) -> {
            Subquery<Integer> credits = query.subquery(Integer.class);
            Root<Enrollment> enrollment = credits.from(Enrollment.class);
            credits.select(cb.coalesce(cb.sum(enrollment.get("course").get("credits")), 0))
                    .where(cb.equal(enrollment.get("student"), root));
            return cb.greaterThanOrEqualTo(credits, minCredits);
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
