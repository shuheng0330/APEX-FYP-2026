package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CheckInResponseDto {
    private boolean success;
    private String message;
    private double distance; // optional
}
