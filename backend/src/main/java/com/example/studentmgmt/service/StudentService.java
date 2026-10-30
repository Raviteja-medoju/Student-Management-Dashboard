package com.example.studentmgmt.service;

import com.example.studentmgmt.dto.PageResponse;
import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.dto.StudentFilter;
import com.example.studentmgmt.dto.StudentRequest;
import com.example.studentmgmt.dto.StudentResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public interface StudentService {

    PageResponse<StudentResponse> findAll(StudentFilter filter, Pageable pageable);

    StudentDetailResponse findById(Long id);

    StudentDetailResponse create(StudentRequest request);

    StudentDetailResponse update(Long id, StudentRequest request);

    void delete(Long id);

    /** True if no other student uses this email ({@code excludeId} is the student being edited, if any). */
    boolean isEmailAvailable(String email, Long excludeId);

    /** All students matching the filter, as CSV. */
    String exportCsv(StudentFilter filter, Sort sort);
}
