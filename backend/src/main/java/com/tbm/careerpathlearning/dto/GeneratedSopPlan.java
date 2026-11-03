package com.tbm.careerpathlearning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase A output: the module plan for an SOP. Each planned module names the SOP
 * sections/points it must fully cover, so Phase B can expand them one-by-one
 * without dropping any content (FR-08-04).
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneratedSopPlan {

    private List<PlannedModule> modules = new ArrayList<>();

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlannedModule {
        private String title;
        /** Which SOP sections/steps/rules this module must completely include. */
        private String covers;
    }
}
