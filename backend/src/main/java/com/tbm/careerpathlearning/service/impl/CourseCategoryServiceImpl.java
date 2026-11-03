package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CourseCategoryChipDto;
import com.tbm.careerpathlearning.dto.CourseCategoryDto;
import com.tbm.careerpathlearning.dto.CourseCategoryRequest;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.CourseCategory;
import com.tbm.careerpathlearning.model.CourseCategoryLink;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.repository.CourseCategoryLinkRepository;
import com.tbm.careerpathlearning.repository.CourseCategoryRepository;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.CourseCategoryService;
import com.tbm.careerpathlearning.service.SopAccess;
import com.tbm.careerpathlearning.service.SopViewer;
import com.tbm.careerpathlearning.service.TrainingAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CourseCategoryServiceImpl implements CourseCategoryService {

    static final int MAX_NAME = 60;
    static final int MAX_PER_COURSE = 5;
    /** The colours offered, as in the prototype's category manager. */
    public static final List<String> COLORS = List.of("#B45309", "#1D4ED8", "#7C3AED", "#DC2626", "#0F766E", "#0891B2",
            "#9333EA", "#EA580C", "#059669", "#475569");
    /** The icons offered: names of the app's icon set. */
    public static final List<String> ICONS = List.of("appstore", "tag", "shop", "tool", "gift", "safety", "crown",
            "laptop", "book", "team", "bulb", "rocket", "car", "dollar", "heart", "star", "experiment", "customer-service");
    /** Used when no General category is stored yet, so a course always has one. */
    private static final String GENERAL_NAME = "General";
    private static final String GENERAL_COLOR = "#475569";

    @Autowired private CourseCategoryRepository courseCategoryRepository;
    @Autowired private CourseCategoryLinkRepository courseCategoryLinkRepository;
    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private TrainingAuditService trainingAuditService;
    /** Malaysia time, whatever time zone the server runs in. */
    @Autowired private Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CourseCategoryDto> list(Viewer viewer) {
        List<CourseCategory> categories = courseCategoryRepository.findAll();
        // Courses that still exist; a deleted one's categories don't count.
        Set<Long> courses = sopDocumentRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .map(SopDocument::courseKey).collect(Collectors.toSet());
        Map<Long, Set<Long>> coursesOf = new HashMap<>();
        Set<Long> categorised = new HashSet<>();
        for (CourseCategoryLink l : courseCategoryLinkRepository.findAll()) {
            if (!courses.contains(l.getCourseKey())) continue;
            coursesOf.computeIfAbsent(l.getCategoryId(), k -> new HashSet<>()).add(l.getCourseKey());
            categorised.add(l.getCourseKey());
        }
        int uncategorised = (int) courses.stream().filter(k -> !categorised.contains(k)).count();
        Set<UUID> creators = categories.stream().map(CourseCategory::getCreatedBy).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = creators.isEmpty() ? Map.of() : staffRepository.findAllWithRoleByIdIn(creators).stream()
                .collect(Collectors.toMap(Staff::getId, s -> Objects.toString(s.getName(), "Unknown user")));
        return categories.stream()
                .sorted(Comparator.comparing((CourseCategory c) -> !c.isGeneral())
                        .thenComparing(CourseCategory::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> {
                    CourseCategoryDto dto = toDto(c, viewer);
                    dto.setCourseCount(coursesOf.getOrDefault(c.getCategoryId(), Set.of()).size()
                            + (c.isGeneral() ? uncategorised : 0));
                    dto.setCreatedByName(c.getCreatedBy() == null ? null : names.getOrDefault(c.getCreatedBy(), "Unknown user"));
                    return dto;
                }).toList();
    }

    @Override
    @Transactional
    public CourseCategoryDto create(CourseCategoryRequest request, Viewer viewer) {
        if (!viewer.canCreate()) {
            throw new ForbiddenRequestException("Staff with Trainer access, managers, HR and the Super Admin create categories.");
        }
        String name = name(request, null);
        LocalDateTime now = LocalDateTime.now(clock);
        CourseCategory c = new CourseCategory();
        c.setName(name);
        c.setColor(color(request));
        c.setIcon(icon(request));
        c.setCreatedBy(viewer.userId());
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        courseCategoryRepository.save(c);
        audit(viewer, TrainingAuditAction.CATEGORY_CREATED, c, name);
        return toDto(c, viewer);
    }

    @Override
    @Transactional
    public CourseCategoryDto update(Long categoryId, CourseCategoryRequest request, Viewer viewer) {
        CourseCategory c = find(categoryId);
        if (!canEdit(c, viewer)) {
            throw new ForbiddenRequestException(c.isDefault() ? "Only HR and the Super Admin change the default categories."
                    : "Only whoever made this category, HR and the Super Admin can change it.");
        }
        String before = c.getName();
        String name = c.isGeneral() ? c.getName() : name(request, c.getCategoryId());
        if (c.isGeneral() && request.getName() != null && !request.getName().isBlank()
                && !request.getName().trim().equalsIgnoreCase(c.getName())) {
            throw new BadRequestException("General keeps its name: courses with no other category belong to it.");
        }
        c.setName(name);
        c.setColor(color(request));
        c.setIcon(icon(request));
        c.setUpdatedAt(LocalDateTime.now(clock));
        courseCategoryRepository.save(c);
        audit(viewer, TrainingAuditAction.CATEGORY_CHANGED, c, before.equals(name) ? name : before + " renamed " + name);
        return toDto(c, viewer);
    }

    @Override
    @Transactional
    public CourseCategoryDto retire(Long categoryId, Viewer viewer) {
        CourseCategory c = retirable(categoryId, viewer);
        if (c.isRetired()) {
            throw new BadRequestException(c.getName() + " is already retired.");
        }
        c.setRetired(true);
        c.setUpdatedAt(LocalDateTime.now(clock));
        courseCategoryRepository.save(c);
        audit(viewer, TrainingAuditAction.CATEGORY_RETIRED, c, c.getName());
        return toDto(c, viewer);
    }

    @Override
    @Transactional
    public CourseCategoryDto restore(Long categoryId, Viewer viewer) {
        CourseCategory c = retirable(categoryId, viewer);
        if (!c.isRetired()) {
            throw new BadRequestException(c.getName() + " isn't retired.");
        }
        c.setRetired(false);
        c.setUpdatedAt(LocalDateTime.now(clock));
        courseCategoryRepository.save(c);
        audit(viewer, TrainingAuditAction.CATEGORY_RESTORED, c, c.getName());
        return toDto(c, viewer);
    }

    @Override
    @Transactional
    public void delete(Long categoryId, Viewer viewer) {
        CourseCategory c = find(categoryId);
        if (c.isDefault()) {
            throw new BadRequestException(c.isGeneral() ? "General can't be deleted: courses with no other category belong to it."
                    : "A default category can't be deleted. Retire it instead: it stays on the courses that have it.");
        }
        if (!canEdit(c, viewer)) {
            throw new ForbiddenRequestException("Only whoever made this category, HR and the Super Admin can delete it.");
        }
        List<CourseCategoryLink> gone = courseCategoryLinkRepository.findAllByCategoryId(categoryId);
        Long general = generalCategory().getCategoryId();
        int moved = 0;
        for (CourseCategoryLink link : gone) {
            courseCategoryLinkRepository.delete(link);
            List<CourseCategoryLink> left = courseCategoryLinkRepository.findAllByCourseKey(link.getCourseKey()).stream()
                    .filter(l -> !l.getLinkId().equals(link.getLinkId())).toList();
            if (left.isEmpty()) {
                // FR-19-04: a course never goes without a category.
                if (general != null) link(link.getCourseKey(), general, true);
                moved++;
            } else if (link.isPrimary() && left.stream().noneMatch(CourseCategoryLink::isPrimary)) {
                CourseCategoryLink next = left.stream().min(Comparator.comparing(CourseCategoryLink::getLinkId)).orElseThrow();
                next.setPrimary(true);
                courseCategoryLinkRepository.save(next);
            }
        }
        courseCategoryRepository.delete(c);
        audit(viewer, TrainingAuditAction.CATEGORY_DELETED, c, c.getName() + ": taken off " + gone.size()
                + (gone.size() == 1 ? " course" : " courses") + (moved > 0 ? ", " + moved + " moved to General" : ""));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseCategoryChipDto> ofCourse(Long courseKey) {
        return ofCourses(List.of(courseKey)).get(courseKey);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<CourseCategoryChipDto>> ofCourses(Collection<Long> courseKeys) {
        Map<Long, List<CourseCategoryChipDto>> out = new HashMap<>();
        if (courseKeys.isEmpty()) {
            return out;
        }
        List<CourseCategoryLink> links = courseCategoryLinkRepository.findAllByCourseKeyIn(new LinkedHashSet<>(courseKeys));
        Map<Long, CourseCategory> categories = courseCategoryRepository.findAllById(links.stream()
                        .map(CourseCategoryLink::getCategoryId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CourseCategory::getCategoryId, Function.identity()));
        Map<Long, List<CourseCategoryLink>> byCourse = links.stream()
                .collect(Collectors.groupingBy(CourseCategoryLink::getCourseKey));
        CourseCategoryChipDto general = null;
        for (Long key : courseKeys) {
            List<CourseCategoryChipDto> chips = byCourse.getOrDefault(key, List.of()).stream()
                    .filter(l -> categories.containsKey(l.getCategoryId()))
                    .sorted(Comparator.comparing((CourseCategoryLink l) -> !l.isPrimary()).thenComparing(CourseCategoryLink::getLinkId))
                    .map(l -> chip(categories.get(l.getCategoryId()), l.isPrimary()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (chips.isEmpty()) {
                if (general == null) general = chip(generalCategory(), true);
                chips.add(general);
            }
            out.put(key, chips);
        }
        return out;
    }

    @Override
    @Transactional
    public List<CourseCategoryChipDto> setCourse(Long documentId, CourseCategoryRequest request, SopViewer viewer) {
        SopDocument doc = sopDocumentRepository.findById(documentId).filter(d -> !d.isDeleted())
                .orElseThrow(() -> new BadRequestException("This course no longer exists."));
        SopAccess.requireEdit(doc, viewer);
        if (doc.isArchived()) {
            throw new BadRequestException("This course is archived. Restore it to change its categories.");
        }
        List<Long> ids = request.getCategoryIds() == null ? List.of()
                : request.getCategoryIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) { // FR-19-05
            throw new BadRequestException("A course needs at least one category.");
        }
        if (ids.size() > MAX_PER_COURSE) {
            throw new BadRequestException("Choose up to " + MAX_PER_COURSE + " categories.");
        }
        Map<Long, CourseCategory> chosen = courseCategoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(CourseCategory::getCategoryId, Function.identity()));
        if (chosen.size() < ids.size()) {
            throw new BadRequestException("A chosen category no longer exists. Reload the page and choose again.");
        }
        Long key = doc.courseKey();
        List<CourseCategoryLink> current = courseCategoryLinkRepository.findAllByCourseKey(key);
        Set<Long> had = current.stream().map(CourseCategoryLink::getCategoryId).collect(Collectors.toSet());
        for (Long id : ids) {
            CourseCategory c = chosen.get(id);
            if (c.isRetired() && !had.contains(id)) { // FR-19-03: kept where it is, never added
                throw new BadRequestException(c.getName() + " is retired, so it can't be added to a course.");
            }
        }
        Long primary = request.getPrimaryId() != null && ids.contains(request.getPrimaryId()) ? request.getPrimaryId() : ids.get(0);
        for (CourseCategoryLink l : current) {
            if (!ids.contains(l.getCategoryId())) {
                courseCategoryLinkRepository.delete(l);
            } else if (l.isPrimary() != l.getCategoryId().equals(primary)) {
                l.setPrimary(l.getCategoryId().equals(primary));
                courseCategoryLinkRepository.save(l);
            }
        }
        for (Long id : ids) {
            if (!had.contains(id)) {
                link(key, id, id.equals(primary));
            }
        }
        return ofCourse(key);
    }

    // --- helpers ------------------------------------------------------------------

    /** General: patch P012 stores it; a stand-in until then, so every course still has one. */
    private CourseCategory generalCategory() {
        return courseCategoryRepository.findByCode(CourseCategory.GENERAL).orElseGet(() -> {
            CourseCategory g = new CourseCategory();
            g.setName(GENERAL_NAME);
            g.setColor(GENERAL_COLOR);
            g.setIcon("appstore");
            g.setCode(CourseCategory.GENERAL);
            return g;
        });
    }

    private void link(Long courseKey, Long categoryId, boolean primary) {
        CourseCategoryLink l = new CourseCategoryLink();
        l.setCourseKey(courseKey);
        l.setCategoryId(categoryId);
        l.setPrimary(primary);
        courseCategoryLinkRepository.save(l);
    }

    private CourseCategory find(Long categoryId) {
        return courseCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BadRequestException("This category no longer exists."));
    }

    private CourseCategory retirable(Long categoryId, Viewer viewer) {
        CourseCategory c = find(categoryId);
        if (!viewer.admin()) {
            throw new ForbiddenRequestException("Only HR and the Super Admin retire or restore categories.");
        }
        if (c.isGeneral()) {
            throw new BadRequestException("General can't be retired: courses with no other category belong to it.");
        }
        if (!c.isDefault()) {
            throw new BadRequestException("Only default categories are retired. Delete a custom one instead.");
        }
        return c;
    }

    private static boolean canEdit(CourseCategory c, Viewer viewer) {
        return viewer.admin() || (!c.isDefault() && viewer.userId().equals(c.getCreatedBy()));
    }

    private String name(CourseCategoryRequest request, Long self) {
        String name = request.getName() == null ? "" : request.getName().trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new BadRequestException("Enter the category's name.");
        }
        if (name.length() > MAX_NAME) {
            throw new BadRequestException("Keep the name to " + MAX_NAME + " characters.");
        }
        boolean taken = self == null ? courseCategoryRepository.existsByNameIgnoreCase(name)
                : courseCategoryRepository.existsByNameIgnoreCaseAndCategoryIdNot(name, self);
        if (taken) {
            throw new BadRequestException("There is already a category called " + name + ".");
        }
        return name;
    }

    private static String color(CourseCategoryRequest request) {
        String color = request.getColor() == null ? "" : request.getColor().trim().toUpperCase(Locale.ROOT);
        if (!COLORS.contains(color)) {
            throw new BadRequestException("Choose one of the colours offered.");
        }
        return color;
    }

    private static String icon(CourseCategoryRequest request) {
        String icon = request.getIcon() == null ? "" : request.getIcon().trim();
        if (!ICONS.contains(icon)) {
            throw new BadRequestException("Choose one of the icons offered.");
        }
        return icon;
    }

    private static CourseCategoryChipDto chip(CourseCategory c, boolean primary) {
        CourseCategoryChipDto chip = new CourseCategoryChipDto();
        chip.setId(c.getCategoryId());
        chip.setName(c.getName());
        chip.setColor(c.getColor());
        chip.setIcon(c.getIcon());
        chip.setPrimary(primary);
        chip.setRetired(c.isRetired());
        return chip;
    }

    private static CourseCategoryDto toDto(CourseCategory c, Viewer viewer) {
        CourseCategoryDto dto = new CourseCategoryDto();
        dto.setId(c.getCategoryId());
        dto.setName(c.getName());
        dto.setColor(c.getColor());
        dto.setIcon(c.getIcon());
        dto.setDefaultCategory(c.isDefault());
        dto.setGeneral(c.isGeneral());
        dto.setRetired(c.isRetired());
        dto.setCanEdit(canEdit(c, viewer));
        dto.setCanRename(dto.isCanEdit() && !c.isGeneral());
        dto.setCanRetire(viewer.admin() && c.isDefault() && !c.isGeneral());
        dto.setCanDelete(!c.isDefault() && canEdit(c, viewer));
        return dto;
    }

    private void audit(Viewer viewer, TrainingAuditAction action, CourseCategory c, String detail) {
        trainingAuditService.record(viewer.userId(), action, TrainingAuditTarget.CATEGORY, String.valueOf(c.getCategoryId()),
                null, detail);
    }
}
