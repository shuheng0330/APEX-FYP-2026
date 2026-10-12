package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** The learner's Learning Portal: their courses and the Open courses they can still join (FR-11-05, FR-11-07). */
@Data
public class SopLearningHomeDto {
    private List<SopLearningCourseDto> courses = new ArrayList<>();
    private List<SopLearningCourseDto> openCourses = new ArrayList<>();
}
