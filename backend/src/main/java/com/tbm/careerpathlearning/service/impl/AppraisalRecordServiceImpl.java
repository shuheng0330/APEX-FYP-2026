package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.AppraisalReadinessDto;
import com.tbm.careerpathlearning.dto.AppraisalRecordDto;
import com.tbm.careerpathlearning.dto.HrAppraisalActionDto;
import com.tbm.careerpathlearning.enums.AppraisalCategory;
import com.tbm.careerpathlearning.enums.AppraisalDecisionType;
import com.tbm.careerpathlearning.enums.AppraisalStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.model.AppraisalRecord;
import com.tbm.careerpathlearning.model.EvaluationCycle;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.repository.AppraisalEvaluationRepository;
import com.tbm.careerpathlearning.repository.AppraisalRecordRepository;
import com.tbm.careerpathlearning.repository.EvaluationCycleRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.AppraisalRecordService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AppraisalRecordServiceImpl implements AppraisalRecordService {

    @Autowired
    private AppraisalRecordRepository appraisalRecordRepository;

    @Autowired
    private AppraisalEvaluationRepository appraisalEvaluationRepository;

    @Autowired
    private EvaluationCycleRepository evaluationCycleRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Override
    @Transactional
    public AppraisalRecordDto createOrUpdateDraft(AppraisalRecordDto dto, UUID userId) {
        validateDraftRequest(dto);

        Staff staff = getStaff(dto.getStaffId(), "Invalid Staff");
        Staff manager = getStaff(userId, "Invalid Manager");
        EvaluationCycle cycle = getEvaluationCycle(dto.getEvaluationCycleId());

        AppraisalRecord record = appraisalRecordRepository
                .findByStaff_IdAndEvaluationCycle_Id(staff.getId(), cycle.getId())
                .orElseGet(AppraisalRecord::new);

        if (record.getStatus() == AppraisalStatus.APPROVED) {
            throw new BadRequestException("Invalid Appraisal", "Approved appraisal records cannot be edited.");
        }

        boolean isNew = record.getId() == null;
        AppraisalReadinessDto readiness = calculateReadinessScore(
                staff.getId(),
                cycle.getId(),
                dto.getReviewPeriodYears()
        );

        record.setStaff(staff);
        record.setManager(manager);
        record.setEvaluationCycle(cycle);
        record.setReviewPeriodYears(dto.getReviewPeriodYears());
        record.setDecisionType(dto.getDecisionType());
        record.setManagerComment(dto.getManagerComment());
        record.setAiInsight(dto.getAiInsight());
        record.setStatus(AppraisalStatus.DRAFT);
        record.setUpdatedBy(userId);

        if (isNew) {
            record.setCreatedBy(userId);
        }

        applyCalculatedDecisionFields(record, dto, readiness);

        return mapToDto(appraisalRecordRepository.save(record));
    }

    @Override
    @Transactional
    public AppraisalRecordDto submit(UUID id, UUID userId) {
        AppraisalRecord record = getAppraisalRecord(id);

        if (record.getStatus() == AppraisalStatus.APPROVED) {
            throw new BadRequestException("Invalid Appraisal", "Approved appraisal records cannot be submitted again.");
        }

        if (isBlank(record.getManagerComment())) {
            throw new BadRequestException("Invalid Appraisal", "Manager comment is required before submitting to HR.");
        }

        record.setStatus(AppraisalStatus.PENDING_REVIEW);
        record.setSubmittedAt(LocalDateTime.now());
        record.setUpdatedBy(userId);
        return mapToDto(appraisalRecordRepository.save(record));
    }

    @Override
    public List<AppraisalRecordDto> getAllByStaffId(UUID staffId) {
        return appraisalRecordRepository.findAllByStaffIdOrderByCreatedAtDesc(staffId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<AppraisalRecordDto> getPendingRecords() {
        return appraisalRecordRepository.findAllByStatusWithDetails(AppraisalStatus.PENDING_REVIEW)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public AppraisalRecordDto approve(UUID id, UUID userId) {
        AppraisalRecord record = getAppraisalRecord(id);
        Staff hrReviewer = getStaff(userId, "Invalid HR Reviewer");

        record.setStatus(AppraisalStatus.APPROVED);
        record.setHrReviewer(hrReviewer);
        record.setApprovedAt(LocalDateTime.now());
        record.setUpdatedBy(userId);
        return mapToDto(appraisalRecordRepository.save(record));
    }

    @Override
    @Transactional
    public AppraisalRecordDto overrideAndApprove(UUID id, HrAppraisalActionDto dto, UUID userId) {
        if (dto == null || dto.getHrOverrideCategory() == null || isBlank(dto.getHrOverrideReason())) {
            throw new BadRequestException("Invalid Appraisal", "HR override category and reason are required.");
        }

        AppraisalRecord record = getAppraisalRecord(id);
        Staff hrReviewer = getStaff(userId, "Invalid HR Reviewer");

        record.setHrOverrideCategory(dto.getHrOverrideCategory());
        record.setHrOverrideReason(dto.getHrOverrideReason());
        applyHrOverrideCategory(record, dto.getHrOverrideCategory());
        record.setStatus(AppraisalStatus.APPROVED);
        record.setHrReviewer(hrReviewer);
        record.setApprovedAt(LocalDateTime.now());
        record.setUpdatedBy(userId);
        return mapToDto(appraisalRecordRepository.save(record));
    }

    @Override
    @Transactional
    public AppraisalRecordDto returnForRevision(UUID id, HrAppraisalActionDto dto, UUID userId) {
        if (dto == null || isBlank(dto.getHrReturnReason())) {
            throw new BadRequestException("Invalid Appraisal", "HR return reason is required.");
        }

        AppraisalRecord record = getAppraisalRecord(id);
        Staff hrReviewer = getStaff(userId, "Invalid HR Reviewer");

        record.setStatus(AppraisalStatus.RETURNED);
        record.setHrReviewer(hrReviewer);
        record.setHrReturnReason(dto.getHrReturnReason());
        record.setUpdatedBy(userId);
        return mapToDto(appraisalRecordRepository.save(record));
    }

    @Override
    public AppraisalReadinessDto calculateReadinessScore(UUID staffId, Long evaluationCycleId, Integer reviewPeriodYears) {
        if (reviewPeriodYears == null || (reviewPeriodYears != 1 && reviewPeriodYears != 3 && reviewPeriodYears != 5)) {
            throw new BadRequestException("Invalid Appraisal", "Review period must be 1, 3, or 5 years.");
        }

        EvaluationCycle cycle = getEvaluationCycle(evaluationCycleId);
        LocalDate endDate = cycle.getEndDate();
        LocalDate startDate = endDate.minusYears(reviewPeriodYears);

        Double averageScore = appraisalEvaluationRepository.averageOverallScoreForReviewPeriod(staffId, startDate, endDate);
        BigDecimal readinessScore = averageScore == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(averageScore).setScale(2, RoundingMode.HALF_UP);

        AppraisalReadinessDto dto = new AppraisalReadinessDto();
        dto.setReadinessScore(readinessScore);
        dto.setSystemCategory(toSystemCategory(readinessScore));
        return dto;
    }

    private void validateDraftRequest(AppraisalRecordDto dto) {
        if (dto == null) {
            throw new BadRequestException("Invalid Appraisal", "Appraisal request is required.");
        }
        if (dto.getStaffId() == null) {
            throw new BadRequestException("Invalid Appraisal", "Staff id is required.");
        }
        if (dto.getEvaluationCycleId() == null) {
            throw new BadRequestException("Invalid Appraisal", "Evaluation cycle id is required.");
        }
        if (dto.getDecisionType() == null) {
            throw new BadRequestException("Invalid Appraisal", "Decision type is required.");
        }
        if (dto.getReviewPeriodYears() == null) {
            dto.setReviewPeriodYears(1);
        }
        if (dto.getReviewPeriodYears() != 1 && dto.getReviewPeriodYears() != 3 && dto.getReviewPeriodYears() != 5) {
            throw new BadRequestException("Invalid Appraisal", "Review period must be 1, 3, or 5 years.");
        }
    }

    private Staff getStaff(UUID staffId, String title) {
        return staffRepository.findByIdAndIsDeletedFalse(staffId)
                .orElseThrow(() -> new DataAccessException(title, "Staff record could not be found."));
    }

    private EvaluationCycle getEvaluationCycle(Long evaluationCycleId) {
        return evaluationCycleRepository.findById(evaluationCycleId)
                .orElseThrow(() -> new DataAccessException("Invalid Evaluation Cycle", "Evaluation cycle could not be found."));
    }

    private AppraisalRecord getAppraisalRecord(UUID id) {
        return appraisalRecordRepository.findById(id)
                .orElseThrow(() -> new DataAccessException("Invalid Appraisal", "Appraisal record could not be found."));
    }

    private void applyCalculatedDecisionFields(AppraisalRecord record, AppraisalRecordDto dto, AppraisalReadinessDto readiness) {
        clearDecisionFields(record);

        if (record.getDecisionType() == AppraisalDecisionType.PROMOTION || record.getDecisionType() == AppraisalDecisionType.BOTH) {
            record.setPromotionReadinessScore(readiness.getReadinessScore());
            record.setPromotionSystemCategory(readiness.getSystemCategory());
            record.setPromotionFinalCategory(resolveFinalCategory(
                    readiness.getSystemCategory(),
                    dto.getPromotionFinalCategory(),
                    dto.getPromotionOverrideReason()
            ));
            record.setPromotionOverrideReason(dto.getPromotionOverrideReason());
        }

        if (record.getDecisionType() == AppraisalDecisionType.SALARY_INCREMENT || record.getDecisionType() == AppraisalDecisionType.BOTH) {
            record.setSalaryReadinessScore(readiness.getReadinessScore());
            record.setSalarySystemCategory(readiness.getSystemCategory());
            record.setSalaryFinalCategory(resolveFinalCategory(
                    readiness.getSystemCategory(),
                    dto.getSalaryFinalCategory(),
                    dto.getSalaryOverrideReason()
            ));
            record.setSalaryOverrideReason(dto.getSalaryOverrideReason());
        }
    }

    private void clearDecisionFields(AppraisalRecord record) {
        record.setPromotionReadinessScore(null);
        record.setPromotionSystemCategory(null);
        record.setPromotionFinalCategory(null);
        record.setPromotionOverrideReason(null);
        record.setSalaryReadinessScore(null);
        record.setSalarySystemCategory(null);
        record.setSalaryFinalCategory(null);
        record.setSalaryOverrideReason(null);
    }

    private AppraisalCategory resolveFinalCategory(
            AppraisalCategory systemCategory,
            AppraisalCategory requestedFinalCategory,
            String overrideReason
    ) {
        if (requestedFinalCategory == null || requestedFinalCategory == systemCategory) {
            return systemCategory;
        }
        if (isBlank(overrideReason)) {
            throw new BadRequestException("Invalid Appraisal", "Override reason is required when changing the system category.");
        }
        return requestedFinalCategory;
    }

    private void applyHrOverrideCategory(AppraisalRecord record, AppraisalCategory overrideCategory) {
        if (record.getDecisionType() == AppraisalDecisionType.PROMOTION || record.getDecisionType() == AppraisalDecisionType.BOTH) {
            record.setPromotionFinalCategory(overrideCategory);
        }

        if (record.getDecisionType() == AppraisalDecisionType.SALARY_INCREMENT || record.getDecisionType() == AppraisalDecisionType.BOTH) {
            record.setSalaryFinalCategory(overrideCategory);
        }
    }

    private AppraisalCategory toSystemCategory(BigDecimal readinessScore) {
        if (readinessScore.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return AppraisalCategory.READY;
        }
        if (readinessScore.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return AppraisalCategory.BORDERLINE;
        }
        return AppraisalCategory.NEEDS_IMPROVEMENT;
    }

    private AppraisalRecordDto mapToDto(AppraisalRecord record) {
        AppraisalRecordDto dto = new AppraisalRecordDto();
        dto.setId(record.getId());
        dto.setStaffId(record.getStaff().getId());
        dto.setStaffName(record.getStaff().getName());
        dto.setManagerId(record.getManager().getId());
        dto.setManagerName(record.getManager().getName());
        dto.setEvaluationCycleId(record.getEvaluationCycle().getId());
        if (record.getEvaluationCycle().getEndDate() != null) {
            dto.setEvaluationCycleEndDate(record.getEvaluationCycle().getEndDate().toString());
        }
        dto.setReviewPeriodYears(record.getReviewPeriodYears());
        dto.setDecisionType(record.getDecisionType());
        dto.setPromotionReadinessScore(record.getPromotionReadinessScore());
        dto.setSalaryReadinessScore(record.getSalaryReadinessScore());
        dto.setPromotionSystemCategory(record.getPromotionSystemCategory());
        dto.setSalarySystemCategory(record.getSalarySystemCategory());
        dto.setPromotionFinalCategory(record.getPromotionFinalCategory());
        dto.setSalaryFinalCategory(record.getSalaryFinalCategory());
        dto.setPromotionOverrideReason(record.getPromotionOverrideReason());
        dto.setSalaryOverrideReason(record.getSalaryOverrideReason());
        dto.setManagerComment(record.getManagerComment());
        dto.setAiInsight(record.getAiInsight());
        dto.setStatus(record.getStatus());
        if (record.getHrReviewer() != null) {
            dto.setHrReviewerId(record.getHrReviewer().getId());
            dto.setHrReviewerName(record.getHrReviewer().getName());
        }
        dto.setHrOverrideCategory(record.getHrOverrideCategory());
        dto.setHrOverrideReason(record.getHrOverrideReason());
        dto.setHrReturnReason(record.getHrReturnReason());
        dto.setSubmittedAt(record.getSubmittedAt());
        dto.setApprovedAt(record.getApprovedAt());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        dto.setCreatedBy(record.getCreatedBy());
        dto.setUpdatedBy(record.getUpdatedBy());
        return dto;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
