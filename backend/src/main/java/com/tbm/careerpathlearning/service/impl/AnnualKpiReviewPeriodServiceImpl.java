package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AnnualKpiReviewPeriodMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
public class AnnualKpiReviewPeriodServiceImpl implements AnnualKpiReviewPeriodService {
    private static final List<AnnualKpiReviewPeriodStatus> ACTIVE =
            List.of(AnnualKpiReviewPeriodStatus.UPCOMING, AnnualKpiReviewPeriodStatus.OPEN);
    private final AnnualKpiReviewPeriodRepository periods;
    private final ReviewPeriodRoleConfigurationRepository configurations;
    private final ReviewCheckpointRepository checkpoints;
    private final ReviewPeriodParticipantRepository participants;
    private final RoleRepository roles;
    private final AnnualKpiReviewPeriodMapper mapper;
    private final AnnualReviewPeriodConfigurationValidator validator;
    private final ReviewCheckpointGenerator generator;
    private final Clock clock;

    public AnnualKpiReviewPeriodServiceImpl(AnnualKpiReviewPeriodRepository periods,
            ReviewPeriodRoleConfigurationRepository configurations, ReviewCheckpointRepository checkpoints,
            ReviewPeriodParticipantRepository participants, RoleRepository roles, AnnualKpiReviewPeriodMapper mapper,
            AnnualReviewPeriodConfigurationValidator validator, ReviewCheckpointGenerator generator,
            @Qualifier("annualKpiReviewClock") Clock clock) {
        this.periods = periods;
        this.configurations = configurations;
        this.checkpoints = checkpoints;
        this.participants = participants;
        this.roles = roles;
        this.mapper = mapper;
        this.validator = validator;
        this.generator = generator;
        this.clock = clock;
    }

    @Override
    public AnnualKpiReviewPeriodDto create(AnnualKpiReviewPeriodRequest request, boolean publish, UUID actor) {
        periods.lockConfiguration();
        AnnualKpiReviewPeriod period = new AnnualKpiReviewPeriod();
        apply(request, period);
        List<ReviewPeriodRoleConfiguration> selected = resolveRoles(period, request, List.of());
        validate(period, selected, publish);
        List<ReviewCheckpoint> schedule = publish ? generate(period, selected) : List.of();
        if (publish) markPublished(period);
        period.setCreatedBy(actor);
        period.setUpdatedBy(actor);
        periods.saveAndFlush(period);
        configurations.saveAllAndFlush(selected);
        checkpoints.saveAllAndFlush(schedule);
        return details(period);
    }

    @Override
    public AnnualKpiReviewPeriodDto update(Long id, AnnualKpiReviewPeriodRequest request, UUID actor) {
        periods.lockConfiguration();
        AnnualKpiReviewPeriod period = requirePeriod(id);
        requireEditable(period);
        requireNoParticipants(id);
        List<ReviewPeriodRoleConfiguration> old = configurations.findAllByReviewPeriodIdOrderByIdAsc(id);
        apply(request, period);
        List<ReviewPeriodRoleConfiguration> selected = resolveRoles(period, request, old);
        boolean published = period.getStatus() == AnnualKpiReviewPeriodStatus.UPCOMING;
        validate(period, selected, published);
        List<ReviewCheckpoint> schedule = published ? generate(period, selected) : List.of();
        if (published) markPublished(period);
        period.setUpdatedBy(actor);
        checkpoints.deleteAllByReviewPeriodId(id);
        configurations.deleteAllByReviewPeriodId(id);
        // Flush removals before inserting replacements with the same unique keys.
        configurations.flush();
        periods.saveAndFlush(period);
        configurations.saveAllAndFlush(selected);
        checkpoints.saveAllAndFlush(schedule);
        return details(period);
    }

    @Override
    public AnnualKpiReviewPeriodDto publish(Long id, UUID actor) {
        periods.lockConfiguration();
        AnnualKpiReviewPeriod period = requirePeriod(id);
        if (period.getStatus() != AnnualKpiReviewPeriodStatus.DRAFT) {
            throw new BadRequestException("Only a Draft review period can be published");
        }
        List<ReviewPeriodRoleConfiguration> selected = configurations.findAllByReviewPeriodIdOrderByIdAsc(id);
        if (selected.stream().anyMatch(c -> c.getRole().isDeleted())) {
            throw new BadRequestException("An applicable role has been deleted; update the Draft configuration");
        }
        validate(period, selected, true);
        List<ReviewCheckpoint> schedule = generate(period, selected);
        markPublished(period);
        period.setUpdatedBy(actor);
        periods.saveAndFlush(period);
        checkpoints.deleteAllByReviewPeriodId(id);
        checkpoints.flush();
        checkpoints.saveAllAndFlush(schedule);
        return details(period);
    }

