package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.model.EmployeeInsightSubmissionLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

@MetadataOnlyLogging
public interface EmployeeInsightSubmissionLinkRepository extends JpaRepository<EmployeeInsightSubmissionLink, Long> {
    Optional<EmployeeInsightSubmissionLink> findBySubmissionActivityId(Long submissionActivityId);
    List<EmployeeInsightSubmissionLink> findAllByAppraisalIdOrderBySubmissionActivityIdDesc(Long appraisalId);
}
