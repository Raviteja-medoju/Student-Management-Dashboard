package com.example.studentmgmt.support;

import com.example.studentmgmt.dto.CourseRequest;
import com.example.studentmgmt.dto.StudentRequest;
import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;

/** Builders for entities and requests used across unit tests. */
public final class TestData {

    public static final Instant CREATED_AT = Instant.parse("2026-09-01T10:00:00Z");

    private TestData() {
    }

    /** A student as if loaded from the database (id and timestamps set). */
    public static Student student(long id, String firstName, String lastName) {
        Student student = new Student(firstName, lastName,
                (firstName + "." + lastName + "@university.edu").toLowerCase(), LocalDate.of(2002, 5, 14));
        ReflectionTestUtils.setField(student, "id", id);
        ReflectionTestUtils.setField(student, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(student, "updatedAt", CREATED_AT);
        return student;
    }

    /** A course as if loaded from the database (id and timestamps set). */
    public static Course course(long id, String code, int credits, int capacity) {
        Course course = new Course(code, "Course " + code, "About " + code, credits, capacity);
        ReflectionTestUtils.setField(course, "id", id);
        ReflectionTestUtils.setField(course, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(course, "updatedAt", CREATED_AT);
        return course;
    }

    /** Simulates the database assigning an id on insert. */
    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static StudentRequest studentRequest(String firstName, String lastName, String email) {
        return new StudentRequest(firstName, lastName, email, LocalDate.of(2001, 3, 20));
    }

    public static CourseRequest courseRequest(String code, int credits, int capacity) {
        return new CourseRequest(code, "Course " + code, "About " + code, credits, capacity);
    }
}
