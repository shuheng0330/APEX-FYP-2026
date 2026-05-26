package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.ProposalDto;
import com.tbm.careerpathlearning.exception.HazardException;
import com.tbm.careerpathlearning.service.EmailService;
import com.tbm.careerpathlearning.service.ValidationService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Value("${frontend.origin}")
    private String FRONTEND_ORIGIN;

    @Value("${mail-server.no-reply}")
    private String NO_REPLY;

    @Value("${server.name}")
    private String SERVER_NAME;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ValidationService validationService;

    private final JavaMailSender mailSender;

    private static final String APP_NAME = "app.name";

    private static final String EMAIL_RESET_TITLE = "email.reset.title";

    private static final String EMAIL_RESET_MESSAGE = "email.reset.message";

    private static final String EMAIL_RESET_OTP = "email.reset.otp";

    private static final String EMAIL_RESET_REMINDER = "email.reset.reminder";

    private static final String EMAIL_REGISTER_TITLE = "email.register.title";

    private static final String EMAIL_REGISTER_MESSAGE = "email.register.message";

    private static final String EMAIL_REGISTER_NEXT = "email.register.next";

    private static final String EMAIL_REGISTER_INSTRUCTION = "email.register.instruction";

    private static final String EMAIL_ACTIVATION_TITLE = "email.activation.title";

    private static final String EMAIL_ACTIVATION_MESSAGE = "email.activation.message";

    private static final String EMAIL_ACTIVATION_OTP = "email.activation.otp";

    private static final String EMAIL_ACTIVATION_REMINDER = "email.activation.reminder";

    private static final String ENCODING = "UTF-8";

    private static final String EMAIL_SERVER_ERR_TITLE_CODE = "email.server.err.title";

    private static final String EMAIL_RESET_ERR_MSG_CODE = "email.reset.err.msg";

    private static final String EMAIL_REGISTER_ERR_MSG_CODE = "email.register.err.msg";

    private static final String EMAIL_ACTIVATION_ERR_MSG_CODE = "email.activation.err.msg";

    private static final String EMAIL_COMPETENCY_PROPOSER_INVITE_TITLE = "email.competency.proposer.invite.title";

    private static final String EMAIL_COMPETENCY_PROPOSER_INVITE_MESSAGE = "email.competency.proposer.invite.message";

    private static final String EMAIL_COMPETENCY_PROPOSER_INVITE_INSTRUCTION = "email.competency.proposer.invite.instruction";

    private static final String EMAIL_COMPETENCY_PROPOSER_INVITE_OPEN = "email.competency.proposer.invite.open";

    private static final String EMAIL_COMPETENCY_PROPOSER_INVITE_ERR_MSG_CODE = "email.competency.proposer.invite.err.msg";

    private static final String EMAIL_COMPETENCY_REVIEWER_INVITE_TITLE = "email.competency.reviewer.invite.title";

    private static final String EMAIL_COMPETENCY_REVIEWER_INVITE_MESSAGE = "email.competency.reviewer.invite.message";

    private static final String EMAIL_COMPETENCY_REVIEWER_INVITE_INSTRUCTION = "email.competency.reviewer.invite.instruction";

    private static final String EMAIL_COMPETENCY_REVIEWER_INVITE_OPEN = "email.competency.reviewer.invite.open";

    private static final String EMAIL_COMPETENCY_REVIEWER_INVITE_ERR_MSG_CODE = "email.competency.reviewer.invite.err.msg";

    private static final String EMAIL_COMPETENCY_UPDATE_TITLE = "email.competency.update.title";

    private static final String EMAIL_COMPETENCY_UPDATE_MESSAGE = "email.competency.update.message";

    private static final String EMAIL_COMPETENCY_UPDATE_INSTRUCTION = "email.competency.update.instruction";

    private static final String EMAIL_COMPETENCY_UPDATE_TITLE_OPEN = "email.competency.update.open";

    private static final String EMAIL_COMPETENCY_UPDATE_ERR_MSG_CODE = "email.competency.update.err.msg";

    private static final String EMAIL_COMPETENCY_REJECT_TITLE = "email.competency.reject.title";

    private static final String EMAIL_COMPETENCY_REJECT_MESSAGE = "email.competency.reject.message";

    private static final String EMAIL_COMPETENCY_REJECT_ERR_MSG_CODE = "email.competency.reject.err.msg";

    private static final String EMAIL_COMPETENCY_APPROVE_TITLE = "email.competency.approve.title";

    private static final String EMAIL_COMPETENCY_APPROVE_MESSAGE = "email.competency.approve.message";

    private static final String EMAIL_COMPETENCY_APPROVE_ERR_MSG_CODE = "email.competency.approve.err.msg";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_PROPOSER_INVITE_TITLE = "email.competency.assignment.proposer.invite.title";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_PROPOSER_INVITE_MESSAGE = "email.competency.assignment.proposer.invite.message";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_PROPOSER_INVITE_INSTRUCTION = "email.competency.assignment.proposer.invite.instruction";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_PROPOSER_INVITE_OPEN = "email.competency.assignment.proposer.invite.open";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_PROPOSER_INVITE_ERR_MSG_CODE = "email.competency.assignment.proposer.invite.err.msg";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_REVIEWER_INVITE_TITLE = "email.competency.assignment.reviewer.invite.title";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_REVIEWER_INVITE_MESSAGE = "email.competency.assignment.reviewer.invite.message";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_REVIEWER_INVITE_INSTRUCTION = "email.competency.assignment.reviewer.invite.instruction";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_REVIEWER_INVITE_OPEN = "email.competency.assignment.reviewer.invite.open";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_REVIEWER_INVITE_ERR_MSG_CODE = "email.competency.assignment.reviewer.invite.err.msg";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_UPDATE_TITLE = "email.competency.assignment.update.title";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_UPDATE_MESSAGE = "email.competency.assignment.update.message";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_UPDATE_INSTRUCTION = "email.competency.assignment.update.instruction";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_UPDATE_TITLE_OPEN = "email.competency.assignment.update.open";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_UPDATE_ERR_MSG_CODE = "email.competency.assignment.update.err.msg";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_REJECT_TITLE = "email.competency.assignment.reject.title";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_REJECT_MESSAGE = "email.competency.assignment.reject.message";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_REJECT_ERR_MSG_CODE = "email.competency.assignment.reject.err.msg";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_APPROVE_TITLE = "email.competency.assignment.approve.title";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_APPROVE_MESSAGE = "email.competency.assignment.approve.message";

    private static final String EMAIL_COMPETENCY_ASSIGNMENT_APPROVE_ERR_MSG_CODE = "email.competency.assignment.approve.err.msg";


    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    @Override
    public void sendPasswordResetEmail(String to, String otp, Locale locale) {
        try {
            String title = messageSource.getMessage(EMAIL_RESET_TITLE, null, locale);
            String appName = messageSource.getMessage(APP_NAME, null, locale);
            String messageText = messageSource.getMessage(EMAIL_RESET_MESSAGE, new String[]{appName}, locale);
            String messageOtp = messageSource.getMessage(EMAIL_RESET_OTP, null, locale);
            String messageReminder = messageSource.getMessage(EMAIL_RESET_REMINDER, null, locale);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(to);
            helper.setSubject(title);

            String htmlContent = """
                        <p>%s<br/><br/>%s<br/><h4>%s</h4></p><p>%s</p>
                    """.formatted(messageText, messageOtp, otp, messageReminder);

            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(EMAIL_RESET_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    @Async
    @Override
    public void sendAccountRegisteredEmail(String to, Locale locale) {
        try {
            String appName = messageSource.getMessage(APP_NAME, null, locale);

            String title = messageSource.getMessage(EMAIL_REGISTER_TITLE, null, locale);
            String messageText = messageSource.getMessage(EMAIL_REGISTER_MESSAGE, null, locale);
            String messageNext = messageSource.getMessage(EMAIL_REGISTER_NEXT, null, locale);
            String messageInstruction = messageSource.getMessage(EMAIL_REGISTER_INSTRUCTION, new String[]{appName}, locale);

            String firstTimeLoginUrl = FRONTEND_ORIGIN + "/first-time-login";

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(to);
            helper.setSubject(title);

            String htmlContent = """
                        <p>%s<br/><h3>%s</h3>%s<br/><a href='%s' target='_blank'>%s</a></p>
                    """.formatted(messageText, messageNext, messageInstruction, firstTimeLoginUrl, firstTimeLoginUrl);

            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(EMAIL_REGISTER_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    @Async
    @Override
    public void sendAccountActivationEmail(String to, String otp, Locale locale) {
        try {
            String appName = messageSource.getMessage(APP_NAME, null, locale);
            String title = messageSource.getMessage(EMAIL_ACTIVATION_TITLE, null, locale);
            String messageText = messageSource.getMessage(EMAIL_ACTIVATION_MESSAGE, new String[]{appName}, locale);
            String messageOtp = messageSource.getMessage(EMAIL_ACTIVATION_OTP, null, locale);
            String messageReminder = messageSource.getMessage(EMAIL_ACTIVATION_REMINDER, null, locale);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(to);
            helper.setSubject(title);

            String htmlContent = """
                        <p>%s<br/><br/>%s<br/><h4>%s</h4></p><p>%s</p>
                    """.formatted(messageText, messageOtp, otp, messageReminder);

            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(EMAIL_ACTIVATION_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    @Async
    @Override
    public void sendCompetencyProposalProposeInvitationEmail(
            String recipient,
            ProposalDto proposalDto,
            String competencyName,
            String competencyDescription,
            UUID staffId,
            List<String> compTag,
            Locale locale) {
        String title = messageSource.getMessage(
                EMAIL_COMPETENCY_PROPOSER_INVITE_TITLE, null, locale);
        String messageText = messageSource.getMessage(EMAIL_COMPETENCY_PROPOSER_INVITE_MESSAGE, null, locale);
        String instruction = messageSource.getMessage(EMAIL_COMPETENCY_PROPOSER_INVITE_INSTRUCTION, null, locale);
        String open = messageSource.getMessage(EMAIL_COMPETENCY_PROPOSER_INVITE_OPEN, null, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(recipient);
            helper.setSubject(title);

            String htmlContent = """
                    <h1>%s</h1>
                    <ul>
                        <li>Status: %s</li>
                        <li>Competency Name: %s</li>
                        <li>Description: %s</li>
                        <li>Tag: <br/><ol>%s</ol></li>
                    </ul>
                    <br/>
                    <p>%s</p>
                    <a href="%s/competencies?proposalId=%s&staffId=%s&tabIndex=definition">%s</a>
                    """.formatted(
                    messageText,
                    proposalDto.getStatus(),
                    competencyName,
                    validationService.isNullOrBlank(competencyDescription) ? "-" : competencyDescription,
                    this.convertCompTagToString(compTag),
                    instruction,
                    FRONTEND_ORIGIN,
                    proposalDto.getId(),
                    staffId,
                    open
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(
                    EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(
                    EMAIL_COMPETENCY_PROPOSER_INVITE_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    private String convertJobScopeToString(List<String> jobScopes) {
        if (jobScopes.isEmpty()) {
            return "-";
        }
        return jobScopes.stream().map(jobScope -> "<li>" + jobScope).collect(Collectors.joining("</li>"));
    }

    private String convertCompetencyToString(Map<String, Integer> competencyMap) {
        if (competencyMap.isEmpty()) {
            return "-";
        }
        return competencyMap.entrySet().stream()
                .map(map -> "<li>" + map.getKey() + " - " + map.getValue() + "%")
                .collect(Collectors.joining("</li>"));
    }

    @Async
    @Override
    public void sendCompetencyProposalReviewInvitationEmail(
            String recipient,
            ProposalDto proposalDto,
            String competencyName,
            String competencyDescription,
            UUID staffId,
            List<String> compTag,
            Locale locale) {
        String title = messageSource.getMessage(
                EMAIL_COMPETENCY_REVIEWER_INVITE_TITLE, null, locale);
        String messageText = messageSource.getMessage(EMAIL_COMPETENCY_REVIEWER_INVITE_MESSAGE, null, locale);
        String instruction = messageSource.getMessage(EMAIL_COMPETENCY_REVIEWER_INVITE_INSTRUCTION, null, locale);
        String open = messageSource.getMessage(EMAIL_COMPETENCY_REVIEWER_INVITE_OPEN, null, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(recipient);
            helper.setSubject(title);

            String htmlContent = """
                    <h1>%s</h1>
                    <ul>
                        <li>Status: %s</li>
                        <li>Competency Name: %s</li>
                        <li>Description: %s</li>
                        <li>Tag: <br/><ol>%s</ol></li>
                    </ul>
                    <br/>
                    <p>%s</p>
                    <a href="%s/competencies?proposalId=%s&staffId=%s&tabIndex=definition">%s</a>
                    """.formatted(
                    messageText,
                    proposalDto.getStatus(),
                    competencyName,
                    validationService.isNullOrBlank(competencyDescription) ? "-" : competencyDescription,
                    this.convertCompTagToString(compTag),
                    instruction,
                    FRONTEND_ORIGIN,
                    proposalDto.getId(),
                    staffId,
                    open
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(
                    EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(
                    EMAIL_COMPETENCY_REVIEWER_INVITE_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    @Async
    @Override
    public void sendCompetencyProposalUpdateEmail(
            String recipient,
            ProposalDto proposalDto,
            String competencyName,
            String competencyDescription,
            List<String> compTag,
            String updatedBy,
            UUID staffId,
            Locale locale) {
        String title = messageSource.getMessage(
                EMAIL_COMPETENCY_UPDATE_TITLE, null, locale);
        String messageText = messageSource.getMessage(EMAIL_COMPETENCY_UPDATE_MESSAGE, null, locale);
        String instruction = messageSource.getMessage(EMAIL_COMPETENCY_UPDATE_INSTRUCTION, null, locale);
        String open = messageSource.getMessage(EMAIL_COMPETENCY_UPDATE_TITLE_OPEN, null, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(recipient);
            helper.setSubject(title);

            String htmlContent = """
                    <h1>%s</h1>
                    <ul>
                        <li>Status: %s</li>
                        <li>Competency Name: %s</li>
                        <li>Description: %s</li>
                        <li>Tag: <br/><ol>%s</ol></li>
                        <li>Updated By: <a href="mailto:%s">%s</a></li>
                    </ul>
                    <br/>
                    <p>%s</p>
                    <a href="%s/competencies?proposalId=%s&staffId=%s&tabIndex=definition">%s</a>
                    """.formatted(
                    messageText,
                    proposalDto.getStatus(),
                    competencyName,
                    validationService.isNullOrBlank(competencyDescription) ? "-" : competencyDescription,
                    this.convertCompTagToString(compTag),
                    updatedBy,
                    updatedBy,
                    instruction,
                    FRONTEND_ORIGIN,
                    proposalDto.getId(),
                    staffId,
                    open
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(
                    EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(
                    EMAIL_COMPETENCY_UPDATE_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    @Async
    @Override
    public void sendCompetencyProposalRejectedEmail(
            String recipient,
            String competencyName,
            String competencyDescription,
            List<String> compTag,
            String updatedBy,
            Locale locale) {
        String title = messageSource.getMessage(
                EMAIL_COMPETENCY_REJECT_TITLE, null, locale);
        String messageText = messageSource.getMessage(EMAIL_COMPETENCY_REJECT_MESSAGE, null, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(recipient);
            helper.setSubject(title);

            String htmlContent = """
                    <h1>%s</h1>
                    <ul>
                        <li>Competency Name: %s</li>
                        <li>Description: %s</li>
                        <li>Tag: <br/><ol>%s</ol></li>
                        <li>Rejected By: <a href="mailto:%s">%s</a></li>
                    </ul>
                    
                    """.formatted(
                    messageText,
                    competencyName,
                    validationService.isNullOrBlank(competencyDescription) ? "-" : competencyDescription,
                    this.convertCompTagToString(compTag),
                    updatedBy,
                    updatedBy
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(
                    EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(
                    EMAIL_COMPETENCY_REJECT_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    private String convertCompTagToString(List<String> compTags) {
        if (compTags.isEmpty()) {
            return "-";
        }
        return compTags.stream().map(compTag -> "<li>" + compTag).collect(Collectors.joining("</li>"));
    }

    @Async
    @Override
    public void sendCompetencyProposalApprovedEmail(
            String recipient,
            String competencyName,
            String competencyDescription,
            List<String> compTag,
            String updatedBy,
            Locale locale) {
        String title = messageSource.getMessage(
                EMAIL_COMPETENCY_APPROVE_TITLE, null, locale);
        String messageText = messageSource.getMessage(EMAIL_COMPETENCY_APPROVE_MESSAGE, null, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(recipient);
            helper.setSubject(title);

            String htmlContent = """
                    <h1>%s</h1>
                    <ul>
                        <li>Competency Name: %s</li>
                        <li>Description: %s</li>
                        <li>Tag: <br/><ol>%s</ol></li>
                        <li>Approved By: <a href="mailto:%s">%s</a></li>
                    </ul>
                    
                    """.formatted(
                    messageText,
                    competencyName,
                    validationService.isNullOrBlank(competencyDescription) ? "-" : competencyDescription,
                    this.convertCompTagToString(compTag),
                    updatedBy,
                    updatedBy
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(
                    EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(
                    EMAIL_COMPETENCY_APPROVE_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    @Async
    @Override
    public void sendCompetencyAssignmentProposalReviewInvitationEmail(
            String recipient,
            ProposalDto proposalDto,
            String departmentName,
            String roleName,
            String roleDescription,
            List<String> jobScopeList,
            Map<String, Integer> competencyMap,
            UUID staffId,
            Locale locale) {
        String title = messageSource.getMessage(
                EMAIL_COMPETENCY_ASSIGNMENT_REVIEWER_INVITE_TITLE, null, locale);
        String messageText = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_REVIEWER_INVITE_MESSAGE, null, locale);
        String instruction = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_REVIEWER_INVITE_INSTRUCTION, null, locale);
        String open = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_REVIEWER_INVITE_OPEN, null, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(recipient);
            helper.setSubject(title);

            String htmlContent = """
                    <h1>%s</h1>
                    <ul>
                        <li>Status: %s</li>
                        <li>Department Name: %s</li>
                        <li>Role Name: %s</li>
                        <li>Role Description: %s</li>
                        <li>Job scopes: <br/><ol>%s</ol></li>
                        <li>Competency: <br/><ol>%s</ol></li>
                    </ul>
                    <br/>
                    <p>%s</p>
                    <a href="%s/competencies?proposalId=%s&staffId=%s&tabIndex=assignment">%s</a>
                    """.formatted(
                    messageText,
                    proposalDto.getStatus(),
                    departmentName,
                    roleName,
                    validationService.isNullOrBlank(roleDescription) ? "-" : roleDescription,
                    this.convertJobScopeToString(jobScopeList),
                    this.convertCompetencyToString(competencyMap),
                    instruction,
                    FRONTEND_ORIGIN,
                    proposalDto.getId(),
                    staffId,
                    open
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(
                    EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(
                    EMAIL_COMPETENCY_ASSIGNMENT_REVIEWER_INVITE_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    @Async
    @Override
    public void sendCompetencyAssignmentProposalProposeInvitationEmail(
            String recipient,
            ProposalDto proposalDto,
            String departmentName,
            String roleName,
            String roleDescription,
            List<String> jobScopeList,
            Map<String, Integer> competencyMap,
            UUID staffId,
            Locale locale) {
        String title = messageSource.getMessage(
                EMAIL_COMPETENCY_ASSIGNMENT_PROPOSER_INVITE_TITLE, null, locale);
        String messageText = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_PROPOSER_INVITE_MESSAGE, null, locale);
        String instruction = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_PROPOSER_INVITE_INSTRUCTION, null, locale);
        String open = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_PROPOSER_INVITE_OPEN, null, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(recipient);
            helper.setSubject(title);

            String htmlContent = """
                    <h1>%s</h1>
                    <ul>
                        <li>Status: %s</li>
                        <li>Department Name: %s</li>
                        <li>Role Name: %s</li>
                        <li>Role Description: %s</li>
                        <li>Job scopes: <br/><ol>%s</ol></li>
                        <li>Competency: <br/><ol>%s</ol></li>
                    </ul>
                    <br/>
                    <p>%s</p>
                    <a href="%s/competencies?proposalId=%s&staffId=%s&tabIndex=assignment">%s</a>
                    """.formatted(
                    messageText,
                    proposalDto.getStatus(),
                    departmentName,
                    roleName,
                    validationService.isNullOrBlank(roleDescription) ? "-" : roleDescription,
                    this.convertJobScopeToString(jobScopeList),
                    this.convertCompetencyToString(competencyMap),
                    instruction,
                    FRONTEND_ORIGIN,
                    proposalDto.getId(),
                    staffId,
                    open
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(
                    EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(
                    EMAIL_COMPETENCY_ASSIGNMENT_PROPOSER_INVITE_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    @Async
    @Override
    public void sendCompetencyAssignmentProposalUpdateEmail(
            String recipient,
            ProposalDto proposalDto,
            String departmentName,
            String roleName,
            String roleDescription,
            List<String> jobScopeList,
            Map<String, Integer> competencyMap,
            String updatedBy,
            UUID staffId,
            Locale locale) {
        String title = messageSource.getMessage(
                EMAIL_COMPETENCY_ASSIGNMENT_UPDATE_TITLE, null, locale);
        String messageText = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_UPDATE_MESSAGE, null, locale);
        String instruction = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_UPDATE_INSTRUCTION, null, locale);
        String open = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_UPDATE_TITLE_OPEN, null, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(recipient);
            helper.setSubject(title);

            String htmlContent = """
                    <h1>%s</h1>
                    <ul>
                        <li>Status: %s</li>
                        <li>Department Name: %s</li>
                        <li>Role Name: %s</li>
                        <li>Role Description: %s</li>
                        <li>Job scopes: <br/><ol>%s</ol></li>
                        <li>Competency: <br/><ol>%s</ol></li>
                        <li>Updated By: <a href="mailto:%s">%s</a></li>
                    </ul>
                    <br/>
                    <p>%s</p>
                    <a href="%s/competencies?proposalId=%s&staffId=%s&tabIndex=assignment">%s</a>
                    """.formatted(
                    messageText,
                    proposalDto.getStatus(),
                    departmentName,
                    roleName,
                    validationService.isNullOrBlank(roleDescription) ? "-" : roleDescription,
                    this.convertJobScopeToString(jobScopeList),
                    this.convertCompetencyToString(competencyMap),
                    updatedBy,
                    updatedBy,
                    instruction,
                    FRONTEND_ORIGIN,
                    proposalDto.getId(),
                    staffId,
                    open
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(
                    EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(
                    EMAIL_COMPETENCY_ASSIGNMENT_UPDATE_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    @Async
    @Override
    public void sendCompetencyAssignmentProposalRejectedEmail(
            String recipient,
            String departmentName,
            String roleName,
            String description,
            List<String> jobScopes,
            Map<String, Integer> competencyMap,
            String updatedBy,
            Locale locale) {
        String title = messageSource.getMessage(
                EMAIL_COMPETENCY_ASSIGNMENT_REJECT_TITLE, null, locale);
        String messageText = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_REJECT_MESSAGE, null, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(recipient);
            helper.setSubject(title);

            String htmlContent = """
                    <h1>%s</h1>
                    <ul>
                        <li>Department Name: %s</li>
                        <li>Role Name: %s</li>
                        <li>Role Description: %s</li>
                        <li>Job scopes: <br/><ol>%s</ol></li>
                        <li>Competency: <br/><ol>%s</ol></li>
                        <li>Rejected By: <a href="mailto:%s">%s</a></li>
                    </ul>
                    """.formatted(
                    messageText,
                    departmentName,
                    roleName,
                    validationService.isNullOrBlank(description) ? "-" : description,
                    this.convertJobScopeToString(jobScopes),
                    this.convertCompetencyToString(competencyMap),
                    updatedBy,
                    updatedBy
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(
                    EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(
                    EMAIL_COMPETENCY_ASSIGNMENT_REJECT_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

    @Async
    @Override
    public void sendCompetencyAssignmentProposalApprovedEmail(
            String recipient,
            String departmentName,
            String roleName,
            String description,
            List<String> jobScopes,
            Map<String, Integer> competencyMap,
            String updatedBy,
            Locale locale) {
        String title = messageSource.getMessage(
                EMAIL_COMPETENCY_ASSIGNMENT_APPROVE_TITLE, null, locale);
        String messageText = messageSource.getMessage(EMAIL_COMPETENCY_ASSIGNMENT_APPROVE_MESSAGE, null, locale);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);

            helper.setFrom(new InternetAddress(NO_REPLY, SERVER_NAME));
            helper.setTo(recipient);
            helper.setSubject(title);

            String htmlContent = """
                    <h1>%s</h1>
                    <ul>
                        <li>Department Name: %s</li>
                        <li>Role Name: %s</li>
                        <li>Role Description: %s</li>
                        <li>Job scopes: <br/><ol>%s</ol></li>
                        <li>Competency: <br/><ol>%s</ol></li>
                        <li>Approved By: <a href="mailto:%s">%s</a></li>
                    </ul>
                    """.formatted(
                    messageText,
                    departmentName,
                    roleName,
                    validationService.isNullOrBlank(description) ? "-" : description,
                    this.convertJobScopeToString(jobScopes),
                    this.convertCompetencyToString(competencyMap),
                    updatedBy,
                    updatedBy
            );

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(
                    EMAIL_SERVER_ERR_TITLE_CODE, null, locale);
            String errorMessage = messageSource.getMessage(
                    EMAIL_COMPETENCY_ASSIGNMENT_APPROVE_ERR_MSG_CODE, null, locale);

            throw new HazardException(errorTitle, errorMessage, e);
        }
    }

}


