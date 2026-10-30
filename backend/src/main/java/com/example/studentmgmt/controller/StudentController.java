package com.example.studentmgmt.controller;

import com.example.studentmgmt.dto.EmailAvailabilityResponse;
import com.example.studentmgmt.dto.PageResponse;
import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.dto.StudentFilter;
import com.example.studentmgmt.dto.StudentRequest;
import com.example.studentmgmt.dto.StudentResponse;
import com.example.studentmgmt.mapper.StudentMapper;
import com.example.studentmgmt.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/students")
@Tag(name = "Students", description = "Create, read, update and delete students")
public class StudentController {

    private static final String SORT_FIELDS = "firstName|lastName|email|dateOfBirth|createdAt";
    private static final String SORT_MESSAGE = "sort must be one of firstName, lastName, email, dateOfBirth, createdAt";
    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    /** Lets Excel detect UTF-8, so names with accents open correctly. */
    private static final String UTF8_BOM = "﻿";

    private final StudentService studentService;
    private final Clock clock;

    public StudentController(StudentService studentService, Clock clock) {
        this.studentService = studentService;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "List students", description = "Paged and sortable, with optional search and filters")
    public PageResponse<StudentResponse> list(
            @Parameter(description = "Matches first name, last name, full name or email (case-insensitive)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Only students enrolled in this course")
            @RequestParam(required = false) @Positive(message = "courseId must be positive") Long courseId,
            @Parameter(description = "true = enrolled in at least one course, false = in none")
            @RequestParam(required = false) Boolean enrolled,
            @Parameter(description = "Only students with at least this many enrolled credits")
            @RequestParam(required = false)
            @Min(value = 0, message = "minCredits must be between 0 and 100")
            @Max(value = 100, message = "minCredits must be between 0 and 100") Integer minCredits,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be 0 or greater") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be between 1 and 100")
            @Max(value = 100, message = "size must be between 1 and 100") int size,
            @RequestParam(defaultValue = "lastName") @Pattern(regexp = SORT_FIELDS, message = SORT_MESSAGE) String sort,
            @RequestParam(defaultValue = "asc")
            @Pattern(regexp = "(?i)asc|desc", message = "direction must be asc or desc") String direction) {
        StudentFilter filter = new StudentFilter(search, courseId, enrolled, minCredits);
        return studentService.findAll(filter, PageRequest.of(page, size, toSort(sort, direction)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export students as CSV", description = "Same filters and sort as the list, without paging")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @Positive(message = "courseId must be positive") Long courseId,
            @RequestParam(required = false) Boolean enrolled,
            @RequestParam(required = false)
            @Min(value = 0, message = "minCredits must be between 0 and 100")
            @Max(value = 100, message = "minCredits must be between 0 and 100") Integer minCredits,
            @RequestParam(defaultValue = "lastName") @Pattern(regexp = SORT_FIELDS, message = SORT_MESSAGE) String sort,
            @RequestParam(defaultValue = "asc")
            @Pattern(regexp = "(?i)asc|desc", message = "direction must be asc or desc") String direction) {
        StudentFilter filter = new StudentFilter(search, courseId, enrolled, minCredits);
        String csv = studentService.exportCsv(filter, toSort(sort, direction));
        String fileName = "students-" + LocalDate.now(clock) + ".csv";
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body((UTF8_BOM + csv).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/email-availability")
    @Operation(summary = "Check whether an email is free",
            description = "Used by the form to warn about duplicates while typing. Pass excludeId when editing.")
    public EmailAvailabilityResponse emailAvailability(
            @RequestParam @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
            @RequestParam(required = false) Long excludeId) {
        return new EmailAvailabilityResponse(StudentMapper.normalizeEmail(email),
                studentService.isEmailAvailable(email, excludeId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a student with their enrolled courses")
    public StudentDetailResponse get(@PathVariable Long id) {
        return studentService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a student", description = "Returns 201 with a Location header; 409 if the email is taken")
    public ResponseEntity<StudentDetailResponse> create(@Valid @RequestBody StudentRequest request) {
        StudentDetailResponse created = studentService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a student")
    public StudentDetailResponse update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return studentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a student and their enrollments")
    public void delete(@PathVariable Long id) {
        studentService.delete(id);
    }

    /** Requested sort plus id as a tie-breaker, so paging is stable. */
    private static Sort toSort(String sort, String direction) {
        return Sort.by(Sort.Direction.fromString(direction), sort).and(Sort.by("id"));
    }
}
