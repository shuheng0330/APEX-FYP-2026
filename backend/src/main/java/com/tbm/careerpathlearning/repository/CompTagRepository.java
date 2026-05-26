package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.CompTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CompTagRepository extends JpaRepository<CompTag, Long> {

    List<CompTag> findAllByIsDeletedIsFalse();

    List<CompTag> findAllByIsDeletedIsFalseAndIdIn(Set<Long> ids);

    //    @Query("SELECT t FROM CompTag t WHERE LOWER(t.tag) IN :compTags AND t.isDeleted=false")
    List<CompTag> findAllByIsDeletedIsFalseAndTagIgnoreCaseIn(Set<String> tags);

    Optional<CompTag> findByTag(String tag);
}
