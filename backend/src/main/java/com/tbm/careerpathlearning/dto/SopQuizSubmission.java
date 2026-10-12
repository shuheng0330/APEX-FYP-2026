package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/** A learner's answers: question id to the option chosen or the text typed. */
@Data
public class SopQuizSubmission {
    private Map<Long, String> answers = new HashMap<>();
}
