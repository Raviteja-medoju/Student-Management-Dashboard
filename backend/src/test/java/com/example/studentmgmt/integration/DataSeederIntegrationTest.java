package com.example.studentmgmt.integration;

import com.example.studentmgmt.dto.CourseResponse;
import com.example.studentmgmt.repository.StudentRepository;
import com.example.studentmgmt.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** The app starts with the demo data a reviewer sees on first run. */
@SpringBootTest(properties = "app.seed.enabled=true")
@ActiveProfiles("test")
class DataSeederIntegrationTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseService courseService;

    @Test
    void seedsStudentsCoursesAndAFullSeminar() {
        assertThat(studentRepository.count()).isEqualTo(12);
        assertThat(courseService.findAll(null)).hasSize(9);

        CourseResponse seminar = courseService.findAll("CS450").getFirst();
        assertThat(seminar.availableSeats()).isZero();
    }
}
