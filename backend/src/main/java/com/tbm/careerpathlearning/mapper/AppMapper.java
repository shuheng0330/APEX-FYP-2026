package com.tbm.careerpathlearning.mapper;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.model.*;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AppMapper {

    // === Learning Material ===
    LearningMaterialDto toDto(LearningMaterial entity);

    @Mapping(target = "departments", ignore = true)
    LearningMaterial toEntity(LearningMaterialDto dto);

    @Mapping(target = "id", source = "orgChart.id")
    @Mapping(target = "name", source = "orgChart.name")
    OrgChartDto mapOrgChartJoinToDto(LearningMaterialOrgChart joinEntity);

    // === Learning Document ===
    LearningDocumentDto toDto(LearningDocument entity);

    LearningDocument toEntity(LearningDocumentDto dto);

    // === Training Program ===
    TrainingProgramDTO toDto(TrainingProgram entity);

    TrainingProgram toEntity(TrainingProgramDTO dto);

    // === Training Invitation ===
    TrainingInvitationDTO toDto(TrainingInvitation entity);

    TrainingInvitation toEntity(TrainingInvitationDTO dto);

    // === Training Registration ===
    TrainingRegistrationDTO toDto(TrainingRegistration entity);

    TrainingRegistration toEntity(TrainingRegistrationDTO dto);

    // === Training Attendance ===
    TrainingAttendanceDto toDto(TrainingAttendance entity);

    TrainingAttendance toEntity(TrainingAttendanceDto dto);

    // === Staff Learning Material===
    StaffLearningMaterialDto toDto(StaffLearningMaterial entity);

    StaffLearningMaterial toEntity(StaffLearningMaterialDto dto);

    // === EvaluationCyle===
    EvaluationCycleDto toDto(EvaluationCycle entity);
    EvaluationCycle toEntity(EvaluationCycleDto dto);

    // === Staff Learning Document===
    @Mapping(target = "enrollmentId", source = "staffLearningMaterial.enrollmentId")
    @Mapping(target = "documentId", source = "learningDocument.documentId")
    StaffLearningDocumentProgressDto toDto(StaffLearningDocumentProgress entity);

    @Mapping(target = "staffLearningMaterial.enrollmentId", source = "enrollmentId")
    @Mapping(target = "learningDocument.documentId", source = "documentId")
    StaffLearningDocumentProgress toEntity(StaffLearningDocumentProgressDto dto);

    // === Staff ===
    StaffDto toDto(Staff entity);

    Staff toEntity(StaffDto dto);

    // === Staff Login Audit ===
    StaffLoginAuditDto toDto(StaffLoginAudit entity);

    StaffLoginAudit toEntity(StaffLoginAuditDto dto);

    // === Staff Refresh Token ===
    StaffRefreshTokenDto toDto(StaffRefreshToken entity);

    StaffRefreshToken toEntity(StaffRefreshTokenDto dto);

    // === Staff Otp ===
    StaffOtpDto toDto(StaffOtp entity);

    StaffOtp toEntity(StaffOtpDto dto);

    // === RoleCompetencies ===
    RoleCompetencyDto toDto(RoleCompetency entity);

    RoleCompetency toEntity(RoleCompetencyDto dto);

    EvaluationRatingsDto toDto(EvaluationRatings entity);

    EvaluationRatings toEntity(EvaluationRatingsDto dto);

    CompetencyDto toDto(Competency entity);

    Competency toEntity(CompetencyDto dto);

    // === Proposal ===
    ProposalDto toDto(Proposal entity);

    Proposal toEntity(ProposalDto dto);

    // === Proposal Participant ===
    ProposalParticipantDto toDto(ProposalParticipant entity);

    ProposalParticipant toEntity(ProposalParticipantDto dto);

    // === Competency Proposal ===
    CompetencyProposalDto toDto(CompetencyProposal entity);

    CompetencyProposal toEntity(CompetencyProposalDto dto);

    // === Competency Comp Tag Proposal ===
    CompetencyCompTagProposalDto toDto(CompetencyCompTagProposal entity);

    CompetencyCompTagProposal toEntity(CompetencyCompTagProposalDto dto);

    // === Role Competency Proposal ===
    RoleCompetencyProposalDto toDto(RoleCompetencyProposal entity);

    RoleCompetencyProposal toEntity(RoleCompetencyProposalDto dto);

    // === Role Job Scope Proposal ===
    RoleJobScopeProposalDto toDto(RoleJobScopeProposal entity);

    RoleJobScopeProposal toEntity(RoleJobScopeProposalDto dto);

    // === Role Competency Item ===
    RoleCompetencyItemDto toDto(RoleCompetencyItem entity);

    RoleCompetencyItem toEntity(RoleCompetencyItemDto dto);

    // === Role Competency Proposal Item ===
    RoleCompetencyProposalItemDto toDto(RoleCompetencyProposalItem entity);

    RoleCompetencyProposalItem toEntity(RoleCompetencyProposalItemDto dto);

    // === Org Chart ===
    OrgChartDto toDto(OrgChart entity);

    OrgChart toEntity(OrgChartDto dto);

    // === Parent Child Node ===
    ParentChildNodeDto toDto(ParentChildNode entity);

    ParentChildNode toEntity(ParentChildNodeDto dto);

    // === Role ===
    RoleDto toDto(Role entity);

    Role toEntity(RoleDto entity);

    // === Career Pathway ===
    CareerPathwayDto toDto(CareerPathway entity);

    CareerPathway toEntity(CareerPathwayDto dto);

    // === Career Pathway Role ===
    CareerPathwayRoleDto toDto(CareerPathwayRole entity);

    CareerPathwayRole toEntity(CareerPathwayRoleDto dto);

    // === Track ===
    TrackDto toDto(Track entity);

    Track toEntity(TrackDto dto);

    // === Career Pathway Track ===
    CareerPathwayTrackDto toDto(CareerPathwayTrack entity);

    CareerPathwayTrack toEntity(CareerPathwayTrackDto dto);

    // === Staff Profile ===
    StaffProfileDto toDto(StaffProfile entity);

    StaffProfile toEntity(StaffProfileDto dto);

    // == Staff Self Declared Skill ===
    StaffSelfDeclaredSkillDto toDto(StaffSelfDeclaredSkill entity);

    StaffSelfDeclaredSkill toEntity(StaffSelfDeclaredSkillDto dto);

    // == Staff Cert ===
    StaffCertDto toDto(StaffCert entity);

    StaffCert toEntity(StaffCertDto dto);

    // === Competency Comp Tag ===
    CompetencyCompTagDto toDto(CompetencyCompTag entity);

    CompetencyCompTag toEntity(CompetencyCompTagDto dto);

    // === Comp Tag ===
    CompTagDto toDto(CompTag entity);

    CompTag toEntity(CompTagDto dto);

    // === job scope ===
    JobScopeDto toDto(JobScope entity);

    JobScope toEntity(JobScopeDto dto);

    // === Role Job Scope ===
    RoleJobScopeDto toDto(RoleJobScope entity);

    RoleJobScope toEntity(RoleJobScopeDto dto);

    // === Role Authority ===
    RoleAuthorityDto toDto(RoleAuthority entity);

    RoleAuthority toEntity(RoleAuthorityDto dto);

    // === Authority ===
    AuthorityDto toDto(Authority entity);

    Authority toEntity(AuthorityDto dto);

    @AfterMapping
    default void setParentReference(@MappingTarget LearningMaterial learningMaterial) {
        if (learningMaterial.getLearningDocuments() != null) {
            learningMaterial.getLearningDocuments().forEach(doc -> doc.setLearningMaterial(learningMaterial));
        }
    }

    // Update existing entity from DTO (only overwrites mapped fields)
    void updateEntityFromDto(LearningDocumentDto dto, @MappingTarget LearningDocument entity);
}
