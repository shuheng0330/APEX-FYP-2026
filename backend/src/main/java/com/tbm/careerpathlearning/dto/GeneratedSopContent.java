package com.tbm.careerpathlearning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Shape of the JSON Gemini returns for one SOP module (FR-08-04).
 * Content is structured into typed sections so the UI renders each differently:
 * paragraphs, numbered steps, reference tables, safety-warning cards, key-term cards.
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
        private String summary;
        private List<String> learningObjectives = new ArrayList<>();
        private List<String> tools = new ArrayList<>();
        private List<ContentSection> sections = new ArrayList<>();
        private List<KeyTerm> keyTerms = new ArrayList<>();
        private List<GeneratedQuestion> quiz = new ArrayList<>();
    }

    /** A typed content block — type is one of: paragraph | steps | table | warnings */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentSection {
        private String type;
        private String heading;
        private String body;
        private List<String> items = new ArrayList<>();
        private List<String> headers = new ArrayList<>();
        private List<List<String>> rows = new ArrayList<>();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KeyTerm {
        private String term;
        private String definition;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeneratedQuestion {
        /** MULTIPLE_CHOICE | TRUE_FALSE */
        private String type;
        private String question;
        private List<String> options = new ArrayList<>();
        private String answer;
        private String explanation;
    }
}
