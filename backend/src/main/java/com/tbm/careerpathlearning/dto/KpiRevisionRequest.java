package com.tbm.careerpathlearning.dto;
import lombok.Data;
import java.util.List;
@Data public class KpiRevisionRequest {
    private Long expectedLockVersion;
    private String reason;
    private com.tbm.careerpathlearning.enums.KpiRevisionPreparationMode preparationMode;
    private List<KpiItemDto> items;
}
