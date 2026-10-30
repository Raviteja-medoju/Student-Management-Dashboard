package com.example.studentmgmt.repository;

import com.example.studentmgmt.entity.Course;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    List<Course> findAllByOrderByCodeAsc();

    @Query("""
            select c from Course c
            where lower(c.code) like lower(concat('%', :term, '%'))
               or lower(c.title) like lower(concat('%', :term, '%'))
            order by c.code
            """)
    List<Course> search(@Param("term") String term);

    /** Loads a course together with enrollments and their students in one query (no N+1). */
    @EntityGraph(attributePaths = {"enrollments", "enrollments.student"})
    Optional<Course> findWithStudentsById(Long id);

    /**
     * Locks the given course rows for the rest of the transaction, so two concurrent
     * enrollments cannot both take the last seat of a course.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id in :ids")
    List<Course> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
