package com.tbm.careerpathlearning.scheduler;

import com.tbm.careerpathlearning.service.KpiNotificationScheduleService;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class KpiNotificationSchedulerTest {
    @Test void scanStartsAtNineInTheExistingBusinessClockZone() {
        var service=mock(KpiNotificationScheduleService.class);var zone=ZoneId.of("Asia/Singapore");
        new KpiNotificationScheduler(service,Clock.fixed(Instant.parse("2027-06-30T23:59:59Z"),zone)).scanIfDue();
        verifyNoInteractions(service);
        new KpiNotificationScheduler(service,Clock.fixed(Instant.parse("2027-07-01T01:00:00Z"),zone)).scanIfDue();
        verify(service).scanToday();
    }
    @Test void failureDoesNotEscapeTheSchedulerAndNextRunRetries() {
        var service=mock(KpiNotificationScheduleService.class);doThrow(new IllegalStateException("Transient failure")).doNothing().when(service).scanToday();
        var scheduler=new KpiNotificationScheduler(service,Clock.fixed(Instant.parse("2027-07-01T12:00:00Z"),ZoneOffset.UTC));
        assertDoesNotThrow(scheduler::scanIfDue);scheduler.scanIfDue();verify(service,times(2)).scanToday();
    }
}
