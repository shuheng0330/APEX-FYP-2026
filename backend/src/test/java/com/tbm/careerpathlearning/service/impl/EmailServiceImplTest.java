package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.ProposalDto;
import com.tbm.careerpathlearning.enums.ProposalStatus;
import com.tbm.careerpathlearning.exception.HazardException;
import com.tbm.careerpathlearning.service.ValidationService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ValidationService validationService;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailServiceImpl emailService;

    private final String RECIPIENT = "test@example.com";
    private final Locale LOCALE = Locale.ENGLISH;
    private final UUID STAFF_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender);

        ReflectionTestUtils.setField(emailService, "FRONTEND_ORIGIN", "http://localhost:3000");
        ReflectionTestUtils.setField(emailService, "NO_REPLY", "no-reply@test.com");
        ReflectionTestUtils.setField(emailService, "SERVER_NAME", "Test Server");

        ReflectionTestUtils.setField(emailService, "messageSource", messageSource);
        ReflectionTestUtils.setField(emailService, "validationService", validationService);

        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendPasswordResetEmail_Success() {
        String otp = "123456";
        emailService.sendPasswordResetEmail(RECIPIENT, otp, LOCALE);

        verify(messageSource).getMessage(eq("email.reset.title"), any(), eq(LOCALE));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendAccountRegisteredEmail_Success() {
        emailService.sendAccountRegisteredEmail(RECIPIENT, LOCALE);

        verify(messageSource).getMessage(eq("email.register.title"), any(), eq(LOCALE));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendAccountActivationEmail_Success() {
        emailService.sendAccountActivationEmail(RECIPIENT, "999999", LOCALE);

        verify(messageSource).getMessage(eq("email.activation.title"), any(), eq(LOCALE));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendEmail_WhenMailServerFails_ShouldThrowHazardException() {
        // Arrange
        doThrow(new MailSendException("Server down")).when(mailSender).send(any(MimeMessage.class));

        // Act & Assert
        assertThrows(HazardException.class, () ->
                emailService.sendAccountRegisteredEmail(RECIPIENT, LOCALE));
    }

    @Test
    void sendCompetencyProposalProposeInvitationEmail_Success() {
        ProposalDto proposal = new ProposalDto();
        proposal.setId(100L);
        proposal.setStatus(ProposalStatus.ONGOING);

        List<String> tags = List.of("Java", "Spring");

        emailService.sendCompetencyProposalProposeInvitationEmail(
                RECIPIENT, proposal, "Competency A", "Description", STAFF_ID, tags, LOCALE
        );

        verify(messageSource).getMessage(eq("email.competency.proposer.invite.title"), any(), eq(LOCALE));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendCompetencyAssignmentProposalReviewInvitationEmail_EmptyLists() {
        ProposalDto proposal = new ProposalDto();
        proposal.setId(200L);
        proposal.setStatus(ProposalStatus.ONGOING);

        when(validationService.isNullOrBlank(anyString())).thenReturn(true); // Simulate blank description

        emailService.sendCompetencyAssignmentProposalReviewInvitationEmail(
                RECIPIENT, proposal, "IT", "Dev", "",
                Collections.emptyList(), Collections.emptyMap(),
                STAFF_ID, LOCALE
        );

        // Verify flow completes without error despite empty lists
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendCompetencyProposalRejectedEmail_Success() {
        emailService.sendCompetencyProposalRejectedEmail(
                RECIPIENT, "Comp A", "Desc", List.of("Tag1"), "Admin", LOCALE
        );

        verify(messageSource).getMessage(eq("email.competency.reject.title"), any(), eq(LOCALE));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendCompetencyProposalApprovedEmail_Success() {
        emailService.sendCompetencyProposalApprovedEmail(
                RECIPIENT, "Comp A", "Desc", List.of("Tag1"), "Admin", LOCALE
        );

        verify(messageSource).getMessage(eq("email.competency.approve.title"), any(), eq(LOCALE));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendCompetencyAssignmentProposalUpdateEmail_Success() {
        ProposalDto proposal = new ProposalDto();
        proposal.setId(300L);
        proposal.setStatus(ProposalStatus.ONGOING);

        Map<String, Integer> competencies = Map.of("Coding", 5);

        emailService.sendCompetencyAssignmentProposalUpdateEmail(
                RECIPIENT, proposal, "HR", "Manager", "Desc",
                List.of("Scope 1"), competencies, "User A", STAFF_ID, LOCALE
        );

        verify(messageSource).getMessage(eq("email.competency.assignment.update.title"), any(), eq(LOCALE));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendCompetencyAssignmentProposalApprovedEmail_EmptyCollections() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Approved");

        // Act with empty lists/maps
        emailService.sendCompetencyAssignmentProposalApprovedEmail(
                RECIPIENT, "IT", "Dev", "Desc",
                Collections.emptyList(), // Empty Job Scopes
                Collections.emptyMap(),  // Empty Competencies
                "Admin", LOCALE
        );

        // Verify mail is sent
        verify(mailSender).send(mimeMessage);

        // Capture the content to verify "-" was used
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        // Since MimeMessage is a mock, we can't inspect its content directly easily unless we verify setText
        // But verify(mailSender).send() confirms the flow completed without crashing on empty lists
    }

    @Test
    void sendCompetencyAssignmentProposalRejectedEmail_BlankDescription() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Rejected");
        when(validationService.isNullOrBlank(any())).thenReturn(true); // Simulate blank description

        emailService.sendCompetencyAssignmentProposalRejectedEmail(
                RECIPIENT, "IT", "Dev", null,
                List.of("Scope"), Map.of("Comp", 1),
                "Admin", LOCALE
        );

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPasswordResetEmail_Exception() {
        doThrow(new RuntimeException("Mail Error")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(HazardException.class, () ->
                emailService.sendPasswordResetEmail(RECIPIENT, "123", LOCALE));
    }

    @Test
    void sendCompetencyProposalUpdateEmail_Exception() {
        doThrow(new RuntimeException("Mail Error")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(HazardException.class, () ->
                emailService.sendCompetencyProposalUpdateEmail(RECIPIENT, new ProposalDto(), "Name", "Desc", List.of("Tag"), "User", STAFF_ID, LOCALE));
    }

    @Test
    void sendCompetencyAssignmentProposalProposeInvitationEmail_Exception() {
        doThrow(new RuntimeException("Mail Error")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(HazardException.class, () ->
                emailService.sendCompetencyAssignmentProposalProposeInvitationEmail(RECIPIENT, new ProposalDto(), "Dept", "Role", "Desc", List.of(), Map.of(), STAFF_ID, LOCALE));
    }

    @Test
    void sendCompetencyProposalApprovedEmail_EmptyTags() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Approved");

        emailService.sendCompetencyProposalApprovedEmail(
                RECIPIENT, "Comp Name", "Desc",
                Collections.emptyList(), // Empty Tags
                "Admin", LOCALE
        );

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendCompetencyProposalRejectedEmail_BlankDesc() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Rejected");
        when(validationService.isNullOrBlank(anyString())).thenReturn(true);

        emailService.sendCompetencyProposalRejectedEmail(
                RECIPIENT, "Comp Name", "", // Blank Desc
                List.of("Tag"), "Admin", LOCALE
        );

        verify(mailSender).send(mimeMessage);
    }
}