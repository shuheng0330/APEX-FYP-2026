package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import java.util.*;

/** Deliberately exposes reads only; no aggregate refresh or workflow repository is needed. */
public interface TeamPerformanceRepository extends Repository<ReviewPeriodParticipant, Long> {
    String SCOPE = """
        p.reviewPeriod.status<>com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus.DRAFT
        and p.reviewPeriod.participantsSnapshottedAt is not null
        and ((:team=true and manager.id=:actorId and p.staff.isDeleted=false)
          or (:team=false and (department.id=:departmentId or (:departmentId is null and department.id is null))))
        """;

    @Query("select distinct period from ReviewPeriodParticipant p join p.reviewPeriod period left join p.staff.manager manager left join p.department department where " + SCOPE
            + " order by period.endDate desc,period.id desc")
    List<AnnualKpiReviewPeriod> findPeriods(boolean team, UUID actorId, Long departmentId);

    @EntityGraph(attributePaths={"staff", "staff.manager", "reviewPeriod", "department", "employeeLevelConfiguration"})
    @Query("select p from ReviewPeriodParticipant p left join p.staff.manager manager left join p.department department where " + SCOPE + " and p.reviewPeriod.id in :periodIds order by p.id")
    List<ReviewPeriodParticipant> findPopulation(boolean team, UUID actorId, Long departmentId, Collection<Long> periodIds);

    interface Department { Long getId(); String getName(); }
    @Query("select department.id as id,max(p.departmentName) as name from ReviewPeriodParticipant p left join p.department department "
            + "where p.reviewPeriod.participantsSnapshottedAt is not null "
            + "and p.reviewPeriod.status<>com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus.DRAFT group by department.id order by department.id")
    List<Department> findDepartments();

    interface VersionSource { Long getParticipantId(); Long getPeriodId(); KpiLevel getLevel(); Long getVersionId(); }
    @Query("""
        select distinct x.participantId as participantId,x.reviewPeriodId as periodId,k.plan.level as level,k.version.id as versionId
        from EmployeeKpiAssignment x join x.kpi k
        where x.participantId in :participantIds and k.version.id=k.plan.currentVersion.id
          and ((k.plan.level=com.tbm.careerpathlearning.enums.KpiLevel.COMPANY
                and k.plan.status=com.tbm.careerpathlearning.enums.KpiPlanStatus.PUBLISHED)
            or (k.plan.level<>com.tbm.careerpathlearning.enums.KpiLevel.COMPANY
                and k.plan.status=com.tbm.careerpathlearning.enums.KpiPlanStatus.APPROVED))
        """)
    List<VersionSource> findFallbackVersions(Collection<Long> participantIds);

    interface PublishedSource { Long getVersionId(); Long getCheckpointId(); }
    @Query("select r.planVersion.id as versionId,r.checkpoint.id as checkpointId from KpiCentralResult r "
            + "where r.reviewPeriodId in :periodIds and r.status=com.tbm.careerpathlearning.enums.KpiCentralResultStatus.PUBLISHED")
    List<PublishedSource> findPublishedSources(Collection<Long> periodIds);

    interface IndividualPlan {
        Long getParticipantId(); KpiPlanStatus getStatus(); UUID getSubmittedToSuperiorId();
    }
    @Query("select p.ownerParticipantId as participantId,p.status as status,p.submittedToSuperiorId as submittedToSuperiorId "
            + "from KpiPlan p where p.ownerParticipantId in :participantIds and p.level=com.tbm.careerpathlearning.enums.KpiLevel.INDIVIDUAL")
    List<IndividualPlan> findIndividualPlans(Collection<Long> participantIds);

    interface ReturnedRevision { Long getParticipantId(); UUID getSuperiorId(); UUID getPreparedBy(); KpiRevisionPreparationMode getMode(); }
    @Query("""
        select v.plan.ownerParticipantId as participantId,v.revisionSuperiorId as superiorId,v.preparedBy as preparedBy,
               v.preparationMode as mode from KpiPlanVersion v
        where v.plan.ownerParticipantId in :participantIds and v.level=com.tbm.careerpathlearning.enums.KpiLevel.INDIVIDUAL
          and v.versionNumber>1 and v.status=com.tbm.careerpathlearning.enums.KpiVersionStatus.RETURNED
        """)
    List<ReturnedRevision> findReturnedRevisions(Collection<Long> participantIds);
}
