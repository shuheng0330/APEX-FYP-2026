package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrgWideScoreDistributionDto {
    private String range;
    private Long count;
    private List<String> staffNames;
}
