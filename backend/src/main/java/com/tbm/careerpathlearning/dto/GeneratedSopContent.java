package com.tbm.careerpathlearning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Shape of the JSON the Gemini model is asked to return for an SOP (FR-08-04).
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneratedSopContent {

    private List<GeneratedModule> modules = new ArrayList<>();

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeneratedModule {
        private String title;
        private String content;
        private List<GeneratedQuestion> quiz = new ArrayList<>();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeneratedQuestion {
        /** MULTIPLE_CHOICE | TRUE_FALSE | FILL_IN_THE_BLANK */
        private String type;
        private String question;
        private List<String> options = new ArrayList<>();
        private String answer;
        private String explanation;
    }
}
