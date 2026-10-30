package com.example.studentmgmt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * Join entity between {@link Student} and {@link Course}. Using an explicit entity instead of a
 * bare {@code @ManyToMany} lets the link carry data ({@code enrolledOn}) and lets the database
 * enforce one enrollment per (student, course) pair.
 */
@Entity
@Table(name = "enrollments",
        uniqueConstraints = @UniqueConstraint(name = "uk_enrollments_student_course",
                columnNames = {"student_id", "course_id"}))
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "enrolled_on", nullable = false)
    private LocalDate enrolledOn;

    protected Enrollment() {
        // required by JPA
    }

    Enrollment(Student student, Course course, LocalDate enrolledOn) {
        this.student = student;
        this.course = course;
        this.enrolledOn = enrolledOn;
    }

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public Course getCourse() {
        return course;
    }

    public LocalDate getEnrolledOn() {
        return enrolledOn;
    }
}
