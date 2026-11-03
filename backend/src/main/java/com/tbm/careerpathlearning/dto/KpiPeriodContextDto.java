package com.tbm.careerpathlearning.dto;
import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import com.tbm.careerpathlearning.enums.KpiAssessmentModel;
import lombok.Data;
import java.time.LocalDate;
@Data
public class KpiPeriodContextDto {
    private Long id;
    private String name;
    private AnnualKpiReviewPeriodStatus status;
    private KpiAssessmentModel assessmentModel;
    private com.tbm.careerpathlearning.enums.KpiFrequencyPolicy frequencyPolicy;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate kpiSetupDeadline;
    private java.time.OffsetDateTime participantsSnapshottedAt;
    private ReviewPeriodEmployeeLevelConfigurationDto kpiAllocation;
}
