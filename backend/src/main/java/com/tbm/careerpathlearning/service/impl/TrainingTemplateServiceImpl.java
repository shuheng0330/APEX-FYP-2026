package com.tbm.careerpathlearning.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.dto.TrainingTemplateDto;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingSessionTemplate;
import com.tbm.careerpathlearning.repository.OrgChartRepository;
import com.tbm.careerpathlearning.repository.RoleRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingSessionTemplateRepository;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import com.tbm.careerpathlearning.service.TrainingTemplateService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class TrainingTemplateServiceImpl implements TrainingTemplateService {

    private static final int MAX_NAME = 100;

    /** Dates and times as text, and fields the form no longer has are skipped. */
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    @Autowired private TrainingSessionTemplateRepository templateRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private OrgChartRepository orgChartRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TrainerAccessService trainerAccessService;
    /** Malaysia time, whatever time zone the server runs in. */
    @Autowired private Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<TrainingTemplateDto> list(TrainingViewer viewer) {
        schedules(viewer);
        List<TrainingSessionTemplate> templates = viewer.hr() ? templateRepository.findAll()
                : templateRepository.findAllByCreatedBy(viewer.userId());
        Map<TrainingSessionTemplate, TrainingSessionRequest> setups = new LinkedHashMap<>();
        templates.forEach(t -> setups.put(t, read(t)));

        // What still exists, looked up once for every template.
        Set<Long> departments = new HashSet<>();
        Set<Long> roles = new HashSet<>();
        Set<UUID> people = new HashSet<>();
        setups.values().stream().filter(Objects::nonNull).forEach(r -> {
            departments.addAll(r.getDepartmentIds());
            roles.addAll(r.getRoleIds());
            people.addAll(r.getStaffIds());
            if (r.getTrainerId() != null) people.add(r.getTrainerId());
        });
        templates.forEach(t -> people.add(t.getCreatedBy()));
        Set<Long> liveDepartments = departments.isEmpty() ? Set.of() : orgChartRepository.findAllById(departments).stream()
                .filter(o -> !o.isDeleted() && o.getType() == OrgChartType.D).map(OrgChart::getId).collect(Collectors.toSet());
        Set<Long> liveRoles = roles.isEmpty() ? Set.of() : roleRepository.findAllById(roles).stream()
                .filter(r -> !r.isDeleted()).map(Role::getId).collect(Collectors.toSet());
        Map<UUID, Staff> staff = people.isEmpty() ? Map.of() : staffRepository.findAllWithRoleByIdIn(people).stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));
        Set<UUID> trainers = trainerAccessService.trainersAndHr().stream().map(Staff::getId).collect(Collectors.toSet());

        List<TrainingTemplateDto> list = new ArrayList<>();
        setups.forEach((t, setup) -> list.add(toDto(t, setup, viewer, liveDepartments, liveRoles, staff, trainers)));
        list.sort(Comparator.comparing(TrainingTemplateDto::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @Override
    @Transactional
    public void save(String name, TrainingSessionRequest setup, TrainingViewer viewer) {
        schedules(viewer);
        String n = name == null || name.isBlank() ? null : name.trim();
        if (n == null) {
            throw new BadRequestException("Name the template.");
        }
        if (n.length() > MAX_NAME) {
            throw new BadRequestException("Keep the template's name to " + MAX_NAME + " characters.");
        }
        if (templateRepository.existsByCreatedByAndNameIgnoreCase(viewer.userId(), n)) {
            throw new BadRequestException("You already have a template called \"" + n
                    + "\". Choose another name, or delete the old template first.");
        }
        TrainingSessionRequest kept = JSON.convertValue(setup, TrainingSessionRequest.class);
        // Each run has its own date and cut-off.
        kept.setDate(null);
        kept.setRegistrationCutoff(null);
        kept.setTemplateName(null);
        kept.setTemplateId(null);
        TrainingSessionTemplate t = new TrainingSessionTemplate();
        t.setName(n);
        t.setSetup(write(kept));
        t.setCreatedBy(viewer.userId());
        t.setCreatedAt(LocalDateTime.now(clock));
        templateRepository.save(t);
    }

    @Override
    @Transactional
    public void markUsed(Long templateId, TrainingViewer viewer) {
        // A template deleted while the form was open, or someone else's, is simply not counted.
        templateRepository.findById(templateId).filter(t -> visible(t, viewer)).ifPresent(t -> {
            t.setTimesUsed(t.getTimesUsed() + 1);
            t.setLastUsedAt(LocalDateTime.now(clock));
            templateRepository.save(t);
        });
    }

    @Override
    @Transactional
    public void delete(Long templateId, TrainingViewer viewer) {
        schedules(viewer);
        TrainingSessionTemplate t = templateRepository.findById(templateId)
                .orElseThrow(() -> new BadRequestException("This template no longer exists."));
        if (!viewer.hr() && !viewer.userId().equals(t.getCreatedBy())) {
            throw new ForbiddenRequestException("Only whoever saved this template, and HR, can delete it.");
        }
        templateRepository.delete(t);
    }

    // --- helpers ------------------------------------------------------------------

    private static void schedules(TrainingViewer viewer) {
        if (!viewer.canSchedule()) {
            throw new ForbiddenRequestException("Only staff with Trainer access, and HR, use session templates.");
        }
    }

    private static boolean visible(TrainingSessionTemplate t, TrainingViewer viewer) {
        return viewer.hr() || viewer.userId().equals(t.getCreatedBy());
    }

    /**
     * The template as the form should be filled from it: departments, job roles and people that no longer exist or
     * have left are dropped, and so is a trainer who no longer has Trainer access, each with a note.
     */
    private static TrainingTemplateDto toDto(TrainingSessionTemplate t, TrainingSessionRequest saved, TrainingViewer viewer,
                                             Set<Long> liveDepartments, Set<Long> liveRoles, Map<UUID, Staff> staff,
                                             Set<UUID> trainers) {
        TrainingTemplateDto dto = new TrainingTemplateDto();
        dto.setId(t.getTemplateId());
        dto.setName(t.getName());
        Staff creator = staff.get(t.getCreatedBy());
        dto.setCreatedByName(creator == null ? "Unknown user" : creator.getName());
        dto.setCreatedAt(t.getCreatedAt());
        dto.setTimesUsed(t.getTimesUsed());
        dto.setLastUsedAt(t.getLastUsedAt());
        dto.setMine(viewer.userId().equals(t.getCreatedBy()));
        dto.setCanDelete(visible(t, viewer));
        if (saved == null) {
            TrainingSessionRequest blank = new TrainingSessionRequest();
            blank.setTitle(t.getName());
            dto.setSetup(blank);
            dto.getLeftOut().add("This template could not be read. Delete it, then save the set-up again.");
            return dto;
        }
        List<String> dropped = new ArrayList<>();
        saved.setDepartmentIds(kept(saved.getDepartmentIds(), liveDepartments::contains, "department", "departments", dropped));
        saved.setRoleIds(kept(saved.getRoleIds(), liveRoles::contains, "job role", "job roles", dropped));
        saved.setStaffIds(kept(saved.getStaffIds(), id -> {
            Staff s = staff.get(id);
            return s != null && !s.isDeleted() && s.getAccountStatus() == StaffAccountStatus.ACTIVE;
        }, "person", "people", dropped));
        if (!dropped.isEmpty()) {
            dto.getLeftOut().add("Left out of the audience, as they no longer exist or have left: "
                    + String.join(", ", dropped) + ".");
        }
        if (saved.getTrainerId() != null) {
            Staff trainer = staff.get(saved.getTrainerId());
            String name = trainer == null ? "The saved trainer" : trainer.getName();
            dto.setTrainerName(name);
            if (!trainers.contains(saved.getTrainerId())) {
                boolean sharing = saved.getKind() == TrainingSessionKind.STAFF_SHARING;
                dto.getLeftOut().add(name + " no longer has Trainer access, so choose who "
                        + (sharing ? "presents" : "runs") + " the session.");
                saved.setTrainerId(null);
            }
        } else {
            dto.setTrainerName(saved.getTrainerName());
        }
        dto.setSetup(saved);
        return dto;
    }

    /** The ids still allowed, noting how many were dropped ("2 people"). */
    private static <T> List<T> kept(Collection<T> ids, Predicate<T> live, String one, String many,
                                    List<String> dropped) {
        List<T> kept = ids == null ? new ArrayList<>() : ids.stream().filter(Objects::nonNull).filter(live).toList();
        int gone = (ids == null ? 0 : (int) ids.stream().filter(Objects::nonNull).count()) - kept.size();
        if (gone > 0) {
            dropped.add(gone + " " + (gone == 1 ? one : many));
        }
        return new ArrayList<>(kept);
    }

    private static TrainingSessionRequest read(TrainingSessionTemplate t) {
        try {
            return JSON.readValue(t.getSetup(), TrainingSessionRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String write(TrainingSessionRequest setup) {
        try {
            return JSON.writeValueAsString(setup);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not save the template", e);
        }
    }
}
