package com.example.studentmgmt.client;

import com.example.studentmgmt.dto.EnrollmentRequest;
import com.example.studentmgmt.dto.PageResponse;
import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.dto.StudentRequest;
import com.example.studentmgmt.dto.StudentResponse;
import com.example.studentmgmt.exception.ApiError;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.function.Supplier;

/**
 * Typed Java client for the Student REST API, built on {@link RestTemplate}.
 * <p>
 * Other services (or scripts) can use it to talk to this API without hand-writing HTTP calls.
 * Error responses are turned into {@link StudentApiException} carrying the server's {@link ApiError}.
 * The end-to-end test {@code StudentApiIntegrationTest} drives the running app through this client.
 */
public class StudentApiClient {

    private static final ParameterizedTypeReference<PageResponse<StudentResponse>> STUDENT_PAGE =
            new ParameterizedTypeReference<>() {
            };

    private final RestTemplate restTemplate;

    /** @param restTemplate a RestTemplate whose root URI points at the API (e.g. http://localhost:8080) */
    public StudentApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public PageResponse<StudentResponse> findAll(String search, int page, int size) {
        return call(() -> restTemplate.exchange(
                "/api/students?search={search}&page={page}&size={size}",
                HttpMethod.GET, null, STUDENT_PAGE,
                search == null ? "" : search, page, size).getBody());
    }

    public StudentDetailResponse findById(long id) {
        return call(() -> restTemplate.getForObject("/api/students/{id}", StudentDetailResponse.class, id));
    }

    public StudentDetailResponse create(StudentRequest request) {
        return call(() -> restTemplate.postForObject("/api/students", request, StudentDetailResponse.class));
    }

    public StudentDetailResponse update(long id, StudentRequest request) {
        return call(() -> restTemplate.exchange("/api/students/{id}", HttpMethod.PUT,
                new HttpEntity<>(request), StudentDetailResponse.class, id).getBody());
    }

    public void delete(long id) {
        call(() -> {
            restTemplate.delete("/api/students/{id}", id);
            return null;
        });
    }

    public StudentDetailResponse replaceCourses(long studentId, List<Long> courseIds) {
        return call(() -> restTemplate.exchange("/api/students/{id}/courses", HttpMethod.PUT,
                new HttpEntity<>(new EnrollmentRequest(courseIds)), StudentDetailResponse.class, studentId).getBody());
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException ex) {
            throw new StudentApiException(ex.getStatusCode(), readApiError(ex), ex);
        }
    }

    private static ApiError readApiError(HttpStatusCodeException ex) {
        try {
            return ex.getResponseBodyAs(ApiError.class);
        } catch (RuntimeException unreadable) {
            return null; // body was not our ApiError JSON (e.g. a proxy error page)
        }
    }
}
