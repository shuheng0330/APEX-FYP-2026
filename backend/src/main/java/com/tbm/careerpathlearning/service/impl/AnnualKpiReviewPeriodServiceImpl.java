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
    private final EmployeeLevelRepository levels;
    private final ReviewPeriodEmployeeLevelConfigurationRepository levelConfigurations;
    private final AnnualKpiReviewPeriodMapper mapper;
    private final AnnualReviewPeriodConfigurationValidator validator;
    private final ReviewCheckpointGenerator generator;
    private final Clock clock;
    private final ReviewPeriodEnrolmentService enrolment;
    private final KpiPlanRepository kpiPlans;

    public AnnualKpiReviewPeriodServiceImpl(AnnualKpiReviewPeriodRepository periods,
            ReviewPeriodRoleConfigurationRepository configurations, ReviewCheckpointRepository checkpoints,
            ReviewPeriodParticipantRepository participants, RoleRepository roles, AnnualKpiReviewPeriodMapper mapper,
            AnnualReviewPeriodConfigurationValidator validator, ReviewCheckpointGenerator generator,
            @Qualifier("annualKpiReviewClock") Clock clock, EmployeeLevelRepository levels,
            ReviewPeriodEmployeeLevelConfigurationRepository levelConfigurations,
            ReviewPeriodEnrolmentService enrolment, KpiPlanRepository kpiPlans) {
        this.periods = periods;
        this.configurations = configurations;
        this.checkpoints = checkpoints;
        this.participants = participants;
        this.roles = roles;
        this.mapper = mapper;
        this.validator = validator;
        this.generator = generator;
        this.clock = clock;
        this.levels = levels;
        this.levelConfigurations = levelConfigurations;
        this.enrolment = enrolment;
        this.kpiPlans = kpiPlans;
    }

    @Override
    public AnnualKpiReviewPeriodDto create(AnnualKpiReviewPeriodRequest request, boolean publish, UUID actor) {
        periods.lockConfiguration();
        AnnualKpiReviewPeriod period = new AnnualKpiReviewPeriod();
        apply(request, period);
        List<ReviewPeriodEmployeeLevelConfiguration> weights = resolveWeights(period, request, null);
        List<ReviewPeriodRoleConfiguration> selected = resolveRoles(period, request, previousRoles(period));
        bindLevels(period, selected, weights, false);
        validate(period, selected, weights, publish);
        List<ReviewCheckpoint> schedule = publish ? generate(period, selected) : List.of();
        if (publish) markPublished(period);
        period.setCreatedBy(actor);
        period.setUpdatedBy(actor);
        periods.saveAndFlush(period);
        levelConfigurations.saveAllAndFlush(weights);
        bindLevels(period, selected, weights, false);
        configurations.saveAllAndFlush(selected);
        checkpoints.saveAllAndFlush(schedule);
        if (publish) { enrolment.enrol(period, selected); periods.saveAndFlush(period); }
        return details(period);
    }

    @Override
    public AnnualKpiReviewPeriodDto update(Long id, AnnualKpiReviewPeriodRequest request, UUID actor) {
        periods.lockConfiguration();
        AnnualKpiReviewPeriod period = requirePeriod(id);
        requireEditable(period);
        if (period.getStatus() == AnnualKpiReviewPeriodStatus.UPCOMING) return updateUpcoming(period, request, actor);
        requireNoParticipants(id);
        List<ReviewPeriodRoleConfiguration> old = configurations.findAllByReviewPeriodIdOrderByIdAsc(id);
        apply(request, period);
        List<ReviewPeriodEmployeeLevelConfiguration> weights = resolveWeights(period, request, id);
        List<ReviewPeriodRoleConfiguration> selected = resolveRoles(period, request, old);
        boolean published = period.getStatus() == AnnualKpiReviewPeriodStatus.UPCOMING;
        bindLevels(period, selected, weights, published);
        validate(period, selected, weights, published);
        List<ReviewCheckpoint> schedule = published ? generate(period, selected) : List.of();
        if (published) markPublished(period);
        period.setUpdatedBy(actor);
        checkpoints.deleteAllByReviewPeriodId(id);
        configurations.deleteAllByReviewPeriodId(id);
        // Flush removals before inserting replacements with the same unique keys.
        configurations.flush();
        levelConfigurations.deleteAllByReviewPeriodId(id);
        levelConfigurations.flush();
        periods.saveAndFlush(period);
        levelConfigurations.saveAllAndFlush(weights);
        bindLevels(period, selected, weights, published);
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
        List<ReviewPeriodEmployeeLevelConfiguration> weights = levelConfigurations
                .findAllByReviewPeriodIdOrderByEmployeeLevelDisplayOrderAsc(id);
        bindLevels(period, selected, weights, false);
        validate(period, selected, weights, true);
        List<ReviewCheckpoint> schedule = generate(period, selected);
        markPublished(period);
        period.setUpdatedBy(actor);
        periods.saveAndFlush(period);
        configurations.saveAllAndFlush(selected);
        checkpoints.deleteAllByReviewPeriodId(id);
        checkpoints.flush();
        checkpoints.saveAllAndFlush(schedule);
        enrolment.enrol(period, selected);
        periods.saveAndFlush(period);
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
        return roles.findAllByIsDeletedIsFalse().stream().filter(Role::isPerformanceReviewEligible).map(role -> {
            ReviewPeriodRoleConfiguration configuration = new ReviewPeriodRoleConfiguration();
            configuration.setRole(role);
            configuration.setReviewFrequency(ReviewPeriodRoleConfiguration.resolveFrequency(role, null));
            var dto = mapper.toDto(configuration);
            if (role.getEmployeeLevel() != null) {
                dto.setEmployeeLevelId(role.getEmployeeLevel().getId());
                dto.setEmployeeLevelName(role.getEmployeeLevel().getName());
            }
            return dto;
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AnnualKpiReviewPeriodDto preview(AnnualKpiReviewPeriodRequest request, Long excludedPeriodId) {
        AnnualKpiReviewPeriod period = new AnnualKpiReviewPeriod();
        List<ReviewPeriodRoleConfiguration> old = List.of();
        if (excludedPeriodId != null) {
            AnnualKpiReviewPeriod existing = requirePeriod(excludedPeriodId);
            requireEditable(existing);
            period.setStatus(existing.getStatus());
            old = configurations.findAllByReviewPeriodIdOrderByIdAsc(excludedPeriodId);
            period.setId(excludedPeriodId);
            if (existing.getStatus() == AnnualKpiReviewPeriodStatus.UPCOMING) requireFrozenConfiguration(existing, request);
        }
        apply(request, period);
        List<ReviewPeriodEmployeeLevelConfiguration> weights = resolveWeights(period, request, excludedPeriodId);
        List<ReviewPeriodRoleConfiguration> selected = resolveRoles(period, request,
                excludedPeriodId == null ? previousRoles(period) : old);
        bindLevels(period, selected, weights, period.getStatus() == AnnualKpiReviewPeriodStatus.UPCOMING);
        validate(period, selected, weights, true);
        markPublished(period);
        AnnualKpiReviewPeriodDto dto = mapper.toDto(period);
        // A preview is not a saved period and must not claim a persisted identifier/reference.
        dto.setId(null);
        dto.setReferenceNumber(null);
        dto.setOpenedAt(null);
        dto.setRoleConfigurations(selected.stream().map(mapper::toDto).toList());
        dto.setEmployeeLevelConfigurations(weights.stream().map(mapper::toDto).toList());
        dto.setCheckpoints(generate(period, selected).stream().map(mapper::toDto).toList());
        return dto;
    }

    @Override
    public void delete(Long id) {
        periods.lockConfiguration();
        AnnualKpiReviewPeriod period = requirePeriod(id);
        requireEditable(period);
        requireNoParticipants(id);
        if (kpiPlans.existsByReviewPeriodId(id)) throw new BadRequestException("This review period contains KPI plans and cannot be deleted");
        checkpoints.deleteAllByReviewPeriodId(id);
        configurations.deleteAllByReviewPeriodId(id);
        configurations.flush();
        levelConfigurations.deleteAllByReviewPeriodId(id);
        levelConfigurations.flush();
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
        if (!request.isGlobalKpiWeightsAbsent()) {
            throw new BadRequestException("Configure KPI weightages per Employee Level; global KPI weights are no longer accepted");
        }
        if (!request.isSeparateSetupDeadlinesAbsent()) {
            throw new BadRequestException("Use the single KPI Setup Deadline; separate KPI setup deadlines are no longer supported");
        }
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
            if (!role.isPerformanceReviewEligible()) {
                throw new BadRequestException("System administrative roles do not participate in performance reviews");
            }
            ReviewPeriodRoleConfiguration configuration = new ReviewPeriodRoleConfiguration();
            configuration.setReviewPeriod(period);
            configuration.setRole(role);
            if (period.getStatus() == AnnualKpiReviewPeriodStatus.UPCOMING) {
                old.stream().filter(c -> c.getRole().getId().equals(role.getId())).findFirst()
                        .ifPresent(c -> configuration.setEmployeeLevelConfiguration(c.getEmployeeLevelConfiguration()));
            }
            ReviewFrequency frequency = item.getReviewFrequency() != null
                    ? item.getReviewFrequency() : previous.get(item.getRoleId());
            configuration.setReviewFrequency(ReviewPeriodRoleConfiguration.resolveFrequency(role, frequency));
            selected.add(configuration);
        }
        return selected;
    }

    private void validate(AnnualKpiReviewPeriod period, List<ReviewPeriodRoleConfiguration> selected,
            List<ReviewPeriodEmployeeLevelConfiguration> weights, boolean publish) {
        if (selected.stream().anyMatch(c -> !c.getRole().isPerformanceReviewEligible())) {
            throw new BadRequestException("System administrative roles do not participate in performance reviews");
        }
        try {
            for (var weight : weights) validator.validateLevelWeights(weight, publish);
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
            Set<Long> required = new HashSet<>();
            levels.findAllByOrderByDisplayOrderAsc().forEach(l -> required.add(l.getId()));
            Set<Long> supplied = new HashSet<>();
            weights.forEach(w -> supplied.add(w.getEmployeeLevel().getId()));
            if (required.isEmpty() || !required.equals(supplied)) {
                throw new BadRequestException("Every Employee Level requires a complete KPI weightage configuration before publishing");
            }
            if (selected.stream().anyMatch(c -> c.getEmployeeLevelConfiguration() == null)) {
                throw new BadRequestException("Every applicable Role must have an Employee Level before publishing");
            }
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
                .stream().filter(c -> c.getRole().isPerformanceReviewEligible()).map(mapper::toDto).toList());
        dto.setCheckpoints(checkpoints.findAllByReviewPeriodIdOrderByReviewFrequencyAscSequenceNumberAsc(period.getId())
                .stream().map(mapper::toDto).toList());
        dto.setEmployeeLevelConfigurations(levelConfigurations.findAllByReviewPeriodIdOrderByEmployeeLevelDisplayOrderAsc(period.getId())
                .stream().map(mapper::toDto).toList());
        boolean editable = period.getStatus() == AnnualKpiReviewPeriodStatus.DRAFT ||
                period.getStatus() == AnnualKpiReviewPeriodStatus.UPCOMING && period.getStartDate().isAfter(LocalDate.now(clock));
        dto.setEditMode(!editable ? "READ_ONLY" : period.getStatus() == AnnualKpiReviewPeriodStatus.DRAFT ? "FULL" : "LIMITED");
        dto.setCanDelete(editable && !participants.existsByReviewPeriodId(period.getId()) && !kpiPlans.existsByReviewPeriodId(period.getId()));
        return dto;
    }

    private AnnualKpiReviewPeriodDto updateUpcoming(AnnualKpiReviewPeriod period, AnnualKpiReviewPeriodRequest request, UUID actor) {
        requireFrozenConfiguration(period, request);
        var selected = configurations.findAllByReviewPeriodIdOrderByIdAsc(period.getId());
        var weights = levelConfigurations.findAllByReviewPeriodIdOrderByEmployeeLevelDisplayOrderAsc(period.getId());
        apply(request, period);
        validate(period, selected, weights, true);
        var schedule = generate(period, selected);
        checkpoints.deleteAllByReviewPeriodId(period.getId()); checkpoints.flush();
        checkpoints.saveAllAndFlush(schedule);
        markPublished(period); period.setUpdatedBy(actor); periods.saveAndFlush(period);
        return details(period);
    }

    private void requireFrozenConfiguration(AnnualKpiReviewPeriod period, AnnualKpiReviewPeriodRequest request) {
        if (request == null) throw new BadRequestException("Review period configuration is required");
        var selected = configurations.findAllByReviewPeriodIdOrderByIdAsc(period.getId());
        var requested = request.getRoleConfigurations();
        boolean unchanged = requested != null && requested.size() == selected.size() &&
                requested.stream().filter(Objects::nonNull).map(AnnualKpiReviewPeriodRequest.RoleFrequency::getRoleId).distinct().count() == selected.size() &&
                requested.stream().allMatch(input -> input != null && selected.stream().anyMatch(saved ->
                        saved.getRole().getId().equals(input.getRoleId()) && (input.getReviewFrequency() == null || saved.getReviewFrequency() == input.getReviewFrequency())));
        var weights = levelConfigurations.findAllByReviewPeriodIdOrderByEmployeeLevelDisplayOrderAsc(period.getId());
        var supplied = request.getEmployeeLevelConfigurations();
        if (supplied != null) unchanged &= supplied.size() == weights.size() && supplied.stream().filter(Objects::nonNull)
                .map(ReviewPeriodEmployeeLevelConfigurationDto::getEmployeeLevelId).distinct().count() == weights.size() &&
                supplied.stream().allMatch(input -> input != null && weights.stream().anyMatch(saved ->
                        saved.getEmployeeLevel().getId().equals(input.getEmployeeLevelId()) &&
                        equalWeight(saved.getCompanyKpiWeight(), input.getCompanyKpiWeight()) &&
                        equalWeight(saved.getDepartmentKpiWeight(), input.getDepartmentKpiWeight()) &&
                        equalWeight(saved.getIndividualKpiWeight(), input.getIndividualKpiWeight())));
        unchanged &= equalWeight(period.getKpiPerformanceWeight(), request.getKpiPerformanceWeight()) &&
                equalWeight(period.getAttitudeEvaluationWeight(), request.getAttitudeEvaluationWeight()) &&
                period.getAnnualKpiConsolidationMethod() == request.getAnnualKpiConsolidationMethod();
        if (!unchanged) throw new BadRequestException("Upcoming periods allow name, date and deadline changes only; coverage, frequencies and scoring configuration are frozen after publication");
    }

    private boolean equalWeight(java.math.BigDecimal a, java.math.BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    @Override
    @Transactional(readOnly = true)
    public AnnualKpiReviewPeriodDefaultsDto creationDefaults(LocalDate startDate) {
        if (startDate == null) throw new BadRequestException("Start Date is required to load creation defaults");
        AnnualKpiReviewPeriod period = new AnnualKpiReviewPeriod();
        period.setStartDate(startDate);
        AnnualKpiReviewPeriodDefaultsDto dto = new AnnualKpiReviewPeriodDefaultsDto();
        AnnualKpiReviewPeriod source = previousPeriod(startDate);
        dto.setSourceReviewPeriodId(source == null ? null : source.getId());
        dto.setSourceReviewPeriodName(source == null ? null : source.getName());
        dto.setEmployeeLevelConfigurations(defaultWeights(period).stream().map(mapper::toDto).toList());
        Map<Long, ReviewFrequency> frequencies = new HashMap<>();
        previousRoles(period).forEach(c -> frequencies.put(c.getRole().getId(), c.getReviewFrequency()));
        var options = availableRoles();
        options.forEach(c -> {
            if (frequencies.containsKey(c.getRoleId())) c.setReviewFrequency(frequencies.get(c.getRoleId()));
        });
        dto.setRoleConfigurations(options);
        return dto;
    }

    private AnnualKpiReviewPeriod previousPeriod(LocalDate startDate) {
        if (startDate == null) return null;
        return periods.findFirstByStatusInOrderByEndDateDescStartDateDescIdDesc(
                List.of(AnnualKpiReviewPeriodStatus.UPCOMING, AnnualKpiReviewPeriodStatus.OPEN,
                        AnnualKpiReviewPeriodStatus.CLOSED)).orElse(null);
    }

    private List<ReviewPeriodRoleConfiguration> previousRoles(AnnualKpiReviewPeriod period) {
        if (period.getStartDate() == null) return List.of();
        Map<Long, ReviewPeriodRoleConfiguration> latestByRole = new LinkedHashMap<>();
        // A Role may be absent from the latest period, so retain its latest published configuration.
        configurations.findPublishedConfigurations(
                List.of(AnnualKpiReviewPeriodStatus.UPCOMING, AnnualKpiReviewPeriodStatus.OPEN,
                        AnnualKpiReviewPeriodStatus.CLOSED))
                .stream().filter(c -> c.getRole().isPerformanceReviewEligible())
                .forEach(configuration -> latestByRole.putIfAbsent(configuration.getRole().getId(), configuration));
        return new ArrayList<>(latestByRole.values());
    }

    private List<ReviewPeriodEmployeeLevelConfiguration> defaultWeights(AnnualKpiReviewPeriod period) {
        AnnualKpiReviewPeriod source = previousPeriod(period.getStartDate());
        if (source != null) {
            return levelConfigurations.findAllByReviewPeriodIdOrderByEmployeeLevelDisplayOrderAsc(source.getId())
                    .stream().map(w -> copyWeight(period, w)).toList();
        }
        return levels.findAllByOrderByDisplayOrderAsc().stream().map(level -> {
            var weight = new ReviewPeriodEmployeeLevelConfiguration();
            weight.setReviewPeriod(period);
            weight.setEmployeeLevel(level);
            weight.setCompanyKpiWeight(level.getDefaultCompanyKpiWeight());
            weight.setDepartmentKpiWeight(level.getDefaultDepartmentKpiWeight());
            weight.setIndividualKpiWeight(level.getDefaultIndividualKpiWeight());
            return weight;
        }).toList();
    }

    private ReviewPeriodEmployeeLevelConfiguration copyWeight(AnnualKpiReviewPeriod period,
            ReviewPeriodEmployeeLevelConfiguration original) {
        var weight = new ReviewPeriodEmployeeLevelConfiguration();
        weight.setReviewPeriod(period);
        weight.setEmployeeLevel(original.getEmployeeLevel());
        weight.setCompanyKpiWeight(original.getCompanyKpiWeight());
        weight.setDepartmentKpiWeight(original.getDepartmentKpiWeight());
        weight.setIndividualKpiWeight(original.getIndividualKpiWeight());
        return weight;
    }

    private List<ReviewPeriodEmployeeLevelConfiguration> resolveWeights(AnnualKpiReviewPeriod period,
            AnnualKpiReviewPeriodRequest request, Long existingId) {
        if (request.getEmployeeLevelConfigurations() == null) {
            if (existingId != null) return levelConfigurations.findAllByReviewPeriodIdOrderByEmployeeLevelDisplayOrderAsc(existingId)
                    .stream().map(w -> copyWeight(period, w)).toList();
            return period.getStartDate() == null ? List.of() : defaultWeights(period);
        }
        Map<Long, EmployeeLevel> available = new HashMap<>();
        levels.findAllByOrderByDisplayOrderAsc().forEach(l -> available.put(l.getId(), l));
        Set<Long> seen = new HashSet<>();
        List<ReviewPeriodEmployeeLevelConfiguration> result = new ArrayList<>();
        for (var input : request.getEmployeeLevelConfigurations()) {
            if (input == null || !available.containsKey(input.getEmployeeLevelId()) || !seen.add(input.getEmployeeLevelId())) {
                throw new BadRequestException("Employee Levels must be valid and unique");
            }
            var weight = new ReviewPeriodEmployeeLevelConfiguration();
            weight.setReviewPeriod(period);
            weight.setEmployeeLevel(available.get(input.getEmployeeLevelId()));
            weight.setCompanyKpiWeight(input.getCompanyKpiWeight());
            weight.setDepartmentKpiWeight(input.getDepartmentKpiWeight());
            weight.setIndividualKpiWeight(input.getIndividualKpiWeight());
            result.add(weight);
        }
        result.sort(Comparator.comparing(w -> w.getEmployeeLevel().getDisplayOrder()));
        return result;
    }

    private void bindLevels(AnnualKpiReviewPeriod period, List<ReviewPeriodRoleConfiguration> selected,
            List<ReviewPeriodEmployeeLevelConfiguration> weights, boolean preservePublished) {
        for (var roleConfiguration : selected) {
            EmployeeLevel level = preservePublished && roleConfiguration.getEmployeeLevelConfiguration() != null
                    ? roleConfiguration.getEmployeeLevelConfiguration().getEmployeeLevel()
                    : roleConfiguration.getRole().getEmployeeLevel();
            var weight = level == null ? null : weights.stream()
                    .filter(w -> w.getEmployeeLevel().getId().equals(level.getId())).findFirst().orElse(null);
            roleConfiguration.setEmployeeLevelConfiguration(weight);
            roleConfiguration.setEmployeeLevelConfigurationId(weight == null ? null : weight.getId());
        }
    }
}
