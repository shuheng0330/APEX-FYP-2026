package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.AnnualAppraisalActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnnualAppraisalActivityRepository extends JpaRepository<AnnualAppraisalActivity,Long> {
    List<AnnualAppraisalActivity> findAllByAppraisalIdOrderByIdAsc(Long appraisalId);
}
