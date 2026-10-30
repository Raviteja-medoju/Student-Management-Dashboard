package com.example.studentmgmt.service;

import com.example.studentmgmt.dto.PageResponse;
import com.example.studentmgmt.dto.StudentDetailResponse;
import com.example.studentmgmt.dto.StudentFilter;
import com.example.studentmgmt.dto.StudentResponse;
import com.example.studentmgmt.entity.Student;
import com.example.studentmgmt.exception.DuplicateResourceException;
import com.example.studentmgmt.exception.ResourceNotFoundException;
import com.example.studentmgmt.export.StudentCsvExporter;
import com.example.studentmgmt.mapper.StudentMapper;
import com.example.studentmgmt.repository.EnrollmentRepository;
import com.example.studentmgmt.repository.StudentRepository;
import com.example.studentmgmt.repository.projection.StudentEnrollmentSummary;
import com.example.studentmgmt.service.impl.StudentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.example.studentmgmt.support.TestData.course;
import static com.example.studentmgmt.support.TestData.student;
import static com.example.studentmgmt.support.TestData.studentRequest;
import static com.example.studentmgmt.support.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class StudentServiceImplTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private StudentCsvExporter csvExporter;

    private StudentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentServiceImpl(studentRepository, enrollmentRepository, new StudentMapper(), csvExporter);
    }

    // ---------- create ----------

    @Test
    void create_savesTrimmedStudentWithLowercaseEmail() {
        when(studentRepository.existsByEmailIgnoreCase("ada@university.edu")).thenReturn(false);
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));

        StudentDetailResponse result = service.create(studentRequest("  Ada ", " Lovelace ", " ADA@University.edu "));

        ArgumentCaptor<Student> saved = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("Ada");
        assertThat(saved.getValue().getLastName()).isEqualTo("Lovelace");
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@university.edu");
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.fullName()).isEqualTo("Ada Lovelace");
        assertThat(result.courses()).isEmpty();
    }

    @Test
    void create_rejectsDuplicateEmail_andSavesNothing() {
        when(studentRepository.existsByEmailIgnoreCase("ada@university.edu")).thenReturn(true);

        assertThatThrownBy(() -> service.create(studentRequest("Ada", "Lovelace", "Ada@University.edu")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ada@university.edu")
                .extracting("field").isEqualTo("email");
        verify(studentRepository, never()).save(any());
    }

    // ---------- read ----------

    @Test
    void findById_returnsCoursesSortedByCodeWithTotals() {
        Student student = student(1L, "Liam", "Chen");
        student.enrollIn(course(2L, "CS201", 4, 30), LocalDate.of(2026, 9, 1));
        student.enrollIn(course(1L, "CS101", 3, 30), LocalDate.of(2026, 9, 2));
        when(studentRepository.findWithCoursesById(1L)).thenReturn(Optional.of(student));

        StudentDetailResponse result = service.findById(1L);

        assertThat(result.courses()).extracting("code").containsExactly("CS101", "CS201");
        assertThat(result.enrolledCourseCount()).isEqualTo(2);
        assertThat(result.totalCredits()).isEqualTo(7);
    }

    @Test
    void findById_throwsNotFound_whenStudentIsMissing() {
        when(studentRepository.findWithCoursesById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Student with id 99 was not found");
    }

    @Test
    void findAll_queriesWithSpecification_andAttachesEnrollmentTotalsInOneQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Student> page = new PageImpl<>(List.of(student(1L, "Ann", "Lee"), student(2L, "Joann", "Park")), pageable, 2);
        when(studentRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(enrollmentRepository.summarizeByStudentIds(List.of(1L, 2L)))
                .thenReturn(List.of(new StudentEnrollmentSummary(1L, 2L, 7L)));

        PageResponse<StudentResponse> result = service.findAll(StudentFilter.search("ann"), pageable);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting("enrolledCourseCount").containsExactly(2, 0);
        assertThat(result.content()).extracting("totalCredits").containsExactly(7, 0);
    }

    @Test
    void findAll_skipsAggregateQueryForEmptyPage() {
        Pageable pageable = PageRequest.of(3, 10);
        when(studentRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

        PageResponse<StudentResponse> result = service.findAll(StudentFilter.none(), pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(3);
        verify(enrollmentRepository, never()).summarizeByStudentIds(anyCollection());
    }

    // ---------- email availability & export ----------

    @Test
    void isEmailAvailable_normalizesAndChecksAllStudents_whenCreating() {
        when(studentRepository.existsByEmailIgnoreCase("ada@university.edu")).thenReturn(true);

        assertThat(service.isEmailAvailable(" ADA@university.edu ", null)).isFalse();
    }

    @Test
    void isEmailAvailable_ignoresTheStudentBeingEdited() {
        when(studentRepository.existsByEmailIgnoreCaseAndIdNot("ada@university.edu", 4L)).thenReturn(false);

        assertThat(service.isEmailAvailable("ada@university.edu", 4L)).isTrue();
        verify(studentRepository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    void exportCsv_writesEveryMatchingStudent() {
        Sort sort = Sort.by("lastName");
        List<Student> students = List.of(student(1L, "Ada", "Lovelace"));
        when(studentRepository.findAll(any(Specification.class), eq(sort))).thenReturn(students);
        when(csvExporter.export(students)).thenReturn("csv");

        assertThat(service.exportCsv(StudentFilter.none(), sort)).isEqualTo("csv");
    }

    // ---------- update ----------

    @Test
    void update_appliesChangesAndFlushes() {
        Student existing = student(5L, "Old", "Name");
        when(studentRepository.findWithCoursesById(5L)).thenReturn(Optional.of(existing));
        when(studentRepository.existsByEmailIgnoreCaseAndIdNot("new@university.edu", 5L)).thenReturn(false);

        StudentDetailResponse result = service.update(5L, studentRequest("New", "Person", "NEW@university.edu"));

        assertThat(result.fullName()).isEqualTo("New Person");
        assertThat(result.email()).isEqualTo("new@university.edu");
        assertThat(existing.getDateOfBirth()).isEqualTo(LocalDate.of(2001, 3, 20));
        verify(studentRepository).flush();
    }

    @Test
    void update_rejectsEmailOwnedByAnotherStudent() {
        when(studentRepository.findWithCoursesById(5L)).thenReturn(Optional.of(student(5L, "Old", "Name")));
        when(studentRepository.existsByEmailIgnoreCaseAndIdNot("taken@university.edu", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, studentRequest("New", "Person", "taken@university.edu")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(studentRepository, never()).flush();
    }

    @Test
    void update_throwsNotFound_whenStudentIsMissing() {
        when(studentRepository.findWithCoursesById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, studentRequest("A", "B", "a@b.edu")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- delete ----------

    @Test
    void delete_removesStudent() {
        Student existing = student(3L, "Mia", "Nguyen");
        when(studentRepository.findById(3L)).thenReturn(Optional.of(existing));

        service.delete(3L);

        verify(studentRepository).delete(existing);
    }

    @Test
    void delete_throwsNotFound_andDeletesNothing() {
        when(studentRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(ResourceNotFoundException.class);
        verify(studentRepository, never()).delete(any(Student.class));
    }
}
