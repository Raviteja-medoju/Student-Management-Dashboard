package com.example.studentmgmt.service;

import com.example.studentmgmt.dto.CourseDetailResponse;
import com.example.studentmgmt.dto.CourseRequest;
import com.example.studentmgmt.dto.CourseResponse;

import java.util.List;

public interface CourseService {

    List<CourseResponse> findAll(String search);

    CourseDetailResponse findById(Long id);

    CourseDetailResponse create(CourseRequest request);

    CourseDetailResponse update(Long id, CourseRequest request);

    void delete(Long id);
}
