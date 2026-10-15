package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.TrainingJobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface TrainingJobRunRepository extends JpaRepository<TrainingJobRun, Long> {

    boolean existsByJobNameAndRunDate(String jobName, LocalDate runDate);
}
