package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.*;

@Data
public class KpiCentralResultDto {
    private Long id;
    private Long reviewPeriodId;
    private String reviewPeriodName;
    private AnnualKpiReviewPeriodStatus reviewPeriodStatus;
    private Long checkpointId;
    private ReviewFrequency reviewFrequency;
    private Long planVersionId;
    private int planVersionNumber;
    private Long departmentId;
    private String departmentName;
    private KpiLevel level;
    private int editionNumber;
    private KpiCentralResultStatus status;
    private Long previousEditionId;
    private Long workingCorrectionId;
    private String correctionReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private OffsetDateTime publishedAt;
    private UUID publishedBy;
    private long lockVersion;
    private List<Item> items=new ArrayList<>();
    private List<String> publicationBlockers=new ArrayList<>();
    private List<String> allowedActions=new ArrayList<>();
    @Data public static class Item {
        private Long id;
        private KpiItemDto kpi;
        private String actualAchievement;
        private Integer point;
        private String comment;
        private List<KpiCentralResultEvidenceDto> evidence=new ArrayList<>();
    }
}
