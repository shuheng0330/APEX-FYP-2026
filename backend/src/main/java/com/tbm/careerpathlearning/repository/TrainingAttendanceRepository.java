package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.TrainingAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrainingAttendanceRepository extends JpaRepository<TrainingAttendance, Long> {

    boolean existsByTrainingId(Long trainingId);

    List<TrainingAttendance> findAllByTrainingId(Long trainingId);

    /** A person's attendance at every session, for their training record (FR-18-01). */
    List<TrainingAttendance> findAllByStaffId(UUID staffId);

    /** The attendance at these sessions, for the staff sharing figures (FR-15-02). */
    List<TrainingAttendance> findAllByTrainingIdIn(Collection<Long> trainingIds);

    /** A person's attendance at these sessions, for their own view of each (FR-14-04). */
    List<TrainingAttendance> findAllByStaffIdAndTrainingIdIn(UUID staffId, Collection<Long> trainingIds);

    /** Which of these sessions have attendance recorded. */
    @Query("select distinct a.trainingId from TrainingAttendance a where a.trainingId in :trainingIds")
    List<Long> findRecordedTrainingIds(@Param("trainingIds") Collection<Long> trainingIds);
}
