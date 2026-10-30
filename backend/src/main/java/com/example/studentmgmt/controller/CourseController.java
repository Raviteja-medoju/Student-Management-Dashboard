package com.example.studentmgmt.controller;

import com.example.studentmgmt.dto.CourseDetailResponse;
import com.example.studentmgmt.dto.CourseRequest;
import com.example.studentmgmt.dto.CourseResponse;
import com.example.studentmgmt.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import java.util.List;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses", description = "Create, read, update and delete courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    @Operation(summary = "List courses", description = "Sorted by code, with live seat counts")
    public List<CourseResponse> list(
            @Parameter(description = "Matches course code or title (case-insensitive)")
            @RequestParam(required = false) String search) {
        return courseService.findAll(search);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a course with its enrolled students")
    public CourseDetailResponse get(@PathVariable Long id) {
        return courseService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a course", description = "Returns 201 with a Location header; 409 if the code is taken")
    public ResponseEntity<CourseDetailResponse> create(@Valid @RequestBody CourseRequest request) {
        CourseDetailResponse created = courseService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a course", description = "409 if capacity would drop below current enrollments")
    public CourseDetailResponse update(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        return courseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a course and its enrollments")
    public void delete(@PathVariable Long id) {
        courseService.delete(id);
    }
}
