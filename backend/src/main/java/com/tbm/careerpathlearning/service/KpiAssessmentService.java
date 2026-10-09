package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.dto.*;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
public interface KpiAssessmentService {
    List<KpiPeriodContextDto> periods(UUID actor);
    List<KpiAssessmentCheckpointDto> checkpoints(Long periodId,UUID actor);
    KpiAssessmentDto mine(Long checkpointId,UUID actor);
    KpiAssessmentDto get(Long id,UUID actor);
    KpiAssessmentDto create(KpiAssessmentRequest request,UUID actor);
    KpiAssessmentDto update(Long id,KpiAssessmentRequest request,UUID actor);
    KpiAssessmentDto submit(Long id,UUID actor);
    List<KpiAssessmentReviewDto> reviews(Long reviewPeriodId,com.tbm.careerpathlearning.enums.KpiAssessmentStatus status,UUID actor);
    KpiAssessmentDto saveSuperiorDraft(Long id,KpiSuperiorAssessmentRequest request,UUID actor);
    KpiAssessmentDto completeReview(Long id,UUID actor);
    KpiAssessmentEvidenceDto upload(Long itemId,MultipartFile file,UUID actor);
    List<KpiAssessmentEvidenceDto> evidence(Long itemId,UUID actor);
    record Download(Resource resource,KpiAssessmentEvidenceDto metadata) {}
    Download download(Long evidenceId,UUID actor);
    void deleteEvidence(Long evidenceId,UUID actor);
}