    @Override
    @Transactional(readOnly = true)
    public AnnualKpiReviewPeriodDto get(Long id) {
        return details(requirePeriod(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnualKpiReviewPeriodDto> list() {
        return periods.findAllByOrderByStartDateDescIdDesc().stream().map(this::details).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnualKpiReviewPeriodDto.RoleConfiguration> availableRoles() {
        return roles.findAllByIsDeletedIsFalse().stream().map(role -> {
            ReviewPeriodRoleConfiguration configuration = new ReviewPeriodRoleConfiguration();
            configuration.setRole(role);
            configuration.setReviewFrequency(ReviewPeriodRoleConfiguration.resolveFrequency(role, null));
            return mapper.toDto(configuration);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AnnualKpiReviewPeriodDto preview(AnnualKpiReviewPeriodRequest request, Long excludedPeriodId) {
        AnnualKpiReviewPeriod period = new AnnualKpiReviewPeriod();
        List<ReviewPeriodRoleConfiguration> old = List.of();
        if (excludedPeriodId != null) {
            requireEditable(requirePeriod(excludedPeriodId));
            old = configurations.findAllByReviewPeriodIdOrderByIdAsc(excludedPeriodId);
            period.setId(excludedPeriodId);
        }
        apply(request, period);
        List<ReviewPeriodRoleConfiguration> selected = resolveRoles(period, request, old);
        validate(period, selected, true);
        markPublished(period);
        AnnualKpiReviewPeriodDto dto = mapper.toDto(period);
        // A preview is not a saved period and must not claim a persisted identifier/reference.
        dto.setId(null);
        dto.setReferenceNumber(null);
        dto.setOpenedAt(null);
        dto.setRoleConfigurations(selected.stream().map(mapper::toDto).toList());
        dto.setCheckpoints(generate(period, selected).stream().map(mapper::toDto).toList());
        return dto;
    }

    @Override
    public void delete(Long id) {
        periods.lockConfiguration();
        AnnualKpiReviewPeriod period = requirePeriod(id);
        requireEditable(period);
        requireNoParticipants(id);
        checkpoints.deleteAllByReviewPeriodId(id);
        configurations.deleteAllByReviewPeriodId(id);
        configurations.flush();
        periods.delete(period);
        periods.flush();
    }

    @Override
    public void openDuePeriods() {
        periods.lockConfiguration();
        for (AnnualKpiReviewPeriod period : periods.findAllByStatusAndStartDateLessThanEqual(
                AnnualKpiReviewPeriodStatus.UPCOMING, LocalDate.now(clock))) {
            if (overlaps(period)) {
                log.error("Cannot open annual KPI review period {}: conflicting published period", period.getId());
                continue;
            }
            markPublished(period);
            periods.saveAndFlush(period);
        }
        // End Date is the measurement cut-off, not an automatic closure trigger.
    }

    private void apply(AnnualKpiReviewPeriodRequest request, AnnualKpiReviewPeriod period) {
        if (request == null) throw new BadRequestException("Review period configuration is required");
        mapper.updateConfiguration(request, period);
        if (period.getName() != null) period.setName(period.getName().strip());
    }

    private List<ReviewPeriodRoleConfiguration> resolveRoles(AnnualKpiReviewPeriod period,
            AnnualKpiReviewPeriodRequest request, List<ReviewPeriodRoleConfiguration> old) {
        if (request.getRoleConfigurations() == null) {
            throw new BadRequestException("Applicable role configuration is required");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (var item : request.getRoleConfigurations()) {
            if (item == null || item.getRoleId() == null || item.getRoleId() <= 0 || !ids.add(item.getRoleId())) {
                throw new BadRequestException("Applicable roles must have valid, unique role IDs");
            }
        }
        Map<Long, Role> applicable = new HashMap<>();
        if (!ids.isEmpty()) roles.findAllByIdInAndIsDeletedIsFalse(ids).forEach(role -> applicable.put(role.getId(), role));
        if (applicable.size() != ids.size()) throw new BadRequestException("An applicable role does not exist or has been deleted");
        Map<Long, ReviewFrequency> previous = new HashMap<>();
        old.forEach(c -> previous.put(c.getRole().getId(), c.getReviewFrequency()));
        List<ReviewPeriodRoleConfiguration> selected = new ArrayList<>();
        for (var item : request.getRoleConfigurations()) {
            Role role = applicable.get(item.getRoleId());
            ReviewPeriodRoleConfiguration configuration = new ReviewPeriodRoleConfiguration();
            configuration.setReviewPeriod(period);
            configuration.setRole(role);
            ReviewFrequency frequency = item.getReviewFrequency() != null
                    ? item.getReviewFrequency() : previous.get(item.getRoleId());
            configuration.setReviewFrequency(ReviewPeriodRoleConfiguration.resolveFrequency(role, frequency));
            selected.add(configuration);
        }
        return selected;
    }

    private void validate(AnnualKpiReviewPeriod period, List<ReviewPeriodRoleConfiguration> selected, boolean publish) {
        try {
            if (publish) validator.validateForPublication(period);
            else validator.validateDraft(period);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
        if (period.getName() != null && (period.getId() == null ? periods.existsByName(period.getName())
                : periods.existsByNameAndIdNot(period.getName(), period.getId()))) {
            throw new BadRequestException("Review period name already exists");
        }
        if (publish) {
            if (selected.isEmpty()) throw new BadRequestException("At least one applicable role is required before publishing");
            if (overlaps(period)) throw new BadRequestException("Dates overlap an Upcoming or Open Annual KPI Review Period");
        }
    }

    private boolean overlaps(AnnualKpiReviewPeriod period) {
        return periods.existsOverlappingPeriod(period.getStartDate(), period.getEndDate(), period.getId(), ACTIVE);
    }

    private List<ReviewCheckpoint> generate(AnnualKpiReviewPeriod period,
            List<ReviewPeriodRoleConfiguration> selected) {
        try {
            return selected.stream().map(ReviewPeriodRoleConfiguration::getReviewFrequency).distinct()
                    .flatMap(frequency -> generator.generate(period, frequency).stream()).toList();
        } catch (IllegalArgumentException | DateTimeException ex) {
            throw new BadRequestException("Invalid assessment schedule: " + ex.getMessage());
        }
    }

    private void markPublished(AnnualKpiReviewPeriod period) {
        if (period.getStartDate().isAfter(LocalDate.now(clock))) {
            period.setStatus(AnnualKpiReviewPeriodStatus.UPCOMING);
        } else {
            period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
            period.setOpenedAt(OffsetDateTime.now(clock));
        }
    }

    private AnnualKpiReviewPeriod requirePeriod(Long id) {
        return periods.findById(id).orElseThrow(() -> new BadRequestException("Annual KPI Review Period not found"));
    }

    private void requireEditable(AnnualKpiReviewPeriod period) {
        boolean dueToOpen = period.getStatus() == AnnualKpiReviewPeriodStatus.UPCOMING
                && !period.getStartDate().isAfter(LocalDate.now(clock));
        if (!List.of(AnnualKpiReviewPeriodStatus.DRAFT, AnnualKpiReviewPeriodStatus.UPCOMING)
                .contains(period.getStatus()) || dueToOpen) {
            throw new BadRequestException("Only Draft or not-yet-open Upcoming review periods can be modified or deleted");
        }
    }

    private void requireNoParticipants(Long id) {
        if (participants.existsByReviewPeriodId(id)) {
            throw new BadRequestException("This review period already contains participant records; they cannot be discarded or resnapshotted");
        }
    }

    private AnnualKpiReviewPeriodDto details(AnnualKpiReviewPeriod period) {
        AnnualKpiReviewPeriodDto dto = mapper.toDto(period);
        dto.setRoleConfigurations(configurations.findAllByReviewPeriodIdOrderByIdAsc(period.getId())
                .stream().map(mapper::toDto).toList());
        dto.setCheckpoints(checkpoints.findAllByReviewPeriodIdOrderByReviewFrequencyAscSequenceNumberAsc(period.getId())
                .stream().map(mapper::toDto).toList());
        return dto;
    }
}
