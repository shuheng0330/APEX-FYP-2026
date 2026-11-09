package com.tbm.careerpathlearning.service.appraisalinsight;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmployeeInsightAccessTest {
    final StaffRepository staff=mock(StaffRepository.class);final RoleAuthorityRepository permissions=mock(RoleAuthorityRepository.class);
    final ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);final AnnualAppraisalRepository appraisals=mock(AnnualAppraisalRepository.class);
    final EmployeeInsightSettings settings=mock(EmployeeInsightSettings.class);final MockEnvironment env=new MockEnvironment();final JdbcTemplate db=mock(JdbcTemplate.class);
    final EmployeeInsightAccess access=new EmployeeInsightAccess(staff,permissions,participants,appraisals,settings,env,db);
    final UUID actor=UUID.randomUUID();final Staff superior=new Staff();final Staff employee=new Staff();final ReviewPeriodParticipant p=new ReviewPeriodParticipant();
    @BeforeEach void setup() {
        var role=new Role();role.setId(1L);superior.setId(actor);superior.setRole(role);superior.setAccountStatus(StaffAccountStatus.ACTIVE);
        employee.setId(UUID.randomUUID());employee.setRole(role);employee.setAccountStatus(StaffAccountStatus.ACTIVE);employee.setManager(superior);
        var period=new AnnualKpiReviewPeriod();period.setId(1L);period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);p.setId(7L);p.setStaff(employee);p.setReviewPeriod(period);
        when(staff.findById(actor)).thenReturn(Optional.of(superior));when(participants.findById(7L)).thenReturn(Optional.of(p));
        when(permissions.existsByRoleIdAndAuthorityName(eq(1L),any())).thenReturn(true);login(actor,"CAN_SUBMIT_ANNUAL_APPRAISAL");
        env.setActiveProfiles("uat");when(settings.isEnabled()).thenReturn(true);
        when(settings.getDatabaseName()).thenReturn("disposable");when(settings.getDatabaseMarker()).thenReturn("verified");
        when(db.queryForObject("select current_database()",String.class)).thenReturn("disposable");
        when(db.queryForObject("select shobj_description(oid,'pg_database') from pg_database where datname=current_database()",String.class)).thenReturn("verified");
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    void login(UUID id,String permission) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority(permission))));}
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void currentSuperiorMayGenerateBeforeFirstDraftWithoutCreatingAnything(AnnualKpiReviewPeriodStatus status) {
        p.getReviewPeriod().setStatus(status);
        assertTrue(access.editable(p,actor));
        assertSame(p,access.superior(7L,actor,true));
        assertSame(p,access.worker(7L,actor));
        verify(appraisals,times(3)).findByParticipantId(7L);verifyNoMoreInteractions(appraisals);
    }
    @ParameterizedTest @CsvSource({"OPEN,DRAFT","OPEN,RETURNED","CLOSED,DRAFT","CLOSED,RETURNED"})
    void actorOwnedDraftOrReturnedAppraisalRemainsEditable(AnnualKpiReviewPeriodStatus period,AppraisalStatus status) {
        p.getReviewPeriod().setStatus(period);
        var a=new AnnualAppraisal();a.setSuperiorId(actor);a.setStatus(status);a.setJustification("Unchanged human input");
        when(appraisals.findByParticipantId(7L)).thenReturn(Optional.of(a));
        assertTrue(access.editable(p,actor));assertSame(p,access.superior(7L,actor,true));assertSame(p,access.worker(7L,actor));
        assertEquals(status,a.getStatus());assertEquals("Unchanged human input",a.getJustification());
        verify(appraisals,times(3)).findByParticipantId(7L);verifyNoMoreInteractions(appraisals);
    }
    @ParameterizedTest @CsvSource({"OPEN,PENDING_REVIEW","OPEN,APPROVED","CLOSED,PENDING_REVIEW","CLOSED,APPROVED"})
    void submittedAndApprovedAppraisalsCannotGenerateOrRegenerate(AnnualKpiReviewPeriodStatus period,AppraisalStatus status) {
        p.getReviewPeriod().setStatus(period);
        var a=new AnnualAppraisal();a.setSuperiorId(actor);a.setStatus(status);
        when(appraisals.findByParticipantId(7L)).thenReturn(Optional.of(a));
        assertFalse(access.editable(p,actor));
        assertThrows(BadRequestException.class,()->access.superior(7L,actor,true));
        assertThrows(BadRequestException.class,()->access.worker(7L,actor));
        assertSame(p,access.superior(7L,actor,false));
        verify(appraisals,times(3)).findByParticipantId(7L);verifyNoMoreInteractions(appraisals);
    }
    @ParameterizedTest @CsvSource({"OPEN,DRAFT","OPEN,RETURNED","CLOSED,DRAFT","CLOSED,RETURNED"})
    void currentSuperiorCannotGenerateForAnotherSuperiorsAppraisal(AnnualKpiReviewPeriodStatus period,AppraisalStatus status) {
        p.getReviewPeriod().setStatus(period);
        var a=new AnnualAppraisal();a.setSuperiorId(UUID.randomUUID());a.setStatus(status);
        when(appraisals.findByParticipantId(7L)).thenReturn(Optional.of(a));
        assertFalse(access.editable(p,actor));
        assertThrows(BadRequestException.class,()->access.superior(7L,actor,true));
        assertThrows(BadRequestException.class,()->access.worker(7L,actor));
        verify(appraisals,times(3)).findByParticipantId(7L);verifyNoMoreInteractions(appraisals);
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"DRAFT","UPCOMING"})
    void unpublishedPeriodsStillCannotGenerate(AnnualKpiReviewPeriodStatus status) {
        p.getReviewPeriod().setStatus(status);assertFalse(access.editable(p,actor));
        assertThrows(AccessDeniedException.class,()->access.superior(7L,actor,true));
        assertThrows(AccessDeniedException.class,()->access.worker(7L,actor));verifyNoInteractions(appraisals);
    }
    @Test void missingPeriodStatusDoesNotBecomeEditable() {
        p.getReviewPeriod().setStatus(null);assertFalse(access.editable(p,actor));
        assertThrows(AccessDeniedException.class,()->access.superior(7L,actor,true));verifyNoInteractions(appraisals);
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void workersStillRequireTheIsolatedUatEnvironment(AnnualKpiReviewPeriodStatus status) {
        p.getReviewPeriod().setStatus(status);
        when(settings.isEnabled()).thenReturn(false);assertThrows(AccessDeniedException.class,()->access.worker(7L,actor));
        when(settings.isEnabled()).thenReturn(true);env.setActiveProfiles("dev");
        assertThrows(AccessDeniedException.class,()->access.worker(7L,actor));env.setActiveProfiles("uat");
        when(db.queryForObject("select current_database()",String.class)).thenReturn("another-database");
        assertThrows(AccessDeniedException.class,()->access.worker(7L,actor));
        verifyNoInteractions(appraisals);
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void staleTokenRevokedRoleOrReportingLineCannotGrantAccess(AnnualKpiReviewPeriodStatus status) {
        p.getReviewPeriod().setStatus(status);
        when(permissions.existsByRoleIdAndAuthorityName(1L,AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL)).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->access.superior(7L,actor,true));
        when(permissions.existsByRoleIdAndAuthorityName(1L,AuthorityName.CAN_SUBMIT_ANNUAL_APPRAISAL)).thenReturn(true);
        employee.setManager(new Staff());assertThrows(AccessDeniedException.class,()->access.superior(7L,actor,true));
        employee.setManager(superior);superior.setDeleted(true);assertThrows(AccessDeniedException.class,()->access.superior(7L,actor,true));
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void dashboardHrAndSubjectIdentityDoNotGrantSuperiorGeneration(AnnualKpiReviewPeriodStatus status) {
        p.getReviewPeriod().setStatus(status);
        for (String permission:List.of("ROLE_USER","CAN_REVIEW_ANNUAL_APPRAISAL","CAN_VIEW_TEAM_PERFORMANCE","CAN_VIEW_ORGANISATION_PERFORMANCE")) {
            login(actor,permission);assertThrows(AccessDeniedException.class,()->access.superior(7L,actor,true));
        }
        login(UUID.randomUUID(),"CAN_SUBMIT_ANNUAL_APPRAISAL");assertThrows(AccessDeniedException.class,()->access.superior(7L,actor,true));
        login(actor,"CAN_SUBMIT_ANNUAL_APPRAISAL");employee.setId(actor);assertThrows(AccessDeniedException.class,()->access.superior(7L,actor,true));
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void hrCanOnlyReadSubmittedRecordsAndCannotReadOwnEmployeeAppraisal(AnnualKpiReviewPeriodStatus status) {
        p.getReviewPeriod().setStatus(status);
        var a=new AnnualAppraisal();a.setParticipant(p);a.setEmployeeId(employee.getId());a.setReviewPeriodId(1L);a.setStatus(AppraisalStatus.PENDING_REVIEW);
        login(actor,"CAN_REVIEW_ANNUAL_APPRAISAL");assertDoesNotThrow(()->access.submitted(a,actor));
        a.setStatus(AppraisalStatus.DRAFT);assertThrows(AccessDeniedException.class,()->access.submitted(a,actor));
        a.setStatus(AppraisalStatus.APPROVED);employee.setId(actor);a.setEmployeeId(actor);assertThrows(AccessDeniedException.class,()->access.submitted(a,actor));
    }
}
