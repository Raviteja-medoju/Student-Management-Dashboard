package com.example.studentmgmt.controller;

import com.example.studentmgmt.dto.PageResponse;
import com.example.studentmgmt.dto.StudentFilter;
import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.dto.StudentRequest;
import com.example.studentmgmt.dto.StudentResponse;
import com.example.studentmgmt.exception.DuplicateResourceException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.service.StudentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentController.class)
class StudentControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private static final String VALID_BODY = """
            {"firstName": "Ada", "lastName": "Lovelace", "email": "ada@university.edu", "dateOfBirth": "2000-12-10"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    // ---------- GET /api/students ----------

    @Test
    void list_returnsPage_andPassesSearchPagingAndSortToService() throws Exception {
        StudentResponse ada = new StudentResponse(1L, "Ada", "Lovelace", "Ada Lovelace", "ada@university.edu",
                LocalDate.of(2000, 12, 10), 2, 7, NOW, NOW);
        when(studentService.findAll(eq(StudentFilter.search("ada")), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(ada), 1, 5, 6, 2, false, true));

        mockMvc.perform(get("/api/students")
                        .param("search", "ada").param("page", "1").param("size", "5")
                        .param("sort", "email").param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.content[0].dateOfBirth").value("2000-12-10"))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.last").value(true));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(studentService).findAll(eq(StudentFilter.search("ada")), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("email").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_rejectsInvalidQueryParameters() throws Exception {
        mockMvc.perform(get("/api/students").param("size", "500").param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("size", "sort")));
        verifyNoInteractions(studentService);
    }

    @Test
    void list_passesFiltersToTheService() throws Exception {
        when(studentService.findAll(any(StudentFilter.class), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true));

        mockMvc.perform(get("/api/students").param("courseId", "3").param("enrolled", "true").param("minCredits", "12"))
                .andExpect(status().isOk());

        verify(studentService).findAll(eq(new StudentFilter(null, 3L, true, 12)), any(Pageable.class));
    }

    @Test
    void list_rejectsInvalidFilters() throws Exception {
        mockMvc.perform(get("/api/students").param("courseId", "-1").param("minCredits", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("courseId", "minCredits")));
        verifyNoInteractions(studentService);
    }

    // ---------- GET /api/students/export ----------

    @Test
    void export_returnsCsvAttachmentWithDatedFileName() throws Exception {
        when(studentService.exportCsv(eq(new StudentFilter(null, 5L, null, null)), any()))
                .thenReturn("id,first_name\r\n1,Ada\r\n");

        mockMvc.perform(get("/api/students/export").param("courseId", "5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"students-2026-10-29.csv\""))
                .andExpect(content().string("\uFEFFid,first_name\r\n1,Ada\r\n"));
    }

    // ---------- GET /api/students/email-availability ----------

    @Test
    void emailAvailability_reportsWhetherTheEmailIsFree() throws Exception {
        when(studentService.isEmailAvailable("Ada@University.edu", 4L)).thenReturn(false);

        mockMvc.perform(get("/api/students/email-availability")
                        .param("email", "Ada@University.edu").param("excludeId", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@university.edu"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void emailAvailability_rejectsInvalidEmail() throws Exception {
        mockMvc.perform(get("/api/students/email-availability").param("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
        verifyNoInteractions(studentService);
    }

    // ---------- GET /api/students/{id} ----------

    @Test
    void get_returnsStudentWithCourses() throws Exception {
        when(studentService.findById(1L)).thenReturn(detail(1L));

        mockMvc.perform(get("/api/students/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("ada@university.edu"))
                .andExpect(jsonPath("$.courses").isArray());
    }

    @Test
    void get_returns404WithApiError_whenMissing() throws Exception {
        when(studentService.findById(99L)).thenThrow(ResourceNotFoundException.of("Student", 99L));

        mockMvc.perform(get("/api/students/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Student with id 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/students/99"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void get_returns400_whenIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/students/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'id' must be a valid Long"));
    }

    // ---------- POST /api/students ----------

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        when(studentService.create(any(StudentRequest.class))).thenReturn(detail(42L));

        mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/students/42")))
                .andExpect(jsonPath("$.id").value(42));

        verify(studentService).create(new StudentRequest("Ada", "Lovelace", "ada@university.edu", LocalDate.of(2000, 12, 10)));
    }

    @Test
    void create_returns400WithFieldErrors_whenBodyIsInvalid() throws Exception {
        String invalid = """
                {"firstName": " ", "lastName": "%s", "email": "not-an-email", "dateOfBirth": "2999-01-01"}
                """.formatted("x".repeat(51));

        mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        containsInAnyOrder("firstName", "lastName", "email", "dateOfBirth")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].message")
                        .value("Email must be a valid email address"));
        verifyNoInteractions(studentService);
    }

    @Test
    void create_returns409WithEmailFieldError_whenEmailIsTaken() throws Exception {
        when(studentService.create(any())).thenThrow(
                new DuplicateResourceException("A student with email 'ada@university.edu' already exists", "email"));

        mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("A student with email 'ada@university.edu' already exists"));
    }

    @Test
    void create_returns400_whenJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content("{\"firstName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or is not valid JSON"));
    }

    @Test
    void create_returns415_whenContentTypeIsNotJson() throws Exception {
        mockMvc.perform(post("/api/students").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---------- PUT / DELETE ----------

    @Test
    void update_returns200WithUpdatedStudent() throws Exception {
        when(studentService.update(eq(1L), any(StudentRequest.class))).thenReturn(detail(1L));

        mockMvc.perform(put("/api/students/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ada"));
    }

    @Test
    void update_returns404_whenMissing() throws Exception {
        when(studentService.update(eq(7L), any())).thenThrow(ResourceNotFoundException.of("Student", 7L));

        mockMvc.perform(put("/api/students/7").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/students/1")).andExpect(status().isNoContent());
        verify(studentService).delete(1L);
    }

    @Test
    void delete_returns404_whenMissing() throws Exception {
        doThrow(ResourceNotFoundException.of("Student", 5L)).when(studentService).delete(5L);

        mockMvc.perform(delete("/api/students/5")).andExpect(status().isNotFound());
    }

    // ---------- generic errors ----------

    @Test
    void unexpectedError_returns500_withoutLeakingDetails() throws Exception {
        when(studentService.findById(1L)).thenThrow(new IllegalStateException("database password is hunter2"));

        mockMvc.perform(get("/api/students/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."));
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(patch("/api/students/1")).andExpect(status().isMethodNotAllowed());
    }

    private static StudentDetailResponse detail(long id) {
        return new StudentDetailResponse(id, "Ada", "Lovelace", "Ada Lovelace", "ada@university.edu",
                LocalDate.of(2000, 12, 10), 0, 0, NOW, NOW, List.of());
    }
}
