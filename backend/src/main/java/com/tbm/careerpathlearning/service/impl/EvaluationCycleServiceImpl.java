package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.EvaluationCycleDto;
import com.tbm.careerpathlearning.enums.CycleStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.EvaluationCycle;
import com.tbm.careerpathlearning.repository.EvaluationCycleRepository;
import com.tbm.careerpathlearning.service.EvaluationCycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EvaluationCycleServiceImpl implements EvaluationCycleService {

    @Autowired
    private EvaluationCycleRepository evaluationCycleRepository;

    @Autowired
    private AppMapper appMapper;

    @Override
    @Transactional(readOnly = true)
    public EvaluationCycleDto getCurrentCycle() {

        // 1️⃣ If there is an OPEN cycle → return it
        Optional<EvaluationCycle> openCycle =
                evaluationCycleRepository.findFirstByStatus(CycleStatus.OPEN);

        if (openCycle.isPresent()) {
            return appMapper.toDto(openCycle.get());
        }

        // 2️⃣ Otherwise, return the nearest UPCOMING cycle
        return evaluationCycleRepository
                .findFirstByStatusOrderByStartDateAsc(CycleStatus.UPCOMING)
                .map(appMapper::toDto)
                .orElse(null);
    }


    @Override
    @Transactional
    public void openNewCycle(EvaluationCycleDto dto, UUID adminId) {

        evaluationCycleRepository.findByStatus(CycleStatus.OPEN)
                .ifPresent(c -> {
                    throw new BadRequestException("An evaluation cycle is already open");
                });

        EvaluationCycle cycle = new EvaluationCycle();
        cycle.setStartDate(dto.getStartDate());
        cycle.setEndDate(dto.getEndDate());
        cycle.setCreatedBy(adminId);
        cycle.setCreatedAt(LocalDateTime.now());

        if (dto.getStartDate().isEqual(LocalDate.now())) {
            cycle.setStatus(CycleStatus.OPEN);
            cycle.setOpenedAt(LocalDateTime.now());
        } else {
            cycle.setStatus(CycleStatus.UPCOMING);
        }

        evaluationCycleRepository.save(cycle);
    }

    @Override
    @Transactional
    public EvaluationCycleDto updateEvaluationCycle(
            Long cycleId,
            EvaluationCycleDto dto,
            UUID adminId) {

        EvaluationCycle cycle = evaluationCycleRepository.findById(cycleId)
                .orElseThrow(() -> new BadRequestException("Evaluation cycle not found"));

        // Cannot update CLOSED cycles
        if (cycle.getStatus() == CycleStatus.CLOSED) {
            throw new BadRequestException("Closed evaluation cycle cannot be updated");
        }

        // Null validation
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new BadRequestException("Start date and end date are required");
        }

        // Invalid date range
        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new BadRequestException("Start date cannot be after end date");
        }

        // OPEN cycle cannot be shortened to past
        if (cycle.getStatus() == CycleStatus.OPEN &&
                dto.getEndDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("End date cannot be in the past for an open cycle");
        }

        cycle.setStartDate(dto.getStartDate());
        cycle.setEndDate(dto.getEndDate());
        cycle.setUpdatedAt(LocalDateTime.now());
        cycle.setUpdatedBy(adminId);


        LocalDate today = LocalDate.now();

        if (dto.getStartDate().isAfter(today)) {
            cycle.setStatus(CycleStatus.UPCOMING);
            cycle.setOpenedAt(null); // important: remove misleading open timestamp
        } else {
            cycle.setStatus(CycleStatus.OPEN);

            if (cycle.getOpenedAt() == null) {
                cycle.setOpenedAt(LocalDateTime.now());
            }
        }

        EvaluationCycle saved = evaluationCycleRepository.save(cycle);

        return appMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationCycleDto> getEvaluationCycleHistory() {

        return evaluationCycleRepository.findAllByOrderByStartDateDesc()
                .stream()
                .map(appMapper::toDto)
                .toList();
    }

}
