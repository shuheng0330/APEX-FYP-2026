package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** An SOP document with its generated modules (and each module's quiz). */
@Data
public class SopDetailDto {
    private SopDocumentDto document;
    private List<SopModuleDto> modules = new ArrayList<>();
}
