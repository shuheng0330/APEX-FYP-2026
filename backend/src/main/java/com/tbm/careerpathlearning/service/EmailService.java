package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.ProposalDto;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public interface EmailService {

    @Async
    void sendPasswordResetEmail(String to, String otp, Locale locale);

    @Async
    void sendAccountRegisteredEmail(String to, Locale locale);

    @Async
    void sendAccountActivationEmail(String to, String otp, Locale locale);

    @Async
    void sendCompetencyProposalProposeInvitationEmail(
            String recipient,
            ProposalDto proposalDto,
            String competencyName,
            String competencyDescription,
            UUID staffId,
            List<String> compTag,
            Locale locale);

    @Async
    void sendCompetencyProposalReviewInvitationEmail(
            String recipient,
            ProposalDto proposalDto,
            String competencyName,
            String competencyDescription,
            UUID staffId,
            List<String> compTag,
            Locale locale);

    @Async
    void sendCompetencyProposalUpdateEmail(
            String recipient,
            ProposalDto proposalDto,
            String competencyName,
            String competencyDescription,
            List<String> compTag,
            String updatedBy,
            UUID staffId,
            Locale locale);

    @Async
    void sendCompetencyProposalRejectedEmail(
            String recipient,
            String competencyName,
            String competencyDescription,
            List<String> compTag,
            String updatedBy,
            Locale locale);

    @Async
    void sendCompetencyProposalApprovedEmail(
            String recipient,
            String competencyName,
            String competencyDescription,
            List<String> compTag,
            String updatedBy,
            Locale locale);

    @Async
    void sendCompetencyAssignmentProposalReviewInvitationEmail(
            String recipient,
            ProposalDto proposalDto,
            String departmentName,
            String roleName,
            String roleDescription,
            List<String> jobScopeList,
            Map<String, Integer> competencyMap,
            UUID staffId,
            Locale locale);

    @Async
    void sendCompetencyAssignmentProposalProposeInvitationEmail(
            String recipient,
            ProposalDto proposalDto,
            String departmentName,
            String roleName,
            String roleDescription,
            List<String> jobScopeList,
            Map<String, Integer> competencyMap,
            UUID staffId,
            Locale locale);

    @Async
    void sendCompetencyAssignmentProposalUpdateEmail(
            String recipient,
            ProposalDto proposalDto,
            String departmentName,
            String roleName,
            String roleDescription,
            List<String> jobScopeList,
            Map<String, Integer> competencyMap,
            String updatedBy,
            UUID staffId,
            Locale locale);

    @Async
    void sendCompetencyAssignmentProposalRejectedEmail(
            String recipient,
            String departmentName,
            String roleName,
            String description,
            List<String> jobScopes,
            Map<String, Integer> competencyMap,
            String updatedBy,
            Locale locale);

    @Async
    void sendCompetencyAssignmentProposalApprovedEmail(
            String recipient,
            String departmentName,
            String roleName,
            String description,
            List<String> jobScopes,
            Map<String, Integer> competencyMap,
            String updatedBy,
            Locale locale);
}


