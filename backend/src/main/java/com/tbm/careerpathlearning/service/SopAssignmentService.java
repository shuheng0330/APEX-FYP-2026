package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.SopAssignableCourseDto;
import com.tbm.careerpathlearning.dto.SopAssignmentDto;
import com.tbm.careerpathlearning.dto.SopAssignmentPreviewDto;
import com.tbm.careerpathlearning.dto.SopAssignmentRequest;
import com.tbm.careerpathlearning.dto.SopAssignmentResultDto;
import com.tbm.careerpathlearning.dto.SopAudienceDto;
import com.tbm.careerpathlearning.enums.SopVisibility;

import java.util.List;
import java.util.UUID;

/**
 * Assigning SOP courses to staff (FR-09). {@code canAssignAny} is true for users holding
 * CAN_ASSIGN_TRAINING (HR, Super Admin); other trainers may only assign courses they uploaded (FR-09-07).
 */
public interface SopAssignmentService {

    /** Generated SOP courses the user may assign, with whether each is ready to assign. */
    List<SopAssignableCourseDto> listCourses(UUID userId, boolean canAssignAny);

    /** Departments, job roles and staff available as an audience. */
    SopAudienceDto getAudience();

    /** Who an assignment would reach, without saving anything. */
    SopAssignmentPreviewDto preview(SopAssignmentRequest request);

    SopAssignmentResultDto assign(SopAssignmentRequest request, UUID userId, boolean canAssignAny);

    /** Assignments of one course, or of every course the user may assign when sopDocumentId is null (FR-09-08). */
    List<SopAssignmentDto> history(Long sopDocumentId, UUID userId, boolean canAssignAny);

    /** Toggles a course between Private and Open (FR-09-04). */
    SopAssignableCourseDto setVisibility(Long sopDocumentId, SopVisibility visibility, UUID userId, boolean canAssignAny);
}
