package com.example.studentmgmt.config;

import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads demo data on startup so the app is not empty on first run.
 * Runs only when {@code app.seed.enabled=true} and the database has no students or courses yet.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final Clock clock;

    public DataSeeder(StudentRepository studentRepository, CourseRepository courseRepository, Clock clock) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (studentRepository.count() > 0 || courseRepository.count() > 0) {
            log.info("Database already has data; skipping demo seed");
            return;
        }

        Map<String, Course> courses = courseRepository.saveAll(List.of(
                new Course("CS101", "Introduction to Programming",
                        "Variables, control flow, functions and problem solving in Java.", 4, 40),
                new Course("CS201", "Data Structures and Algorithms",
                        "Lists, trees, graphs, hashing, sorting and complexity analysis.", 4, 35),
                new Course("CS305", "Database Systems",
                        "Relational modelling, SQL, indexing, transactions and normalization.", 3, 30),
                new Course("CS340", "Web Application Development",
                        "Building full-stack apps with REST APIs and a modern SPA framework.", 3, 30),
                new Course("CS450", "Machine Learning Seminar",
                        "Small research seminar on applied machine learning. Limited seats.", 3, 4),
                new Course("MA101", "Calculus I",
                        "Limits, derivatives, integrals and their applications.", 4, 50),
                new Course("MA210", "Linear Algebra",
                        "Vectors, matrices, linear maps, eigenvalues and decompositions.", 3, 40),
                new Course("PH110", "Physics for Engineers",
                        "Mechanics, energy, waves and an introduction to electromagnetism.", 4, 45),
                new Course("EN105", "Technical Writing",
                        "Writing clear documentation, reports and proposals for technical readers.", 2, 25)
        )).stream().collect(Collectors.toMap(Course::getCode, Function.identity()));

        LocalDate today = LocalDate.now(clock);
        List<Student> students = List.of(
                student(0, "Aarav", "Sharma", "2003-04-12", courses, today, "CS101", "CS201", "MA101", "CS450"),
                student(1, "Emma", "Johnson", "2002-09-30", courses, today, "CS305", "CS340", "EN105"),
                student(2, "Liam", "Chen", "2004-01-18", courses, today, "CS101", "MA101", "PH110"),
                student(3, "Sofia", "Martinez", "2001-11-05", courses, today, "CS201", "CS305", "CS450", "MA210"),
                student(4, "Noah", "Williams", "2003-07-22", courses, today, "CS340", "EN105"),
                student(5, "Priya", "Patel", "2002-03-14", courses, today, "CS201", "CS450", "MA210"),
                student(6, "Lucas", "Brown", "2004-06-09", courses, today, "CS101", "PH110"),
                student(7, "Mia", "Nguyen", "2003-12-01", courses, today, "CS305", "CS340", "CS450"),
                student(8, "Ethan", "Davis", "2001-08-27", courses, today, "MA101", "MA210", "PH110"),
                student(9, "Ava", "Kim", "2004-02-16", courses, today, "CS101", "EN105"),
                student(10, "Mateo", "Garcia", "2002-05-03", courses, today),
                student(11, "Zara", "Ahmed", "2003-10-19", courses, today, "CS201", "CS340", "MA101")
        );
        studentRepository.saveAll(students);

        log.info("Seeded {} courses and {} students", courses.size(), students.size());
    }

    /** Enrollment dates are spread over the last ~6 months so the dashboard trend chart has a shape. */
    private static Student student(int index, String firstName, String lastName, String dateOfBirth,
                                   Map<String, Course> courses, LocalDate today, String... courseCodes) {
        String email = (firstName + "." + lastName + "@university.edu").toLowerCase();
        Student student = new Student(firstName, lastName, email, LocalDate.parse(dateOfBirth));
        for (int i = 0; i < courseCodes.length; i++) {
            long daysAgo = (index * 47L + i * 29L) % 175;
            student.enrollIn(courses.get(courseCodes[i]), today.minusDays(daysAgo));
        }
        return student;
    }
}
