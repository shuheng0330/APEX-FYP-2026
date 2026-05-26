package com.tbm.careerpathlearning.scheduler;

import com.tbm.careerpathlearning.enums.CycleStatus;
import com.tbm.careerpathlearning.model.EvaluationCycle;
import com.tbm.careerpathlearning.repository.EvaluationCycleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class EvaluationCycleAutoScheduler {

    @Autowired
    private EvaluationCycleRepository evaluationCycleRepository;

    public EvaluationCycleAutoScheduler(EvaluationCycleRepository evaluationCycleRepository) {
        this.evaluationCycleRepository = evaluationCycleRepository;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void autoOpenAndCloseCycles() {

        LocalDate today = LocalDate.now();

        // 1️⃣ Auto-open UPCOMING cycles (00:00 start day)
        List<EvaluationCycle> upcomingToOpen =
                evaluationCycleRepository
                        .findAllByStatusAndStartDateLessThanEqual(
                                CycleStatus.UPCOMING, today);

        for (EvaluationCycle cycle : upcomingToOpen) {

            // Manual override protection
            if (cycle.getOpenedAt() != null) {
                continue;
            }

            cycle.setStatus(CycleStatus.OPEN);
            cycle.setOpenedAt(LocalDateTime.now());
            evaluationCycleRepository.save(cycle);

            log.info("Evaluation cycle {} auto-opened on {}", cycle.getId(), today);
        }

        // 2️⃣ Auto-close OPEN cycles (after end day finishes)
        List<EvaluationCycle> openToClose =
                evaluationCycleRepository
                        .findAllByStatusAndEndDateBefore(
                                CycleStatus.OPEN, today);

        for (EvaluationCycle cycle : openToClose) {

            // Manual override protection
            if (cycle.getClosedAt() != null) {
                continue;
            }

            cycle.setStatus(CycleStatus.CLOSED);
            cycle.setClosedAt(LocalDateTime.now());
            evaluationCycleRepository.save(cycle);

            log.info("Evaluation cycle {} auto-closed on {}", cycle.getId(), today);
        }
    }


}
