package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.ReviewPeriodParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewPeriodParticipantRepository extends JpaRepository<ReviewPeriodParticipant, Long> {
    boolean existsByReviewPeriodId(Long reviewPeriodId);
    List<ReviewPeriodParticipant> findAllByReviewPeriodId(Long reviewPeriodId);
    Optional<ReviewPeriodParticipant> findByReviewPeriodIdAndStaffId(Long reviewPeriodId, UUID staffId);
    List<ReviewPeriodParticipant> findAllByReviewPeriodIdAndSuperiorId(Long reviewPeriodId, UUID superiorId);
    List<ReviewPeriodParticipant> findAllByStaffIdOrderByReviewPeriodStartDateDesc(UUID staffId);
    @org.springframework.data.jpa.repository.Query("select p from ReviewPeriodParticipant p where p.staff.manager.id=:superiorId "
        +"and p.staff.isDeleted=false and p.staff.accountStatus=com.tbm.careerpathlearning.enums.StaffAccountStatus.ACTIVE "
        +"and p.reviewPeriod.status in (com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus.UPCOMING,"
        +"com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus.OPEN) "
        +"and p.reviewPeriod.participantsSnapshottedAt is not null "
        +"order by p.reviewPeriod.startDate desc,p.staffName,p.id")
    List<ReviewPeriodParticipant> findCurrentSubordinateParticipants(UUID superiorId);
}
