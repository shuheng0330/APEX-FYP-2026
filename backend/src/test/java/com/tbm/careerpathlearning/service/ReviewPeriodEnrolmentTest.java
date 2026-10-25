package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.enums.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ReviewPeriodEnrolmentTest {
    StaffRepository staff=mock(StaffRepository.class);
    ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    ReviewPeriodParticipantFactory factory=mock(ReviewPeriodParticipantFactory.class);
    PerformanceDepartmentResolver resolver=mock(PerformanceDepartmentResolver.class);
    ReviewPeriodEnrolmentService service=new ReviewPeriodEnrolmentService(staff,participants,factory,resolver,Clock.systemUTC());
    @Test void excludesInactiveDeletedAndSystemAccountsAndMarksEmptySnapshot() {
        var period=new AnnualKpiReviewPeriod();period.setId(1L);
        var role=new Role();role.setId(2L);
        var config=new ReviewPeriodRoleConfiguration();config.setRole(role);
        var inactive=new Staff();inactive.setRole(role);inactive.setAccountStatus(StaffAccountStatus.INACTIVE);
        var deleted=new Staff();deleted.setRole(role);deleted.setAccountStatus(StaffAccountStatus.ACTIVE);deleted.setDeleted(true);
        var systemRole=new Role();systemRole.setId(3L);systemRole.setPerformanceReviewEligible(false);
        var system=new Staff();system.setRole(systemRole);system.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findEligibleReviewStaff(anySet(),eq(StaffAccountStatus.ACTIVE))).thenReturn(List.of(inactive,deleted,system));
        service.enrol(period,List.of(config));assertNotNull(period.getParticipantsSnapshottedAt());
        verifyNoInteractions(factory);verify(participants).saveAllAndFlush(List.of());
    }
    @Test void enrolsActiveEmployeeThroughExistingFactory() {
        var period=new AnnualKpiReviewPeriod();period.setId(1L);
        var role=new Role();role.setId(2L);var config=new ReviewPeriodRoleConfiguration();config.setRole(role);
        var employee=new Staff();employee.setRole(role);employee.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findEligibleReviewStaff(anySet(),any())).thenReturn(List.of(employee));
        var participant=new ReviewPeriodParticipant();when(factory.snapshot(period,employee,config,null)).thenReturn(participant);
        service.enrol(period,List.of(config));verify(participants).saveAllAndFlush(List.of(participant));
        assertNotNull(participant.getCreatedAt());
    }
    @Test void refusesAnotherSnapshotIncludingPreviouslyEmptyRoster() {
        var period=new AnnualKpiReviewPeriod();period.setId(1L);period.setParticipantsSnapshottedAt(OffsetDateTime.now());
        assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.enrol(period,List.of()));
        verifyNoInteractions(staff,factory);
    }
    @Test void departmentFailureDoesNotSetSnapshotMarkerOrSavePartialRoster() {
        var period=new AnnualKpiReviewPeriod();period.setId(1L);
        var role=new Role();role.setId(2L);var config=new ReviewPeriodRoleConfiguration();config.setRole(role);
        var employee=new Staff();employee.setRole(role);employee.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findEligibleReviewStaff(anySet(),any())).thenReturn(List.of(employee));
        when(resolver.resolve(role)).thenThrow(new com.tbm.careerpathlearning.exception.BadRequestException("Ambiguous department"));
        assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.enrol(period,List.of(config)));
        assertNull(period.getParticipantsSnapshottedAt());verify(participants,never()).saveAllAndFlush(any());
    }

    @Test void factorySnapshotsDepartmentScheduleInsteadOfRoleDefault() {
        var period=new AnnualKpiReviewPeriod();period.setId(1L);period.setFrequencyPolicy(KpiFrequencyPolicy.DEPARTMENT_BASED);
        var role=new Role();role.setId(2L);role.setDefaultReviewFrequency(ReviewFrequency.ANNUALLY);
        var department=new OrgChart();department.setId(7L);department.setType(OrgChartType.D);department.setName("IT live name");
        var schedule=new ReviewPeriodDepartmentConfiguration();schedule.setId(3L);schedule.setReviewPeriod(period);
        schedule.setDepartment(department);schedule.setDepartmentName("IT recorded name");schedule.setReviewFrequency(ReviewFrequency.QUARTERLY);
        period.getDepartmentConfigurations().add(schedule);
        var config=new ReviewPeriodRoleConfiguration();config.setRole(role);config.setReviewPeriod(period);config.setReviewFrequency(ReviewFrequency.ANNUALLY);
        var weights=new ReviewPeriodEmployeeLevelConfiguration();weights.setId(5L);weights.setReviewPeriod(period);
        config.setEmployeeLevelConfiguration(weights);config.setEmployeeLevelConfigurationId(5L);
        var employee=new Staff();employee.setRole(role);
        var recorded=new ReviewPeriodParticipantFactory().snapshot(period,employee,config,department);
        assertEquals(ReviewFrequency.QUARTERLY,recorded.getReviewFrequency());
        assertEquals("IT recorded name",recorded.getDepartmentName());assertSame(schedule,recorded.getDepartmentConfiguration());
        assertSame(weights,recorded.getEmployeeLevelConfiguration());
        assertThrows(IllegalArgumentException.class,()->new ReviewPeriodParticipantFactory().snapshot(period,employee,config,null));
    }
}
