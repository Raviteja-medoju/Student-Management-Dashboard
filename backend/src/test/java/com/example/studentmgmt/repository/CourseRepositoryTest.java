package com.example.studentmgmt.repository;

import com.example.studentmgmt.entity.Course;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CourseRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void codeMustBeUnique_atDatabaseLevel() {
        courseRepository.saveAndFlush(new Course("CS101", "Intro", null, 4, 30));

        assertThatThrownBy(() -> courseRepository.saveAndFlush(new Course("CS101", "Other", null, 3, 10)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void creditsOutOfRange_areRejectedByCheckConstraint() {
        assertThatThrownBy(() -> courseRepository.saveAndFlush(new Course("CS102", "Too many credits", null, 9, 30)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void search_matchesCodeOrTitle_orderedByCode() {
        courseRepository.saveAll(List.of(
                new Course("MA101", "Calculus I", null, 4, 30),
                new Course("CS305", "Database Systems", null, 3, 30),
                new Course("CS101", "Intro to Programming", null, 4, 30)));

        assertThat(courseRepository.search("cs")).extracting(Course::getCode).containsExactly("CS101", "CS305");
        assertThat(courseRepository.search("CALC")).extracting(Course::getCode).containsExactly("MA101");
        assertThat(courseRepository.findAllByOrderByCodeAsc()).extracting(Course::getCode)
                .containsExactly("CS101", "CS305", "MA101");
    }

    @Test
    void findAllByIdForUpdate_returnsOnlyExistingCourses() {
        Course saved = courseRepository.save(new Course("CS101", "Intro", null, 4, 30));

        assertThat(courseRepository.findAllByIdForUpdate(List.of(saved.getId(), 9999L)))
                .extracting(Course::getCode).containsExactly("CS101");
    }

    @Test
    void existsByCode_ignoresCase_andCanExcludeOwnId() {
        Course saved = courseRepository.save(new Course("CS101", "Intro", null, 4, 30));

        assertThat(courseRepository.existsByCodeIgnoreCase("cs101")).isTrue();
        assertThat(courseRepository.existsByCodeIgnoreCaseAndIdNot("CS101", saved.getId())).isFalse();
    }
}
