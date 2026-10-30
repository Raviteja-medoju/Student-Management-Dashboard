package com.example.studentmgmt.controller;

import com.example.studentmgmt.dto.CourseDetailResponse;
import com.example.studentmgmt.dto.CourseRequest;
import com.example.studentmgmt.dto.CourseResponse;
import com.example.studentmgmt.dto.EnrolledStudentResponse;
import com.example.studentmgmt.exception.BusinessRuleException;
import com.example.studentmgmt.exception.DuplicateResourceException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private static final String VALID_BODY = """
            {"code": "CS101", "title": "Intro to Programming", "description": "Basics", "credits": 4, "capacity": 30}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService courseService;

    @Test
    void list_returnsCourses() throws Exception {
        when(courseService.findAll("cs")).thenReturn(List.of(
                new CourseResponse(1L, "CS101", "Intro", null, 4, 30, 12, 18, NOW, NOW)));

        mockMvc.perform(get("/api/courses").param("search", "cs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("CS101"))
                .andExpect(jsonPath("$[0].availableSeats").value(18));
    }

    @Test
    void get_returnsCourseWithRoster() throws Exception {
        when(courseService.findById(1L)).thenReturn(detail(1L,
                List.of(new EnrolledStudentResponse(3L, "Mia Nguyen", "mia@university.edu", LocalDate.of(2026, 9, 1)))));

        mockMvc.perform(get("/api/courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[0].fullName").value("Mia Nguyen"))
                .andExpect(jsonPath("$.students[0].enrolledOn").value("2026-09-01"));
    }

    @Test
    void get_returns404_whenMissing() throws Exception {
        when(courseService.findById(9L)).thenThrow(ResourceNotFoundException.of("Course", 9L));

        mockMvc.perform(get("/api/courses/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Course with id 9 was not found"));
    }

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        when(courseService.create(any(CourseRequest.class))).thenReturn(detail(5L, List.of()));

        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/courses/5")))
                .andExpect(jsonPath("$.code").value("CS101"));
    }

    @Test
    void create_returns400_forInvalidCodeCreditsAndCapacity() throws Exception {
        String invalid = """
                {"code": "COMPSCI-1", "title": "", "credits": 9, "capacity": 0}
                """;

        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        containsInAnyOrder("code", "title", "credits", "capacity")));
        verifyNoInteractions(courseService);
    }

    @Test
    void create_returns409_whenCodeIsTaken() throws Exception {
        when(courseService.create(any())).thenThrow(
                new DuplicateResourceException("A course with code 'CS101' already exists", "code"));

        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
    }

    @Test
    void update_returns409_whenCapacityDropsBelowEnrollment() throws Exception {
        when(courseService.update(eq(1L), any())).thenThrow(
                new BusinessRuleException("Capacity cannot be lower than the 12 students already enrolled in CS101"));

        mockMvc.perform(put("/api/courses/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Capacity cannot be lower than the 12 students already enrolled in CS101"));
    }

    @Test
    void update_returns200() throws Exception {
        when(courseService.update(eq(1L), any())).thenReturn(detail(1L, List.of()));

        mockMvc.perform(put("/api/courses/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/courses/1")).andExpect(status().isNoContent());
        verify(courseService).delete(1L);
    }

    @Test
    void delete_returns404_whenMissing() throws Exception {
        doThrow(ResourceNotFoundException.of("Course", 2L)).when(courseService).delete(2L);

        mockMvc.perform(delete("/api/courses/2")).andExpect(status().isNotFound());
    }

    private static CourseDetailResponse detail(long id, List<EnrolledStudentResponse> students) {
        return new CourseDetailResponse(id, "CS101", "Intro to Programming", "Basics", 4, 30,
                students.size(), 30 - students.size(), NOW, NOW, students);
    }
}
