package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.CompTag;
import com.tbm.careerpathlearning.model.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface TrackRepository extends JpaRepository<Track, Long> {

    List<Track> findAllByIsDeletedIsFalse();

    List<Track> findAllByIsDeletedIsFalseAndTrackIgnoreCaseIn(Set<String> track);

    List<Track> findAllByIsDeletedIsFalseAndIdIn(Set<Long> ids);

//    List<CompTag> findAllByIsDeletedIsFalse();
//
//    @Query("SELECT t FROM CompTag t WHERE t.id IN :compTagIds AND t.isDeleted=false")
//    List<CompTag> findAllNotDeletedCompTagByIds(@Param("compTagIds") Set<Long> compTagIds);
//
//    @Query("SELECT t FROM CompTag t WHERE LOWER(t.tag) IN :compTags AND t.isDeleted=false")
//    List<CompTag> findAllByNotDeletedCompTagIn(@Param("compTags") Set<String> compTags);

}
