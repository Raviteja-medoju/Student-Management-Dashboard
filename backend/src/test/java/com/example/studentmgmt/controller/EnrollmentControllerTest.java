package com.example.studentmgmt.controller;

import com.example.studentmgmt.dto.EnrolledCourseResponse;
import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.exception.BusinessRuleException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.service.EnrollmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnrollmentController.class)
class EnrollmentControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnrollmentService enrollmentService;

    @Test
    void replace_returns200WithUpdatedStudent() throws Exception {
        when(enrollmentService.replaceEnrollments(1L, List.of(10L, 20L))).thenReturn(studentWithCourse());

        mockMvc.perform(put("/api/students/1/courses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseIds\": [10, 20]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].code").value("CS101"));
    }

    @Test
    void replace_returns400_whenCourseIdsMissing() throws Exception {
        mockMvc.perform(put("/api/students/1/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("courseIds"));
        verifyNoInteractions(enrollmentService);
    }

    @Test
    void replace_returns409_whenACourseIsFull() throws Exception {
        when(enrollmentService.replaceEnrollments(1L, List.of(5L)))
                .thenThrow(new BusinessRuleException("Course CS450 is full (4 of 4 seats taken)"));

        mockMvc.perform(put("/api/students/1/courses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseIds\": [5]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Course CS450 is full (4 of 4 seats taken)"));
    }

    @Test
    void enroll_returns201() throws Exception {
        when(enrollmentService.enroll(1L, 10L)).thenReturn(studentWithCourse());

        mockMvc.perform(post("/api/students/1/courses/10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrolledCourseCount").value(1));
    }

    @Test
    void unenroll_returns204() throws Exception {
        mockMvc.perform(delete("/api/students/1/courses/10")).andExpect(status().isNoContent());
        verify(enrollmentService).unenroll(1L, 10L);
    }

    @Test
    void unenroll_returns404_whenNotEnrolled() throws Exception {
        doThrow(new ResourceNotFoundException("Student 1 is not enrolled in course 10"))
                .when(enrollmentService).unenroll(1L, 10L);

        mockMvc.perform(delete("/api/students/1/courses/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Student 1 is not enrolled in course 10"));
    }

    private static StudentDetailResponse studentWithCourse() {
        return new StudentDetailResponse(1L, "Ada", "Lovelace", "Ada Lovelace", "ada@university.edu",
                LocalDate.of(2000, 12, 10), 1, 4, NOW, NOW,
                List.of(new EnrolledCourseResponse(10L, "CS101", "Intro", 4, LocalDate.of(2026, 10, 1))));
    }
}
