package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CourseCategoryChipDto;
import com.tbm.careerpathlearning.dto.CourseCategoryDto;
import com.tbm.careerpathlearning.dto.CourseCategoryRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Course categories (FR-19). Staff with Trainer access, managers, HR and the Super Admin create them; a custom one is
 * changed or deleted by its creator, HR or the Super Admin; the defaults are changed and retired by HR and the Super
 * Admin; a course's own categories are set by its owner (decision D, 29 Oct 2026).
 */
public interface CourseCategoryService {

    /** Who is acting: HR or the Super Admin manage every category; others may create when allowed. */
    record Viewer(UUID userId, boolean admin, boolean canCreate) {
    }

    /** Every category, retired ones too, by name with General first. */
    List<CourseCategoryDto> list(Viewer viewer);

    CourseCategoryDto create(CourseCategoryRequest request, Viewer viewer);

    /** Rename and recolour (FR-19-02); General keeps its name. */
    CourseCategoryDto update(Long categoryId, CourseCategoryRequest request, Viewer viewer);

    /** FR-19-03: a default other than General, hidden from selection and kept on the courses that have it. */
    CourseCategoryDto retire(Long categoryId, Viewer viewer);

    CourseCategoryDto restore(Long categoryId, Viewer viewer);

    /** FR-19-04: a custom category, taken off every course; a course left with none moves to General. */
    void delete(Long categoryId, Viewer viewer);

    /** A course's categories, primary first; General when it has none. */
    List<CourseCategoryChipDto> ofCourse(Long courseKey);

    Map<Long, List<CourseCategoryChipDto>> ofCourses(Collection<Long> courseKeys);

    /** FR-19-05: sets a course's categories, at least one, one of them primary; by the course's owner. */
    List<CourseCategoryChipDto> setCourse(Long documentId, CourseCategoryRequest request, SopViewer viewer);
}
