package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.KpiRevisionDto;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import jakarta.persistence.Entity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.time.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises real flush/version behaviour without connecting to any configured database. */
class KpiRevisionPersistenceTest {
    static SessionFactory factory;
    private static final Set<String> TABLES=Set.of("annual_kpi_review_period","kpi_plan","kpi_plan_version","kpi","kpi_scoring_definition");
    private static class RevisionSchema implements SchemaFilterProvider,SchemaFilter {
        public SchemaFilter getCreateFilter(){return this;}
        public SchemaFilter getDropFilter(){return this;}
        public SchemaFilter getTruncatorFilter(){return this;}
        public SchemaFilter getMigrateFilter(){return this;}
        public SchemaFilter getValidateFilter(){return this;}
        public boolean includeNamespace(Namespace namespace){return true;}
        public boolean includeTable(Table table){return TABLES.contains(table.getName());}
        public boolean includeSequence(Sequence sequence){return false;}
    }

    @BeforeAll static void database() throws Exception {
        var registry=new StandardServiceRegistryBuilder()
            .applySetting("hibernate.connection.driver_class","org.h2.Driver")
            .applySetting("hibernate.connection.url","jdbc:h2:mem:revision_tokens;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            .applySetting("hibernate.hbm2ddl.auto","create-drop")
            .applySetting("hibernate.hbm2ddl.schema_filter_provider",new RevisionSchema())
            .applySetting("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
            .build();
        var sources=new MetadataSources(registry);
        var scanner=new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for(var bean:scanner.findCandidateComponents("com.tbm.careerpathlearning.model"))
            sources.addAnnotatedClass(Class.forName(bean.getBeanClassName()));
        var metadata=sources.buildMetadata();
        // Unrelated PostgreSQL-only tables are not needed for this isolated ORM regression.
        for(var namespace:metadata.getDatabase().getNamespaces())
            for(var table:namespace.getTables())
                for(var foreignKey:table.getForeignKeys().values())
                    if(!TABLES.contains(foreignKey.getReferencedTable().getName()))foreignKey.disableCreation();
        factory=metadata.buildSessionFactory();
    }
    @AfterAll static void close() {if(factory!=null)factory.close();}

    static Stream<Arguments> preparationPaths() {
        return Stream.of(ZoneOffset.UTC,ZoneId.of("Asia/Singapore")).flatMap(zone->Stream.of(
            Arguments.of(KpiLevel.DEPARTMENT,null,zone),
            Arguments.of(KpiLevel.INDIVIDUAL,KpiRevisionPreparationMode.SUPERIOR,zone),
            Arguments.of(KpiLevel.INDIVIDUAL,KpiRevisionPreparationMode.HR_MD,zone)));
    }
    @ParameterizedTest @MethodSource("preparationPaths")
    void preparationResponseCanBeUsedForFirstEditWithoutRefresh(KpiLevel level,KpiRevisionPreparationMode mode,ZoneId zone) {
        var fixture=new KpiRevisionServiceTest();fixture.setup();
        ReflectionTestUtils.setField(fixture.service,"clock",Clock.fixed(Instant.parse("2026-10-12T10:15:31.724801700Z"),zone));
        if(level==KpiLevel.DEPARTMENT)fixture.department();
        else fixture.individual(mode);
        var actor=mode==KpiRevisionPreparationMode.SUPERIOR?fixture.hod:fixture.hr;
        fixture.login(actor,level==KpiLevel.DEPARTMENT?KpiRevisionService.HR:
            mode==KpiRevisionPreparationMode.SUPERIOR?KpiRevisionService.SUPERIOR:KpiRevisionService.INDIVIDUAL);
        try(var em=factory.createEntityManager()) {
            var tx=em.getTransaction();tx.begin();
            // Only version/item persistence is relevant; scope and RBAC retain their existing fixtures.
            var period=fixture.period;period.setId(null);period.setName("Review "+UUID.randomUUID());em.persist(period);
            var plan=fixture.plan;plan.setId(null);plan.setDepartment(null);plan.setOwnerParticipant(null);
            plan.setOwnerParticipantId(null);plan.setCurrentVersion(null);plan.setCreatedAt(fixture.base.getEffectiveAt());
            plan.setUpdatedAt(plan.getCreatedAt());plan.setCreatedBy(actor);plan.setUpdatedBy(actor);em.persist(plan);
            var base=fixture.base;base.setId(null);base.setReviewPeriodId(period.getId());
            base.setCreatedAt(plan.getCreatedAt());base.setUpdatedAt(plan.getCreatedAt());base.setCreatedBy(actor);base.setUpdatedBy(actor);
            for(var k:base.getItems()){k.setId(null);k.setPlanId(plan.getId());k.setReviewPeriodId(period.getId());}
            em.persist(base);em.flush();
            var working=fixture.working;working.setId(null);working.setReviewPeriodId(period.getId());
            for(var k:working.getItems()){k.setId(null);k.setPlanId(plan.getId());k.setReviewPeriodId(period.getId());k.setPredecessorId(base.getItems().get(0).getId());}
            em.persist(working);em.flush();plan.setCurrentVersion(base);em.flush();tx.commit();
            var initialRequest=fixture.request();em.clear();
            // Each HTTP request starts with newly loaded entities and the repository's real lock mode.
            when(fixture.versions.lockById(working.getId())).thenAnswer(call->{
                var loaded=em.find(KpiPlanVersion.class,working.getId());
                if(level==KpiLevel.INDIVIDUAL) {
                    var participant=new ReviewPeriodParticipant();participant.setId(7L);participant.setStaff(fixture.staff.findById(fixture.other).orElseThrow());
                    loaded.getPlan().setOwnerParticipant(participant);
                }
                em.lock(loaded,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);return Optional.of(loaded);
            });
            when(fixture.versions.findById(working.getId())).thenReturn(Optional.of(working));
            var repository=new org.springframework.data.jpa.repository.support.SimpleJpaRepository<>(KpiPlanVersion.class,em);
            doAnswer(call->repository.saveAndFlush(call.getArgument(0))).when(fixture.versions).saveAndFlush(any());
            ReflectionTestUtils.setField(fixture.service,"entityManager",em);
            tx.begin();
            KpiRevisionDto prepared=fixture.service.prepare(working.getId(),initialRequest,actor);
            tx.commit();
            em.clear();
            var committed=em.find(KpiPlanVersion.class,working.getId());
            assertEquals(committed.getLockVersion(),prepared.getLockVersion(),"Preparation must return the committed token");
            em.clear();
            var edit=fixture.request();edit.setExpectedLockVersion(prepared.getLockVersion());
            edit.setItems(new ArrayList<>(prepared.getItems()));edit.getItems().get(0).setTarget("First saved edit");
            tx.begin();
            var saved=assertDoesNotThrow(()->fixture.service.update(working.getId(),edit,actor));
            tx.commit();
            em.clear();
            assertEquals(em.find(KpiPlanVersion.class,working.getId()).getLockVersion(),saved.getLockVersion(),"Saving must return the committed token");
            assertEquals("First saved edit",saved.getItems().get(0).getTarget());
            em.clear();tx.begin();
            edit.setExpectedLockVersion(saved.getLockVersion());edit.getItems().get(0).setTarget("Second saved edit");
            var next=assertDoesNotThrow(()->fixture.service.update(working.getId(),edit,actor));
            tx.commit();em.clear();
            assertEquals(em.find(KpiPlanVersion.class,working.getId()).getLockVersion(),next.getLockVersion());
            assertTrue(next.getLockVersion()>saved.getLockVersion(),"Item-only saves must advance the token even under a fixed clock");
            edit.setExpectedLockVersion(prepared.getLockVersion());
            em.clear();tx.begin();
            assertThrows(com.tbm.careerpathlearning.exception.KpiVersionConflictException.class,
                ()->fixture.service.update(working.getId(),edit,actor),"Genuinely stale requests must still be rejected");
            tx.rollback();
        } finally {fixture.cleanup();}
    }
}
