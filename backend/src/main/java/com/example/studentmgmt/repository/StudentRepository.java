package com.example.studentmgmt.repository;

import com.example.studentmgmt.entity.Student;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/** Filtering and search go through {@link StudentSpecifications} (JpaSpecificationExecutor). */
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Loads a student together with enrollments and their courses in one query (no N+1). */
    @EntityGraph(attributePaths = {"enrollments", "enrollments.course"})
    Optional<Student> findWithCoursesById(Long id);

    List<Student> findTop5ByOrderByCreatedAtDescIdDesc();
}
