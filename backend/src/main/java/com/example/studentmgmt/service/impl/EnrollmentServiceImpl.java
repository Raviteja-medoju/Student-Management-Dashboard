package com.example.studentmgmt.service.impl;

import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Enrollment;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.exception.BusinessRuleException;
import com.example.studentmgmt.exception.DuplicateResourceException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.mapper.StudentMapper;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.EnrollmentRepository;
import com.example.studentmgmt.repository.StudentRepository;
import com.example.studentmgmt.service.EnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentServiceImpl.class);

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentMapper studentMapper;
    private final Clock clock;

    public EnrollmentServiceImpl(StudentRepository studentRepository,
                                 CourseRepository courseRepository,
                                 EnrollmentRepository enrollmentRepository,
                                 StudentMapper studentMapper,
                                 Clock clock) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentMapper = studentMapper;
        this.clock = clock;
    }

    /**
     * All-or-nothing replacement of a student's course set.
     * <ol>
     *   <li>Validate that every requested course exists (nothing is touched if one is missing).</li>
     *   <li>Remove enrollments that are no longer requested.</li>
     *   <li>Add the new ones, checking seats. If any course is full the exception rolls back
     *       steps 2 and 3 together, even though Hibernate already flushed some of those changes.</li>
     * </ol>
     */
    @Override
    @Transactional
    public StudentDetailResponse replaceEnrollments(Long studentId, List<Long> courseIds) {
        Student student = loadStudentWithCourses(studentId);
        Set<Long> requestedIds = new LinkedHashSet<>(courseIds);

        Map<Long, Course> courses = requestedIds.isEmpty() ? Map.of()
                : courseRepository.findAllByIdForUpdate(requestedIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        List<Long> missing = requestedIds.stream().filter(id -> !courses.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("Course(s) not found: " + missing);
        }

        List<Enrollment> dropped = student.getEnrollments().stream()
                .filter(enrollment -> !requestedIds.contains(enrollment.getCourse().getId()))
                .toList();
        dropped.forEach(student::removeEnrollment);

        Set<Long> currentIds = student.getEnrollments().stream()
                .map(enrollment -> enrollment.getCourse().getId())
                .collect(Collectors.toSet());
        LocalDate today = LocalDate.now(clock);
        int added = 0;
        for (Long courseId : requestedIds) {
            if (!currentIds.contains(courseId)) {
                Course course = courses.get(courseId);
                ensureSeatAvailable(course);
                student.enrollIn(course, today);
                added++;
            }
        }

        studentRepository.flush();
        log.info("Replaced enrollments for student id={}: +{} -{}", studentId, added, dropped.size());
        return studentMapper.toDetailResponse(student);
    }

    @Override
    @Transactional
    public StudentDetailResponse enroll(Long studentId, Long courseId) {
        Student student = loadStudentWithCourses(studentId);
        Course course = courseRepository.findAllByIdForUpdate(List.of(courseId)).stream()
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseId));

        boolean alreadyEnrolled = student.getEnrollments().stream()
                .anyMatch(enrollment -> enrollment.getCourse().getId().equals(courseId));
        if (alreadyEnrolled) {
            throw new DuplicateResourceException(
                    "%s is already enrolled in %s".formatted(student.getFullName(), course.getCode()));
        }

        ensureSeatAvailable(course);
        student.enrollIn(course, LocalDate.now(clock));
        studentRepository.flush();
        log.info("Enrolled student id={} in course id={}", studentId, courseId);
        return studentMapper.toDetailResponse(student);
    }

    @Override
    @Transactional
    public void unenroll(Long studentId, Long courseId) {
        Student student = loadStudentWithCourses(studentId);
        Enrollment enrollment = student.getEnrollments().stream()
                .filter(e -> e.getCourse().getId().equals(courseId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student %d is not enrolled in course %d".formatted(studentId, courseId)));
        student.removeEnrollment(enrollment);
        log.info("Unenrolled student id={} from course id={}", studentId, courseId);
    }

    private Student loadStudentWithCourses(Long studentId) {
        return studentRepository.findWithCoursesById(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
    }

    private void ensureSeatAvailable(Course course) {
        long taken = enrollmentRepository.countByCourseId(course.getId());
        if (taken >= course.getCapacity()) {
            throw new BusinessRuleException("Course %s is full (%d of %d seats taken)"
                    .formatted(course.getCode(), taken, course.getCapacity()));
        }
    }
}
