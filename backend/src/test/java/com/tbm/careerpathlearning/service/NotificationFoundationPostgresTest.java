package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.model.UserNotification;
import com.tbm.careerpathlearning.repository.UserNotificationInsertImpl;
import com.tbm.careerpathlearning.repository.UserNotificationRepository;
import com.tbm.careerpathlearning.service.impl.NotificationServiceImpl;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit disposable PostgreSQL only. Never reads .env or connects to application databases. */
@EnabledIfEnvironmentVariable(named = "APEX_NOTIFICATION_TEST_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:55450/postgres")
class NotificationFoundationPostgresTest {
    private final UUID recipient = UUID.randomUUID();
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private String schema;
    private SessionFactory sessions;

    @BeforeEach
    void createDisposableSchema() throws Exception {
        schema = "notification_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = connection()) {
            sql(connection, "CREATE SCHEMA " + schema);
            sql(connection, "SET search_path TO " + schema);
            sql(connection, migration());
        }
        sessions = new Configuration().addAnnotatedClass(UserNotification.class)
                .setProperty("hibernate.connection.url", System.getenv("APEX_NOTIFICATION_TEST_URL") + "?currentSchema=" + schema)
                .setProperty("hibernate.connection.username", "notification_test")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .buildSessionFactory();
    }

    @AfterEach
    void removeDisposableSchema() throws Exception {
        if (sessions != null) sessions.close();
        if (schema != null) {
            try (var connection = connection()) {
                sql(connection, "DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void migrationAdoptsPopulatedTableWithoutRewritingRowsOrAddingDuplicateUniqueIndexes() throws Exception {
        try (var session = sessions.openSession()) {
            session.beginTransaction();
            assertTrue(service(session).notifyInAppOnce("preserved", recipient, NotificationType.COURSE_ASSIGNED, "t", "b", null));
            session.getTransaction().commit();
        }
        try (var connection = connection()) {
            sql(connection, "SET search_path TO " + schema);
            sql(connection, "ALTER INDEX uq_user_notification_dedupe_key RENAME TO existing_training_dedupe_index");
            String before = text(connection, "SELECT to_jsonb(n)::text FROM user_notification n");
            String indexesBefore = text(connection, "SELECT count(*) FROM pg_index WHERE indrelid='user_notification'::regclass");
            sql(connection, migration());
            assertEquals(before, text(connection, "SELECT to_jsonb(n)::text FROM user_notification n"));
            assertEquals(indexesBefore, text(connection, "SELECT count(*) FROM pg_index WHERE indrelid='user_notification'::regclass"));
        }
    }

    @Test
    void migrationAddsMissingUniquenessButRejectsDuplicateRecordsWithoutDeletingThem() throws Exception {
        try (var connection = connection()) {
            sql(connection, "SET search_path TO " + schema);
            sql(connection, "DROP INDEX uq_user_notification_dedupe_key");
            sql(connection, migration());
            sql(connection, "DROP INDEX uq_user_notification_dedupe_key");
            sql(connection, """
                    INSERT INTO user_notification(recipient_id,type,title,body,dedupe_key,created_at,email_status,email_attempts)
                    SELECT gen_random_uuid(),'COURSE_ASSIGNED','t','b','duplicate',now(),'SENT',1 FROM generate_series(1,2)
                    """);
            assertThrows(SQLException.class, () -> sql(connection, migration()));
            assertEquals("2", text(connection, "SELECT count(*) FROM user_notification"));
        }
    }

    @Test
    void incompatibleExistingTableFailsWithoutChangingItsRecords() throws Exception {
        try (var connection = connection()) {
            sql(connection, "SET search_path TO " + schema);
            sql(connection, "ALTER TABLE user_notification DROP COLUMN email_attempts");
            assertThrows(SQLException.class, () -> sql(connection, migration()));
        }
    }

    @Test
    void concurrentIdenticalKeysCreateOneRecordAndLeaveBothTransactionsUsable() throws Exception {
        assertConcurrentDelivery(false);
        verifyNoInteractions(events);
    }

    @Test
    void concurrentEmailedNotificationsAnnounceOnlyTheWinningInsert() throws Exception {
        assertConcurrentDelivery(true);
        verify(events).publishEvent(any(NotificationCreatedEvent.class));
    }

    @Test
    void rolledBackKeyCanBeRetriedAndDistinctRecipientsAndTransitionsKeepTheirOwnRecords() {
        try (var session = sessions.openSession()) {
            session.beginTransaction();
            assertTrue(service(session).notifyInAppOnce("submission:1:" + recipient, recipient,
                    NotificationType.COURSE_ASSIGNED, "t", "b", null));
            session.getTransaction().rollback();
        }
        try (var session = sessions.openSession()) {
            session.beginTransaction();
            var service = service(session);
            assertTrue(service.notifyInAppOnce("submission:1:" + recipient, recipient, NotificationType.COURSE_ASSIGNED, "t", "b", null));
            assertFalse(service.notifyInAppOnce("submission:1:" + recipient, recipient, NotificationType.COURSE_ASSIGNED, "t", "b", null));
            assertTrue(service.notifyInAppOnce("submission:2:" + recipient, recipient, NotificationType.COURSE_ASSIGNED, "t", "b", null));
            UUID other = UUID.randomUUID();
            assertTrue(service.notifyInAppOnce("submission:1:" + other, other, NotificationType.COURSE_ASSIGNED, "t", "b", null));
            assertEquals(2, service.mine(recipient).getUnread());
            assertEquals(1, service.mine(other).getUnread());
            session.getTransaction().commit();
        }
        verifyNoInteractions(events);
    }

    @Test
    void malformedInsertionStillFailsInsteadOfBeingMistakenForADuplicate() {
        try (var session = sessions.openSession()) {
            session.beginTransaction();
            assertThrows(RuntimeException.class, () -> service(session).notifyInAppOnce("invalid", null,
                    NotificationType.COURSE_ASSIGNED, "t", "b", null));
            session.getTransaction().rollback();
        }
    }

    @Test
    void springWorkflowEventUsesTheWorkflowTransactionAndDoesNotQueueEmail() {
        var permissions=mock(com.tbm.careerpathlearning.repository.RoleAuthorityRepository.class);
        when(permissions.existsByRoleIdAndAuthorityName(anyLong(),any())).thenReturn(true);
        var role=new com.tbm.careerpathlearning.model.Role();role.setId(1L);
        var employee=new com.tbm.careerpathlearning.model.Staff();employee.setId(recipient);employee.setRole(role);
        employee.setAccountStatus(com.tbm.careerpathlearning.enums.StaffAccountStatus.ACTIVE);
        var period=new com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod();period.setId(2L);
        period.setStatus(com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus.UPCOMING);
        period.setParticipantsSnapshottedAt(java.time.OffsetDateTime.parse("2026-10-01T09:00:00Z"));
        period.setKpiSetupDeadline(java.time.LocalDate.of(2026,10,29));
        var participant=new com.tbm.careerpathlearning.model.ReviewPeriodParticipant();participant.setStaff(employee);participant.setReviewPeriod(period);
        var plan=new com.tbm.careerpathlearning.model.KpiPlan();plan.setId(3L);plan.setLevel(com.tbm.careerpathlearning.enums.KpiLevel.INDIVIDUAL);
        plan.setReviewPeriod(period);plan.setOwnerParticipant(participant);
        var event=new KpiNotificationEvents.Plan(plan,"APPROVED",UUID.randomUUID());
        var emailEvents=new java.util.concurrent.atomic.AtomicInteger();
        var sourcePeriods=mock(com.tbm.careerpathlearning.repository.AnnualKpiReviewPeriodRepository.class);
        when(sourcePeriods.findAllByStatus(com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus.UPCOMING)).thenReturn(java.util.List.of(period));
        var accounts=mock(com.tbm.careerpathlearning.repository.StaffRepository.class);
        when(accounts.findAllActiveWithRole()).thenReturn(java.util.List.of(employee));
        try (var context=new AnnotationConfigApplicationContext()) {
            context.register(Transactions.class);
            context.registerBean("transactionManager",org.springframework.transaction.PlatformTransactionManager.class,()->new JpaTransactionManager(sessions));
            context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-10-26T09:00:00Z"),ZoneOffset.UTC));
            context.registerBean(UserNotificationRepository.class,()-> {
                var em=SharedEntityManagerCreator.createSharedEntityManager(sessions);
                var insert=new UserNotificationInsertImpl();ReflectionTestUtils.setField(insert,"entityManager",em);
                return new JpaRepositoryFactory(em).getRepository(UserNotificationRepository.class,RepositoryFragments.just(insert));
            });
            context.registerBean(NotificationServiceImpl.class);
            context.registerBean(KpiWorkflowNotifications.class,()->new KpiWorkflowNotifications(context.getBean(NotificationService.class),
                    accounts,permissions,
                    mock(com.tbm.careerpathlearning.repository.ReviewPeriodParticipantRepository.class),
                    mock(com.tbm.careerpathlearning.repository.AnnualAppraisalRepository.class),
                    mock(AnnualAppraisalReadinessService.class),mock(PerformanceDepartmentResolver.class)));
            context.registerBean(KpiNotificationScheduleService.class,()->new KpiNotificationScheduleService(sourcePeriods,
                    mock(com.tbm.careerpathlearning.repository.ReviewPeriodParticipantRepository.class),
                    mock(com.tbm.careerpathlearning.repository.ReviewCheckpointRepository.class),
                    mock(com.tbm.careerpathlearning.repository.KpiAssessmentRepository.class),
                    mock(com.tbm.careerpathlearning.repository.AttitudeAssessmentRepository.class),
                    mock(com.tbm.careerpathlearning.repository.AnnualAppraisalRepository.class),
                    mock(com.tbm.careerpathlearning.repository.KpiPlanRepository.class),
                    mock(com.tbm.careerpathlearning.repository.KpiPlanVersionRepository.class),
                    mock(com.tbm.careerpathlearning.repository.KpiCentralResultRepository.class),
                    mock(com.tbm.careerpathlearning.repository.CompanyKpiResultDelegateRepository.class),
                    mock(com.tbm.careerpathlearning.repository.IndividualKpiAssistanceAuthorizationRepository.class),
                    mock(com.tbm.careerpathlearning.service.impl.KpiAssessmentServiceImpl.class),
                    mock(AnnualAppraisalReadinessService.class),context.getBean(KpiWorkflowNotifications.class),context.getBean(Clock.class)));
            context.addApplicationListener(applicationEvent->{
                if(applicationEvent instanceof PayloadApplicationEvent<?> payload && payload.getPayload() instanceof NotificationCreatedEvent) emailEvents.incrementAndGet();
            });
            context.refresh();
            var transaction=new TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->{
                context.publishEvent(event);throw new IllegalStateException("Appraisal save failed");
            }));
            var repository=context.getBean(UserNotificationRepository.class);
            assertEquals(0,repository.count());
            transaction.executeWithoutResult(status->{context.publishEvent(event);context.publishEvent(event);});
            assertEquals(1,repository.count());
            assertEquals("SKIPPED",repository.findAll().get(0).getEmailStatus());
            var scheduled=context.getBean(KpiNotificationScheduleService.class);
            assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->{
                scheduled.scanToday();throw new IllegalStateException("Scan transaction failed");
            }));
            assertEquals(1,repository.count());
            scheduled.scanToday();scheduled.scanToday();
            assertEquals(2,repository.count());
            assertTrue(repository.findAll().stream().allMatch(n->"SKIPPED".equals(n.getEmailStatus())));
            assertEquals(0,emailEvents.get());
        }
    }

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    static class Transactions {}

    private void assertConcurrentDelivery(boolean email) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> request = () -> {
                try (var session = sessions.openSession()) {
                    session.beginTransaction();
                    var service = service(session);
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    boolean created = email
                            ? service.notifyOnce("concurrent", recipient, NotificationType.COURSE_ASSIGNED, "t", "b", null)
                            : service.notifyInAppOnce("concurrent", recipient, NotificationType.COURSE_ASSIGNED, "t", "b", null);
                    assertEquals(1L, session.createNativeQuery("SELECT 1", Long.class).getSingleResult());
                    session.getTransaction().commit();
                    return created;
                }
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            try (var session = sessions.openSession()) {
                var rows = session.createQuery("from UserNotification", UserNotification.class).list();
                assertEquals(1, rows.size());
                assertEquals(email ? "PENDING" : "SKIPPED", rows.get(0).getEmailStatus());
                assertNull(rows.get(0).getReadAt());
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private NotificationServiceImpl service(Session session) {
        var insert = new UserNotificationInsertImpl();
        ReflectionTestUtils.setField(insert, "entityManager", session);
        var repository = new JpaRepositoryFactory(session).getRepository(UserNotificationRepository.class, RepositoryFragments.just(insert));
        var service = new NotificationServiceImpl();
        ReflectionTestUtils.setField(service, "userNotificationRepository", repository);
        ReflectionTestUtils.setField(service, "eventPublisher", events);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-10-26T01:00:00Z"), ZoneOffset.UTC));
        return service;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("APEX_NOTIFICATION_TEST_URL"), "notification_test", "");
    }

    private String migration() throws Exception {
        return new ClassPathResource("db/migration/annual-kpi/V50__notification_integration_foundation.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private void sql(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private String text(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
