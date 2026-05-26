package com.tbm.careerpathlearning.dto;

import lombok.Data;

@Data
public class RatingDTO {
    private Long compId;
    private String competencyName;
    private int rating;
}
