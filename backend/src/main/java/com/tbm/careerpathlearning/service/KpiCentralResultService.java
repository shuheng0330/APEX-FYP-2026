package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;

public interface KpiCentralResultService {
    List<KpiPeriodContextDto> periods(UUID actor);
    List<KpiCentralResultOptionDto> options(Long periodId,UUID actor);
    List<AnnualKpiReviewPeriodDto.Checkpoint> checkpoints(Long versionId,UUID actor);
    List<KpiCentralResultDto> list(Long periodId,UUID actor);
    KpiCentralResultDto get(Long id,UUID actor);
    KpiCentralResultDto create(KpiCentralResultRequest request,UUID actor);
    KpiCentralResultDto update(Long id,KpiCentralResultRequest request,UUID actor);
    KpiCentralResultDto publish(Long id,KpiCentralResultDecisionRequest request,UUID actor);
    KpiCentralResultDto correct(Long id,KpiCentralResultDecisionRequest request,UUID actor);
    List<KpiCentralResultDto> history(Long id,UUID actor);
    KpiCentralResultEvidenceMutationDto upload(Long itemId,Long expectedLockVersion,MultipartFile file,UUID actor);
    List<KpiCentralResultEvidenceDto> evidence(Long itemId,UUID actor);
    record Download(Resource resource,KpiCentralResultEvidenceDto metadata) {}
    Download download(Long evidenceId,UUID actor);
    KpiCentralResultEvidenceMutationDto deleteEvidence(Long evidenceId,Long expectedLockVersion,UUID actor);
    List<CompanyKpiResultDelegateDto> delegates(Long periodId,UUID actor);
    CompanyKpiResultDelegateDto assignDelegate(Long periodId,UUID staffId,UUID actor);
    CompanyKpiResultDelegateDto revokeDelegate(Long periodId,Long delegateId,UUID actor);
}
