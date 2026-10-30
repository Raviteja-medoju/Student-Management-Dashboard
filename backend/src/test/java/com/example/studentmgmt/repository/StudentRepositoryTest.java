package com.example.studentmgmt.repository;

import com.example.studentmgmt.dto.StudentFilter;
import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against a real H2 database with the Flyway schema, so constraints and queries are the real ones. */
@DataJpaTest
class StudentRepositoryTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void emailMustBeUnique_atDatabaseLevel() {
        studentRepository.saveAndFlush(new Student("Ada", "Lovelace", "ada@university.edu", LocalDate.of(2000, 1, 1)));

        Student duplicate = new Student("Ada", "Clone", "ada@university.edu", LocalDate.of(2001, 1, 1));
        assertThatThrownBy(() -> studentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByEmail_ignoresCase_andCanExcludeOwnId() {
        Student ada = studentRepository.save(new Student("Ada", "Lovelace", "ada@university.edu", LocalDate.of(2000, 1, 1)));

        assertThat(studentRepository.existsByEmailIgnoreCase("ADA@University.EDU")).isTrue();
        assertThat(studentRepository.existsByEmailIgnoreCaseAndIdNot("ada@university.edu", ada.getId())).isFalse();
        assertThat(studentRepository.existsByEmailIgnoreCaseAndIdNot("ada@university.edu", ada.getId() + 1)).isTrue();
    }

    @Test
    void search_matchesNamesFullNameAndEmail_caseInsensitively() {
        studentRepository.save(new Student("Grace", "Hopper", "grace@navy.mil", LocalDate.of(1990, 1, 1)));
        studentRepository.save(new Student("Alan", "Turing", "alan@bletchley.uk", LocalDate.of(1990, 1, 1)));
        studentRepository.save(new Student("Ada", "Lovelace", "countess@analytical.engine", LocalDate.of(1990, 1, 1)));

        assertThat(lastNames(StudentFilter.search("HOP"))).containsExactly("Hopper");
        assertThat(lastNames(StudentFilter.search("alan tur"))).containsExactly("Turing");
        assertThat(lastNames(StudentFilter.search("analytical"))).containsExactly("Lovelace");
        assertThat(lastNames(StudentFilter.search("a"))).hasSize(3);
    }

    @Test
    void search_treatsLikeWildcardsAsPlainText() {
        studentRepository.save(new Student("Ada", "Lovelace", "ada@university.edu", LocalDate.of(1990, 1, 1)));
        studentRepository.save(new Student("Pct", "Sign", "100%@university.edu", LocalDate.of(1990, 1, 1)));

        assertThat(lastNames(StudentFilter.search("%"))).containsExactly("Sign");
        assertThat(lastNames(StudentFilter.search("_"))).isEmpty();
    }

    @Test
    void search_isPaged() {
        for (int i = 0; i < 12; i++) {
            studentRepository.save(new Student("Student", "No" + i, "s" + i + "@university.edu", LocalDate.of(2000, 1, 1)));
        }

        Page<Student> second = studentRepository.findAll(
                StudentSpecifications.matching(StudentFilter.search("student")), PageRequest.of(1, 5));

        assertThat(second.getContent()).hasSize(5);
        assertThat(second.getTotalElements()).isEqualTo(12);
        assertThat(second.getTotalPages()).isEqualTo(3);
    }

    @Test
    void filters_byCourse_enrollmentStatus_andMinimumCredits_andCombineWithSearch() {
        Course cs101 = courseRepository.save(new Course("CS101", "Intro", null, 4, 30));
        Course ma101 = courseRepository.save(new Course("MA101", "Calculus", null, 4, 30));
        Course en105 = courseRepository.save(new Course("EN105", "Writing", null, 2, 30));
        Student heavy = new Student("Heavy", "Load", "heavy@university.edu", LocalDate.of(2000, 1, 1));
        heavy.enrollIn(cs101, LocalDate.of(2026, 9, 1));
        heavy.enrollIn(ma101, LocalDate.of(2026, 9, 1));
        heavy.enrollIn(en105, LocalDate.of(2026, 9, 1));
        Student light = new Student("Light", "Load", "light@university.edu", LocalDate.of(2000, 1, 1));
        light.enrollIn(en105, LocalDate.of(2026, 9, 1));
        Student none = new Student("No", "Courses", "none@university.edu", LocalDate.of(2000, 1, 1));
        studentRepository.saveAll(List.of(heavy, light, none));

        assertThat(firstNames(new StudentFilter(null, en105.getId(), null, null))).containsExactly("Heavy", "Light");
        assertThat(firstNames(new StudentFilter(null, cs101.getId(), null, null))).containsExactly("Heavy");
        assertThat(firstNames(new StudentFilter(null, null, true, null))).containsExactly("Heavy", "Light");
        assertThat(firstNames(new StudentFilter(null, null, false, null))).containsExactly("No");
        assertThat(firstNames(new StudentFilter(null, null, null, 10))).containsExactly("Heavy");
        assertThat(firstNames(new StudentFilter("light", en105.getId(), true, 2))).containsExactly("Light");
        assertThat(firstNames(StudentFilter.none())).hasSize(3);
    }

    @Test
    void findWithCoursesById_loadsEnrollmentsAndCoursesInOneGo() {
        Course course = courseRepository.save(new Course("CS101", "Intro", null, 4, 30));
        Student student = new Student("Liam", "Chen", "liam@university.edu", LocalDate.of(2004, 1, 18));
        student.enrollIn(course, LocalDate.of(2026, 9, 1));
        Long id = studentRepository.saveAndFlush(student).getId();
        entityManager.clear();

        Student loaded = studentRepository.findWithCoursesById(id).orElseThrow();

        var util = Persistence.getPersistenceUtil();
        assertThat(util.isLoaded(loaded, "enrollments")).isTrue();
        assertThat(util.isLoaded(loaded.getEnrollments().iterator().next(), "course")).isTrue();
    }

    @Test
    void deletingStudent_cascadesToEnrollments_butKeepsCourses() {
        Course course = courseRepository.save(new Course("CS101", "Intro", null, 4, 30));
        Student student = new Student("Liam", "Chen", "liam@university.edu", LocalDate.of(2004, 1, 18));
        student.enrollIn(course, LocalDate.of(2026, 9, 1));
        studentRepository.saveAndFlush(student);
        assertThat(enrollmentRepository.count()).isEqualTo(1);

        studentRepository.delete(student);
        studentRepository.flush();

        assertThat(enrollmentRepository.count()).isZero();
        assertThat(courseRepository.count()).isEqualTo(1);
    }

    private List<String> lastNames(StudentFilter filter) {
        return studentRepository.findAll(StudentSpecifications.matching(filter), Sort.by("lastName"))
                .stream().map(Student::getLastName).toList();
    }

    private List<String> firstNames(StudentFilter filter) {
        return studentRepository.findAll(StudentSpecifications.matching(filter), Sort.by("firstName"))
                .stream().map(Student::getFirstName).toList();
    }

    @Test
    void timestampsAreSetOnInsertAndUpdate() {
        Student student = studentRepository.saveAndFlush(
                new Student("Ava", "Kim", "ava@university.edu", LocalDate.of(2004, 2, 16)));
        assertThat(student.getCreatedAt()).isNotNull();
        assertThat(student.getUpdatedAt()).isEqualTo(student.getCreatedAt());

        student.setLastName("Kimura");
        studentRepository.saveAndFlush(student);

        assertThat(student.getUpdatedAt()).isAfterOrEqualTo(student.getCreatedAt());
    }
}
