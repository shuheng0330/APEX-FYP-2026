package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.KpiCheckpointAggregateService;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.persistence.Entity;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.service.*;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="APEX_PHASE2_POSTGRES_TEST",matches="true")
class CentralKpiResultJpaPostgresTest {
    @Test void mappedEntitiesAndScopedRepositoriesRoundTripWithoutTouchingPublicRecords(@TempDir Path directory) throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var schema="apex_central_jpa_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                var tables=new ArrayList<String>();
                try(var s=c.createStatement();var rows=s.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'")) {
                    while(rows.next())tables.add(rows.getString(1));
                }
                assertTrue(tables.contains("kpi_central_result"),"Apply V46 with the backed-up isolated runner before this JPA test");
                var foundation=Set.of("kpi_central_result","kpi_central_result_item","kpi_central_result_evidence","company_kpi_result_delegate","kpi_checkpoint_result");
                for(var table:tables)if(!foundation.contains(table))sql(c,"CREATE TABLE "+schema+".\""+table+"\" (LIKE public.\""+table+"\" INCLUDING ALL)");
                // Serial defaults copied by LIKE must not advance sequences in the public schema.
                var serialColumns=new ArrayList<String[]>();
                try(var statement=c.prepareStatement("SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=? AND column_default LIKE 'nextval(%'")) {
                    statement.setString(1,schema);
                    try(var rows=statement.executeQuery()){while(rows.next())serialColumns.add(new String[]{rows.getString(1),rows.getString(2)});}
                }
                for(int i=0;i<serialColumns.size();i++) {
                    var sequence="fixture_serial_"+i;sql(c,"CREATE SEQUENCE "+sequence);
                    sql(c,"ALTER TABLE \""+serialColumns.get(i)[0]+"\" ALTER COLUMN \""+serialColumns.get(i)[1]+"\" SET DEFAULT nextval('"+schema+"."+sequence+"'::regclass)");
                }
                sql(c,"ALTER TABLE annual_kpi_review_period DROP COLUMN assessment_model");
                sql(c,"ALTER TABLE kpi_assessment DROP COLUMN assessment_model");
                sql(c,"ALTER TABLE kpi_assessment ADD CONSTRAINT ck_assessment_review CHECK(status<>'REVIEWED' OR checkpoint_score IS NOT NULL)");
                sql(c,"ALTER TABLE kpi DROP CONSTRAINT IF EXISTS uq_kpi_central_version");
                sql(c,new ClassPathResource("db/migration/annual-kpi/V46__central_kpi_result_foundation.sql")
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                var config=new Configuration();config.setProperty("hibernate.connection.url",url);
                config.setProperty("hibernate.connection.username",env.get("DB_USER"));config.setProperty("hibernate.connection.password",env.get("DB_PASS"));
                config.setProperty("hibernate.hbm2ddl.auto","none");config.setProperty("hibernate.default_schema",schema);
                config.setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
                var scanner=new ClassPathScanningCandidateComponentProvider(false);scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
                for(var candidate:scanner.findCandidateComponents("com.tbm.careerpathlearning.model"))config.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
                try(var sf=config.buildSessionFactory();var em=sf.withOptions().connection(c).openSession()) {
                    em.beginTransaction();var now=OffsetDateTime.parse("2027-10-13T12:00:00+08:00");
                    var actor=new Staff();actor.setId(UUID.randomUUID());actor.setName("Isolated result owner");actor.setEmail("isolated@example.test");
                    actor.setCreatedAt(now);actor.setUpdatedAt(now);actor.setAccountStatus(StaffAccountStatus.ACTIVE);em.persist(actor);
                    var period=new AnnualKpiReviewPeriod();
                    Mappers.getMapper(AnnualKpiReviewPeriodMapper.class).updateConfiguration(AnnualKpiReviewPeriodServiceImplTest.validRequest(),period);
                    period.setName("Isolated central period");
                    period.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);em.persist(period);
                    var checkpoint=new ReviewCheckpoint();checkpoint.setReviewPeriod(period);checkpoint.setReviewFrequency(ReviewFrequency.MONTHLY);
                    checkpoint.setSequenceNumber(1);checkpoint.setStartDate(LocalDate.of(2027,9,1));checkpoint.setEndDate(LocalDate.of(2027,9,30));
                    checkpoint.setSelfAssessmentDeadline(LocalDate.of(2027,10,5));checkpoint.setSuperiorAssessmentDeadline(LocalDate.of(2027,10,10));em.persist(checkpoint);
                    var plan=new KpiPlan();plan.setReviewPeriod(period);plan.setLevel(KpiLevel.COMPANY);plan.setCreatedBy(actor.getId());plan.setUpdatedBy(actor.getId());
                    plan.setCreatedAt(now);plan.setUpdatedAt(now);em.persist(plan);
                    var version=new KpiPlanVersion();version.setPlan(plan);version.setReviewPeriodId(period.getId());version.setLevel(KpiLevel.COMPANY);
                    version.setVersionNumber(1);version.setCreatedBy(actor.getId());version.setUpdatedBy(actor.getId());version.setCreatedAt(now);version.setUpdatedAt(now);em.persist(version);
                    var kpi=new Kpi();kpi.setPlan(plan);kpi.setPlanId(plan.getId());kpi.setReviewPeriodId(period.getId());kpi.setVersion(version);
                    kpi.setName("Central revenue");kpi.setTarget("100 units");kpi.setWeightage(new BigDecimal("100"));
                    for(int point=1;point<=5;point++)kpi.getScoringDefinitions().put(point,"Definition "+point);
                    em.persist(kpi);
                    version.getItems().add(kpi);plan.setCurrentVersion(version);em.flush();
                    version.setStatus(KpiVersionStatus.EFFECTIVE);version.setEffectiveAt(now);
                    plan.setStatus(KpiPlanStatus.PUBLISHED);plan.setPublishedAt(now);plan.setPublishedBy(actor.getId());plan.setPublishedLate(false);
                    var level=new EmployeeLevel();level.setCode("ISOLATED");level.setName("Isolated level");level.setDisplayOrder(1);
                    level.setDefaultCompanyKpiWeight(new BigDecimal("100"));level.setDefaultDepartmentKpiWeight(BigDecimal.ZERO);
                    level.setDefaultIndividualKpiWeight(BigDecimal.ZERO);em.persist(level);
                    var weights=new ReviewPeriodEmployeeLevelConfiguration();weights.setReviewPeriod(period);weights.setEmployeeLevel(level);
                    weights.setCompanyKpiWeight(new BigDecimal("100"));weights.setDepartmentKpiWeight(BigDecimal.ZERO);weights.setIndividualKpiWeight(BigDecimal.ZERO);em.persist(weights);
                    var participant=new ReviewPeriodParticipant();participant.setReviewPeriod(period);participant.setStaff(actor);
                    participant.setReviewFrequency(ReviewFrequency.MONTHLY);participant.setEmployeeLevelConfiguration(weights);
                    participant.setEmployeeLevelConfigurationId(weights.getId());participant.setCreatedAt(now);em.persist(participant);
                    var assignment=new EmployeeKpiAssignment();assignment.setKpiId(kpi.getId());assignment.setParticipantId(participant.getId());
                    assignment.setReviewPeriodId(period.getId());assignment.setAssignedAt(now);em.persist(assignment);em.flush();
                    var result=new KpiCentralResult();result.setPlanVersion(version);result.setReviewPeriodId(period.getId());result.setCheckpoint(checkpoint);
                    result.setLevel(KpiLevel.COMPANY);result.setReviewFrequency(ReviewFrequency.MONTHLY);result.setEditionNumber(1);
                    result.setCreatedBy(actor.getId());result.setUpdatedBy(actor.getId());result.setCreatedAt(now);result.setUpdatedAt(now);
                    var item=new KpiCentralResultItem();item.setResult(result);item.setKpi(kpi);item.setPlanVersionId(version.getId());result.getItems().add(item);
                    var factory=new JpaRepositoryFactory(em);var repo=factory.getRepository(KpiCentralResultRepository.class);repo.saveAndFlush(result);
                    var id=result.getId();em.clear();var loaded=repo.lockById(id).orElseThrow();assertNull(loaded.getItems().get(0).getPoint());
                    var mapper=Mappers.getMapper(KpiCentralResultMapper.class);
                    ReflectionTestUtils.setField(mapper,"kpiPlanMapper",Mappers.getMapper(KpiPlanMapper.class));
                    assertEquals("Central revenue",mapper.toDto(loaded).getItems().get(0).getKpi().getName());
                    long token=loaded.getLockVersion();loaded.getItems().get(0).setPoint(4);loaded.getItems().get(0).setActualAchievement("80 units");
                    loaded.setUpdatedAt(now.plusSeconds(1));repo.saveAndFlush(loaded);assertTrue(loaded.getLockVersion()>token);
                    em.clear();assertEquals(4,repo.findById(id).orElseThrow().getItems().get(0).getPoint());
                    assertEquals(1,repo.findAllByReviewPeriodIdOrderByUpdatedAtDescIdDesc(period.getId()).size());
                    assertTrue(repo.existsByPlanVersionIdAndCheckpointId(version.getId(),checkpoint.getId()));
                    assertEquals(1,repo.eligibleParticipants(version.getId(),checkpoint.getId()));
                    var aggregate=new KpiCheckpointResult();aggregate.setParticipantId(participant.getId());aggregate.setReviewPeriodId(period.getId());
                    aggregate.setCheckpointId(checkpoint.getId());aggregate.setReviewFrequency(ReviewFrequency.MONTHLY);aggregate.setCompanyVersionId(version.getId());
                    var aggregates=factory.getRepository(KpiCheckpointResultRepository.class);aggregates.saveAndFlush(aggregate);em.clear();
                    var pending=aggregates.lockByScope(participant.getId(),checkpoint.getId()).orElseThrow();
                    assertNull(pending.getScore());assertTrue(pending.getCalculationHistory().isEmpty());
                    assertEquals(1,factory.getRepository(KpiPlanVersionRepository.class).findAllByReviewPeriodIdOrderByVersionNumberDescIdDesc(period.getId()).size());
                    var delegate=new CompanyKpiResultDelegate();delegate.setReviewPeriodId(period.getId());delegate.setStaffId(actor.getId());
                    delegate.setAssignedBy(actor.getId());delegate.setAssignedAt(now);
                    var delegates=factory.getRepository(CompanyKpiResultDelegateRepository.class);delegates.saveAndFlush(delegate);
                    assertTrue(delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(period.getId(),actor.getId()));
                    delegate.setRevokedBy(actor.getId());delegate.setRevokedAt(now.plusSeconds(1));delegates.saveAndFlush(delegate);em.clear();
                    assertFalse(delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(period.getId(),actor.getId()));
                    var fileRepo=factory.getRepository(KpiCentralResultEvidenceRepository.class);
                    var storage=new KpiAssessmentEvidenceStorage(directory.toString(),10485760);
                    var service=new KpiCentralResultServiceImpl(factory.getRepository(AnnualKpiReviewPeriodRepository.class),
                            factory.getRepository(ReviewCheckpointRepository.class),factory.getRepository(KpiPlanVersionRepository.class),repo,
                            delegates,factory.getRepository(StaffRepository.class),org.mockito.Mockito.mock(PerformanceDepartmentResolver.class),
                            mapper,Mappers.getMapper(KpiPlanMapper.class),Mappers.getMapper(AnnualKpiReviewPeriodMapper.class),
                            Clock.fixed(now.toInstant(),ZoneId.of("Asia/Singapore")),factory.getRepository(KpiCentralResultItemRepository.class),fileRepo,storage,
                            org.mockito.Mockito.mock(KpiCheckpointAggregateService.class));
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor.getId(),null,
                            List.of(new SimpleGrantedAuthority("CAN_MANAGE_COMPANY_KPI"))));
                    var current=repo.findById(id).orElseThrow();var currentItem=current.getItems().get(0);
                    byte[] image;try(var output=new ByteArrayOutputStream()) {
                        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);image=output.toByteArray();
                    }
                    var upload=service.upload(currentItem.getId(),current.getLockVersion(),new MockMultipartFile("file","evidence.png","image/png",image),actor.getId());
                    var decision=new KpiCentralResultDecisionRequest();decision.setExpectedLockVersion(upload.resultLockVersion());
                    var published=service.publish(id,decision,actor.getId());em.clear();
                    assertEquals(published.getLockVersion(),repo.findById(id).orElseThrow().getLockVersion());
                    assertEquals(KpiCentralResultStatus.PUBLISHED,published.getStatus());
                    assertEquals(actor.getId(),published.getPublishedBy());assertEquals(now.toInstant(),published.getPublishedAt().toInstant());
                    decision.setExpectedLockVersion(published.getLockVersion());decision.setReason("Correct achievement");
                    var correction=service.correct(id,decision,actor.getId());em.clear();
                    assertEquals(KpiCentralResultStatus.PUBLISHED,repo.findById(id).orElseThrow().getStatus());
                    var inherited=correction.getItems().get(0).getEvidence().get(0);assertNotEquals(upload.evidence().getId(),inherited.getId());
                    var originalFile=fileRepo.findById(upload.evidence().getId()).orElseThrow();var copiedFile=fileRepo.findById(inherited.getId()).orElseThrow();
                    assertNotEquals(originalFile.getStorageKey(),copiedFile.getStorageKey());
                    assertArrayEquals(image,storage.resource(originalFile.getStorageKey()).getContentAsByteArray());
                    assertArrayEquals(image,storage.resource(copiedFile.getStorageKey()).getContentAsByteArray());
                    var update=new KpiCentralResultRequest();update.setPlanVersionId(version.getId());update.setCheckpointId(checkpoint.getId());
                    update.setExpectedLockVersion(correction.getLockVersion());var answer=new KpiCentralResultRequest.Answer();
                    answer.setKpiId(kpi.getId());answer.setActualAchievement("90 units");answer.setPoint(5);update.setItems(List.of(answer));
                    var changed=service.update(correction.getId(),update,actor.getId());em.clear();
                    decision.setExpectedLockVersion(changed.getLockVersion());var corrected=service.publish(changed.getId(),decision,actor.getId());em.clear();
                    var previous=repo.findById(id).orElseThrow();assertEquals(KpiCentralResultStatus.SUPERSEDED,previous.getStatus());
                    assertEquals(4,previous.getItems().get(0).getPoint());assertEquals("80 units",previous.getItems().get(0).getActualAchievement());
                    assertEquals(published.getPublishedAt().toInstant(),previous.getPublishedAt().toInstant());
                    assertEquals(KpiCentralResultStatus.PUBLISHED,corrected.getStatus());assertEquals(5,corrected.getItems().get(0).getPoint());
                    assertEquals(2,service.history(id,actor.getId()).size());
                    var savepoint=c.setSavepoint();
                    try {assertEquals("23514",assertThrows(SQLException.class,()->sql(c,"UPDATE kpi_central_result_item SET point=1 WHERE result_id="+id)).getSQLState());}
                    finally {c.rollback(savepoint);c.releaseSavepoint(savepoint);}
                    em.getTransaction().rollback();
                }
            } finally {SecurityContextHolder.clearContext();if(!c.isClosed())c.rollback();}
        }
    }
    private void sql(Connection c,String query)throws SQLException {try(var s=c.createStatement()){s.execute(query);}}
}
