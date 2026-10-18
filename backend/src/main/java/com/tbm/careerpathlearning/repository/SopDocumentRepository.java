package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.SopDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SopDocumentRepository extends JpaRepository<SopDocument, Long> {
    List<SopDocument> findAllByIsDeletedFalseOrderByCreatedAtDesc();

    /** Every version of a course, oldest first (FR-07-16); the first version also matches by its own id. */
    @Query("select d from SopDocument d where (d.courseId = :courseId or d.id = :courseId) and d.isDeleted = false "
            + "order by d.versionNo asc")
    List<SopDocument> findVersions(@Param("courseId") Long courseId);
}
