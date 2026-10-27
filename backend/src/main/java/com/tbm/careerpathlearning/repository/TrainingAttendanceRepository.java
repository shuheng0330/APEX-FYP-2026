package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.TrainingAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TrainingAttendanceRepository extends JpaRepository<TrainingAttendance, Long> {

    boolean existsByTrainingId(Long trainingId);

    /** Which of these sessions have attendance recorded. */
    @Query("select distinct a.trainingId from TrainingAttendance a where a.trainingId in :trainingIds")
    List<Long> findRecordedTrainingIds(@Param("trainingIds") Collection<Long> trainingIds);
}
