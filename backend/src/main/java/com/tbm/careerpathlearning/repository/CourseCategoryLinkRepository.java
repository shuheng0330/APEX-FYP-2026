package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.CourseCategoryLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CourseCategoryLinkRepository extends JpaRepository<CourseCategoryLink, Long> {

    List<CourseCategoryLink> findAllByCourseKey(Long courseKey);

    List<CourseCategoryLink> findAllByCourseKeyIn(Collection<Long> courseKeys);

    List<CourseCategoryLink> findAllByCategoryId(Long categoryId);
}
