package com.example.studentmgmt.service;

import com.example.studentmgmt.dto.CourseDetailResponse;
import com.example.studentmgmt.dto.CourseRequest;
import com.example.studentmgmt.dto.CourseResponse;
import com.example.studentmgmt.entity.Course;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.exception.BusinessRuleException;
import com.example.studentmgmt.exception.DuplicateResourceException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.mapper.CourseMapper;
import com.example.studentmgmt.repository.CourseRepository;
import com.example.studentmgmt.repository.EnrollmentRepository;
import com.example.studentmgmt.repository.projection.CourseEnrollmentCount;
import com.example.studentmgmt.service.impl.CourseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.example.studentmgmt.support.TestData.course;
import static com.example.studentmgmt.support.TestData.courseRequest;
import static com.example.studentmgmt.support.TestData.student;
import static com.example.studentmgmt.support.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    private CourseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CourseServiceImpl(courseRepository, enrollmentRepository, new CourseMapper());
    }

    @Test
    void findAll_returnsCoursesWithSeatNumbers() {
        when(courseRepository.findAllByOrderByCodeAsc())
                .thenReturn(List.of(course(1L, "CS101", 4, 30), course(2L, "MA101", 3, 2)));
        when(enrollmentRepository.countByCourseIds(List.of(1L, 2L)))
                .thenReturn(List.of(new CourseEnrollmentCount(2L, 2L)));

        List<CourseResponse> result = service.findAll(null);

        assertThat(result).extracting("code").containsExactly("CS101", "MA101");
        assertThat(result).extracting("enrolledCount").containsExactly(0, 2);
        assertThat(result).extracting("availableSeats").containsExactly(30, 0);
    }

    @Test
    void findAll_withSearch_usesSearchQuery_andSkipsCountsWhenNothingMatches() {
        when(courseRepository.search("calc")).thenReturn(List.of());

        assertThat(service.findAll(" calc ")).isEmpty();
        verify(enrollmentRepository, never()).countByCourseIds(anyCollection());
    }

    @Test
    void findById_returnsRosterSortedByName() {
        Course course = course(1L, "CS101", 4, 30);
        Student zoe = student(1L, "Zoe", "Adams");
        Student amy = student(2L, "Amy", "Baker");
        // Simulate what the entity graph loads: the course's inverse collection is populated.
        course.getEnrollments().add(zoe.enrollIn(course, LocalDate.of(2026, 9, 1)));
        course.getEnrollments().add(amy.enrollIn(course, LocalDate.of(2026, 9, 2)));
        when(courseRepository.findWithStudentsById(1L)).thenReturn(Optional.of(course));

        CourseDetailResponse result = service.findById(1L);

        assertThat(result.students()).extracting("fullName").containsExactly("Amy Baker", "Zoe Adams");
        assertThat(result.enrolledCount()).isEqualTo(2);
        assertThat(result.availableSeats()).isEqualTo(28);
    }

    @Test
    void findById_throwsNotFound() {
        when(courseRepository.findWithStudentsById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Course with id 9 was not found");
    }

    @Test
    void create_uppercasesCodeAndBlankDescriptionBecomesNull() {
        when(courseRepository.existsByCodeIgnoreCase("CS999")).thenReturn(false);
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> withId(inv.getArgument(0), 11L));

        CourseDetailResponse result = service.create(
                new CourseRequest(" cs999 ", " Compilers ", "   ", 3, 20));

        ArgumentCaptor<Course> saved = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("CS999");
        assertThat(saved.getValue().getTitle()).isEqualTo("Compilers");
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(result.id()).isEqualTo(11L);
        assertThat(result.availableSeats()).isEqualTo(20);
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(courseRepository.existsByCodeIgnoreCase("CS101")).thenReturn(true);

        assertThatThrownBy(() -> service.create(courseRequest("cs101", 3, 20)))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("field").isEqualTo("code");
        verify(courseRepository, never()).save(any());
    }

    @Test
    void update_appliesChanges() {
        Course existing = course(4L, "CS101", 3, 20);
        when(courseRepository.findWithStudentsById(4L)).thenReturn(Optional.of(existing));
        when(courseRepository.existsByCodeIgnoreCaseAndIdNot("CS102", 4L)).thenReturn(false);

        CourseDetailResponse result = service.update(4L, courseRequest("cs102", 5, 50));

        assertThat(result.code()).isEqualTo("CS102");
        assertThat(result.credits()).isEqualTo(5);
        assertThat(result.capacity()).isEqualTo(50);
        verify(courseRepository).flush();
    }

    @Test
    void update_rejectsCapacityBelowCurrentEnrollment() {
        Course existing = course(4L, "CS101", 3, 20);
        existing.getEnrollments().add(student(1L, "A", "One").enrollIn(existing, LocalDate.now()));
        existing.getEnrollments().add(student(2L, "B", "Two").enrollIn(existing, LocalDate.now()));
        when(courseRepository.findWithStudentsById(4L)).thenReturn(Optional.of(existing));
        when(courseRepository.existsByCodeIgnoreCaseAndIdNot("CS101", 4L)).thenReturn(false);

        assertThatThrownBy(() -> service.update(4L, courseRequest("CS101", 3, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 students already enrolled");
        assertThat(existing.getCapacity()).isEqualTo(20);
    }

    @Test
    void update_rejectsCodeOwnedByAnotherCourse() {
        when(courseRepository.findWithStudentsById(4L)).thenReturn(Optional.of(course(4L, "CS101", 3, 20)));
        when(courseRepository.existsByCodeIgnoreCaseAndIdNot("MA101", 4L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(4L, courseRequest("MA101", 3, 20)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void delete_removesCourse() {
        Course existing = course(4L, "CS101", 3, 20);
        when(courseRepository.findById(4L)).thenReturn(Optional.of(existing));

        service.delete(4L);

        verify(courseRepository).delete(existing);
    }

    @Test
    void delete_throwsNotFound() {
        when(courseRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(4L)).isInstanceOf(ResourceNotFoundException.class);
        verify(courseRepository, never()).delete(any());
    }
}
