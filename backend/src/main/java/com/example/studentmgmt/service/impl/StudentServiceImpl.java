package com.example.studentmgmt.service.impl;

import com.example.studentmgmt.dto.PageResponse;
import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.dto.StudentFilter;
import com.example.studentmgmt.dto.StudentRequest;
import com.example.studentmgmt.dto.StudentResponse;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.exception.DuplicateResourceException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.export.StudentCsvExporter;
import com.example.studentmgmt.mapper.StudentMapper;
import com.example.studentmgmt.repository.EnrollmentRepository;
import com.example.studentmgmt.repository.StudentRepository;
import com.example.studentmgmt.repository.StudentSpecifications;
import com.example.studentmgmt.repository.projection.StudentEnrollmentSummary;
import com.example.studentmgmt.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StudentServiceImpl implements StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentMapper studentMapper;
    private final StudentCsvExporter csvExporter;

    public StudentServiceImpl(StudentRepository studentRepository,
                              EnrollmentRepository enrollmentRepository,
                              StudentMapper studentMapper,
                              StudentCsvExporter csvExporter) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentMapper = studentMapper;
        this.csvExporter = csvExporter;
    }

    @Override
    public PageResponse<StudentResponse> findAll(StudentFilter filter, Pageable pageable) {
        Specification<Student> spec = StudentSpecifications.matching(filter);
        Page<Student> page = studentRepository.findAll(spec, pageable);
        Map<Long, StudentEnrollmentSummary> summaries = summarize(page.getContent());
        return PageResponse.from(page, student -> studentMapper.toResponse(student, summaries.get(student.getId())));
    }

    @Override
    public StudentDetailResponse findById(Long id) {
        return studentMapper.toDetailResponse(loadWithCourses(id));
    }

    @Override
    @Transactional
    public StudentDetailResponse create(StudentRequest request) {
        String email = StudentMapper.normalizeEmail(request.email());
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw duplicateEmail(email);
        }
        Student saved = studentRepository.save(studentMapper.toEntity(request));
        log.info("Created student id={} email={}", saved.getId(), saved.getEmail());
        return studentMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional
    public StudentDetailResponse update(Long id, StudentRequest request) {
        Student student = loadWithCourses(id);
        String email = StudentMapper.normalizeEmail(request.email());
        if (studentRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw duplicateEmail(email);
        }
        studentMapper.updateEntity(student, request);
        studentRepository.flush(); // runs @PreUpdate so the response carries the new updatedAt
        log.info("Updated student id={}", id);
        return studentMapper.toDetailResponse(student);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", id));
        studentRepository.delete(student); // enrollments go with it (cascade + orphanRemoval)
        log.info("Deleted student id={}", id);
    }

    @Override
    public boolean isEmailAvailable(String email, Long excludeId) {
        String normalized = StudentMapper.normalizeEmail(email);
        return excludeId == null
                ? !studentRepository.existsByEmailIgnoreCase(normalized)
                : !studentRepository.existsByEmailIgnoreCaseAndIdNot(normalized, excludeId);
    }

    @Override
    public String exportCsv(StudentFilter filter, Sort sort) {
        Specification<Student> spec = StudentSpecifications.matching(filter);
        List<Student> students = studentRepository.findAll(spec, sort);
        log.info("Exporting {} students as CSV", students.size());
        // Enrollments and courses load in batches (hibernate.default_batch_fetch_size), not one query per row.
        return csvExporter.export(students);
    }

    private Student loadWithCourses(Long id) {
        return studentRepository.findWithCoursesById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", id));
    }

    private Map<Long, StudentEnrollmentSummary> summarize(List<Student> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = students.stream().map(Student::getId).toList();
        return enrollmentRepository.summarizeByStudentIds(ids).stream()
                .collect(Collectors.toMap(StudentEnrollmentSummary::studentId, Function.identity()));
    }

    private static DuplicateResourceException duplicateEmail(String email) {
        return new DuplicateResourceException("A student with email '%s' already exists".formatted(email), "email");
    }
}
