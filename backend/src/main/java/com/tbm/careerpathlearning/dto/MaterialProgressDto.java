package com.tbm.careerpathlearning.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class MaterialProgressDto {
    private Long materialId;
    private Double progress;
}