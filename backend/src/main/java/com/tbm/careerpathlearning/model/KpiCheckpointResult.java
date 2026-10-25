package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.ReviewFrequency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Getter @Setter @Table(name="kpi_checkpoint_result")
public class KpiCheckpointResult {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long participantId;
    @Column(nullable=false,updatable=false) private Long reviewPeriodId;
    @Column(nullable=false,updatable=false) private Long checkpointId;
    @Column(nullable=false,updatable=false) private Long companyCheckpointId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=20) private ReviewFrequency reviewFrequency;
    private Long companyVersionId;
    private Long departmentVersionId;
    private Long individualVersionId;
    private Long companyResultId;
    private Long departmentResultId;
    @Column(precision=7,scale=4) private BigDecimal score;
    private OffsetDateTime calculatedAt;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(nullable=false,columnDefinition="jsonb")
    private List<com.tbm.careerpathlearning.dto.KpiScoreCalculationDto> calculationHistory=new ArrayList<>();
    @Version private long lockVersion;
}
