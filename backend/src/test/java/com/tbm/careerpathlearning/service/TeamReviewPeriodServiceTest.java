package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamReviewPeriodServiceTest {
    UUID actor=UUID.randomUUID();
    ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    StaffRepository staff=mock(StaffRepository.class);
    TeamReviewPeriodService service=new TeamReviewPeriodService(participants,staff,Mappers.getMapper(KpiPlanMapper.class),
            Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"),ZoneOffset.UTC));
    @BeforeEach void setup() {
        var account=new Staff();account.setId(actor);account.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findById(actor)).thenReturn(Optional.of(account));
    }
    AnnualKpiReviewPeriod period(long id,String end,AnnualKpiReviewPeriodStatus status) {
        var p=new AnnualKpiReviewPeriod();p.setId(id);p.setName("Period "+id);p.setStatus(status);
        p.setEndDate(LocalDate.parse(end));p.setStartDate(p.getEndDate().minusMonths(11));return p;
    }
    @Test void openWinsEvenAfterItsEndDateAndDraftsAreExcluded() {
        when(participants.findTeamReviewPeriods(actor)).thenReturn(List.of(period(1,"2025-12-31",AnnualKpiReviewPeriodStatus.OPEN),
            period(2,"2026-10-10",AnnualKpiReviewPeriodStatus.CLOSED),period(3,"2027-12-31",AnnualKpiReviewPeriodStatus.DRAFT)));
        var result=service.periods(actor);assertEquals(1L,result.defaultPeriodId());assertEquals(2,result.periods().size());
        verify(participants).findTeamReviewPeriods(actor);
    }
    @Test void latestEndedUsesDeterministicTieBreakAndExcludesToday() {
        when(participants.findTeamReviewPeriods(actor)).thenReturn(List.of(period(1,"2026-10-10",AnnualKpiReviewPeriodStatus.CLOSED),
            period(2,"2026-10-10",AnnualKpiReviewPeriodStatus.CLOSED),period(3,"2026-10-11",AnnualKpiReviewPeriodStatus.CLOSED),
            period(4,"2027-12-31",AnnualKpiReviewPeriodStatus.UPCOMING)));
        assertEquals(2L,service.periods(actor).defaultPeriodId());
    }
    @Test void noOpenOrEndedLeavesSelectionEmpty() {
        when(participants.findTeamReviewPeriods(actor)).thenReturn(List.of(period(1,"2027-12-31",AnnualKpiReviewPeriodStatus.UPCOMING)));
        assertNull(service.periods(actor).defaultPeriodId());
    }
    @Test void inactiveAccountCannotReadCoverage() {
        staff.findById(actor).orElseThrow().setDeleted(true);
        assertThrows(AccessDeniedException.class,()->service.periods(actor));verifyNoInteractions(participants);
    }
}
