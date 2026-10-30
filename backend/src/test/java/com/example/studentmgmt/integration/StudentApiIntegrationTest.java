package com.example.studentmgmt.integration;

import com.example.studentmgmt.client.StudentApiClient;
import com.example.studentmgmt.client.StudentApiException;
import com.example.studentmgmt.dto.CourseDetailResponse;
import com.example.studentmgmt.dto.CourseRequest;
import com.example.studentmgmt.dto.EnrolledCourseResponse;
import com.example.studentmgmt.dto.PageResponse;
import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.dto.StudentRequest;
import com.example.studentmgmt.dto.StudentResponse;
import com.example.studentmgmt.exception.ApiError;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.EnrollmentRepository;
import com.example.studentmgmt.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end: starts the real server on a random port and drives it over HTTP.
 * Students go through {@link StudentApiClient} (RestTemplate); courses and raw error checks
 * go through {@link TestRestTemplate}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class StudentApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private StudentApiClient client;

    @BeforeEach
    void setUp() {
        client = new StudentApiClient(restTemplateBuilder.rootUri("http://localhost:" + port).build());
        enrollmentRepository.deleteAllInBatch();
        studentRepository.deleteAllInBatch();
        courseRepository.deleteAllInBatch();
    }

    @Test
    void fullStudentLifecycle_throughRestTemplateClient() {
        // Create
        StudentDetailResponse created = client.create(
                new StudentRequest("Grace", "Hopper", "Grace@Navy.MIL", LocalDate.of(1990, 12, 9)));
        assertThat(created.id()).isNotNull();
        assertThat(created.email()).isEqualTo("grace@navy.mil");
        assertThat(created.createdAt()).isNotNull();

        // Read
        assertThat(client.findById(created.id()).fullName()).isEqualTo("Grace Hopper");

        // List with search
        PageResponse<StudentResponse> page = client.findAll("hopp", 0, 10);
        assertThat(page.content()).extracting(StudentResponse::id).containsExactly(created.id());
        assertThat(page.totalElements()).isEqualTo(1);

        // Update
        StudentDetailResponse updated = client.update(created.id(),
                new StudentRequest("Grace", "Brewster Hopper", "grace@navy.mil", LocalDate.of(1990, 12, 9)));
        assertThat(updated.lastName()).isEqualTo("Brewster Hopper");
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());

        // Enroll
        Long courseId = createCourse("CS101", 30).id();
        StudentDetailResponse enrolled = client.replaceCourses(created.id(), List.of(courseId));
        assertThat(enrolled.courses()).extracting(EnrolledCourseResponse::code).containsExactly("CS101");
        assertThat(enrolled.totalCredits()).isEqualTo(4);

        // Delete (enrollments go too)
        client.delete(created.id());
        assertThatThrownBy(() -> client.findById(created.id()))
                .isInstanceOfSatisfying(StudentApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                    assertThat(ex.getApiError().message())
                            .isEqualTo("Student with id %d was not found".formatted(created.id()));
                });
        assertThat(enrollmentRepository.count()).isZero();
        assertThat(courseRepository.count()).isEqualTo(1);
    }

    @Test
    void duplicateEmail_isRejectedWith409AndFieldError() {
        client.create(new StudentRequest("Ada", "Lovelace", "ada@university.edu", LocalDate.of(2000, 1, 1)));

        assertThatThrownBy(() -> client.create(
                new StudentRequest("Ada", "Again", "ADA@university.edu", LocalDate.of(2000, 1, 1))))
                .isInstanceOfSatisfying(StudentApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getApiError().fieldErrors()).extracting(ApiError.FieldError::field).containsExactly("email");
                });
        assertThat(studentRepository.count()).isEqualTo(1);
    }

    @Test
    void invalidPayload_isRejectedWith400AndFieldErrors() {
        assertThatThrownBy(() -> client.create(new StudentRequest("", "Lovelace", "nope", LocalDate.now().plusDays(1))))
                .isInstanceOfSatisfying(StudentApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getApiError().fieldErrors()).extracting(ApiError.FieldError::field)
                            .containsExactlyInAnyOrder("firstName", "email", "dateOfBirth");
                });
        assertThat(studentRepository.count()).isZero();
    }

    @Test
    void enrollingInAFullCourse_isRejectedWith409_andLeavesEnrollmentsUntouched() {
        Long seminar = createCourse("CS450", 1).id();
        Long first = client.create(new StudentRequest("First", "In", "first@university.edu", LocalDate.of(2000, 1, 1))).id();
        Long second = client.create(new StudentRequest("Second", "Late", "late@university.edu", LocalDate.of(2000, 1, 1))).id();
        client.replaceCourses(first, List.of(seminar));

        assertThatThrownBy(() -> client.replaceCourses(second, List.of(seminar)))
                .isInstanceOfSatisfying(StudentApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getMessage()).isEqualTo("Course CS450 is full (1 of 1 seats taken)");
                });
        assertThat(client.findById(second).courses()).isEmpty();
    }

    @Test
    void createCourse_returns201WithLocationHeader() {
        ResponseEntity<CourseDetailResponse> response = testRestTemplate.postForEntity("/api/courses",
                new CourseRequest("ma210", "Linear Algebra", "Vectors and matrices", 3, 40), CourseDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MA210");
        assertThat(response.getHeaders().getLocation()).hasPath("/api/courses/" + response.getBody().id());
    }

    @Test
    void exportCsv_returnsFilteredStudents() {
        Long seminar = createCourse("CS450", 10).id();
        Long ada = client.create(new StudentRequest("Ada", "Lovelace", "ada@university.edu", LocalDate.of(2000, 1, 1))).id();
        client.create(new StudentRequest("Alan", "Turing", "alan@university.edu", LocalDate.of(2000, 1, 1)));
        client.replaceCourses(ada, List.of(seminar));

        ResponseEntity<String> response = testRestTemplate.getForEntity(
                "/api/students/export?enrolled=true", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentDisposition().getFilename()).startsWith("students-").endsWith(".csv");
        assertThat(response.getBody()).contains("Lovelace").contains("CS450").doesNotContain("Turing");
    }

    @Test
    void emailAvailability_reflectsExistingStudents() {
        Long ada = client.create(new StudentRequest("Ada", "Lovelace", "ada@university.edu", LocalDate.of(2000, 1, 1))).id();

        assertThat(testRestTemplate.getForObject("/api/students/email-availability?email=ADA@university.edu", String.class))
                .contains("\"available\":false");
        assertThat(testRestTemplate.getForObject(
                "/api/students/email-availability?email=ada@university.edu&excludeId=" + ada, String.class))
                .contains("\"available\":true");
        assertThat(testRestTemplate.getForObject("/api/students/email-availability?email=new@university.edu", String.class))
                .contains("\"available\":true");
    }

    @Test
    void unknownEndpoint_returnsApiError404() {
        ResponseEntity<ApiError> response = testRestTemplate.getForEntity("/api/does-not-exist", ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("No endpoint GET /api/does-not-exist");
    }

    @Test
    void openApiDocsAreServed() {
        ResponseEntity<String> response = testRestTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Student Management API", "/api/students/{studentId}/courses");
    }

    private CourseDetailResponse createCourse(String code, int capacity) {
        ResponseEntity<CourseDetailResponse> response = testRestTemplate.postForEntity("/api/courses",
                new CourseRequest(code, "Course " + code, null, 4, capacity), CourseDetailResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
