package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class KpiAssignmentServiceTest {
    ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    EmployeeKpiAssignmentRepository assignments=mock(EmployeeKpiAssignmentRepository.class);
    KpiAssignmentService service=new KpiAssignmentService(participants,assignments,Clock.systemUTC());
    @Test void requiresCompletedSnapshotNotNonemptyRoster() {
        var p=new AnnualKpiReviewPeriod();p.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        assertThrows(BadRequestException.class,()->service.requirePublishedRoster(p));
        p.setParticipantsSnapshottedAt(OffsetDateTime.now());assertDoesNotThrow(()->service.requirePublishedRoster(p));
        p.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);assertThrows(BadRequestException.class,()->service.requirePublishedRoster(p));
    }
    @Test void assignsEveryCompanyItemOnlyToExistingParticipants() {
        var period=new AnnualKpiReviewPeriod();period.setId(1L);period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);period.setParticipantsSnapshottedAt(OffsetDateTime.now());
        var plan=new KpiPlan();plan.setReviewPeriod(period);plan.setLevel(KpiLevel.COMPANY);
        var a=new Kpi();a.setId(11L);var b=new Kpi();b.setId(12L);plan.setItems(List.of(a,b));
        var p=new ReviewPeriodParticipant();p.setId(21L);when(participants.findAllByReviewPeriodId(1L)).thenReturn(List.of(p));
        service.cascade(plan);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<EmployeeKpiAssignment>> capture=ArgumentCaptor.forClass(List.class);
        verify(assignments).saveAllAndFlush(capture.capture());assertEquals(2,capture.getValue().size());
        assertTrue(capture.getValue().stream().allMatch(i->i.getParticipantId().equals(21L)&&i.getReviewPeriodId().equals(1L)));
        verify(participants,never()).saveAll(any());
    }
}
