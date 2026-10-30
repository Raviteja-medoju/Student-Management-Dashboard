package com.example.studentmgmt.service.impl;

import com.example.studentmgmt.dto.CourseDetailResponse;
import com.example.studentmgmt.dto.CourseRequest;
import com.example.studentmgmt.dto.CourseResponse;
import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.exception.BusinessRuleException;
import com.example.studentmgmt.exception.DuplicateResourceException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.mapper.CourseMapper;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.EnrollmentRepository;
import com.example.studentmgmt.repository.projection.CourseEnrollmentCount;
import com.example.studentmgmt.service.CourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseServiceImpl.class);

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseMapper courseMapper;

    public CourseServiceImpl(CourseRepository courseRepository,
                             EnrollmentRepository enrollmentRepository,
                             CourseMapper courseMapper) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseMapper = courseMapper;
    }

    @Override
    public List<CourseResponse> findAll(String search) {
        List<Course> courses = StringUtils.hasText(search)
                ? courseRepository.search(search.trim())
                : courseRepository.findAllByOrderByCodeAsc();
        Map<Long, Long> counts = enrolledCounts(courses);
        return courses.stream()
                .map(course -> courseMapper.toResponse(course, counts.getOrDefault(course.getId(), 0L)))
                .toList();
    }

    @Override
    public CourseDetailResponse findById(Long id) {
        return courseMapper.toDetailResponse(loadWithStudents(id));
    }

    @Override
    @Transactional
    public CourseDetailResponse create(CourseRequest request) {
        String code = CourseMapper.normalizeCode(request.code());
        if (courseRepository.existsByCodeIgnoreCase(code)) {
            throw duplicateCode(code);
        }
        Course saved = courseRepository.save(courseMapper.toEntity(request));
        log.info("Created course id={} code={}", saved.getId(), saved.getCode());
        return courseMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional
    public CourseDetailResponse update(Long id, CourseRequest request) {
        Course course = loadWithStudents(id);
        String code = CourseMapper.normalizeCode(request.code());
        if (courseRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw duplicateCode(code);
        }
        int enrolled = course.getEnrollments().size();
        if (request.capacity() < enrolled) {
            throw new BusinessRuleException(
                    "Capacity cannot be lower than the %d students already enrolled in %s".formatted(enrolled, course.getCode()));
        }
        courseMapper.updateEntity(course, request);
        courseRepository.flush(); // runs @PreUpdate so the response carries the new updatedAt
        log.info("Updated course id={}", id);
        return courseMapper.toDetailResponse(course);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", id));
        courseRepository.delete(course); // enrollments go with it (cascade + orphanRemoval)
        log.info("Deleted course id={}", id);
    }

    private Course loadWithStudents(Long id) {
        return courseRepository.findWithStudentsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", id));
    }

    private Map<Long, Long> enrolledCounts(List<Course> courses) {
        if (courses.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = courses.stream().map(Course::getId).toList();
        return enrollmentRepository.countByCourseIds(ids).stream()
                .collect(Collectors.toMap(CourseEnrollmentCount::courseId, CourseEnrollmentCount::enrolledCount));
    }

    private static DuplicateResourceException duplicateCode(String code) {
        return new DuplicateResourceException("A course with code '%s' already exists".formatted(code), "code");
    }
}
