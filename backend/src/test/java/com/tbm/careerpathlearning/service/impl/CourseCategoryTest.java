package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CourseCategoryChipDto;
import com.tbm.careerpathlearning.dto.CourseCategoryDto;
import com.tbm.careerpathlearning.dto.CourseCategoryRequest;
import com.tbm.careerpathlearning.dto.SopLearningCourseDto;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.CourseCategory;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAuditLog;
import com.tbm.careerpathlearning.repository.CourseCategoryRepository;
import com.tbm.careerpathlearning.service.CourseCategoryService.Viewer;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Course categories: making and changing them, retiring the defaults, deleting custom ones, and setting a course's
 * (FR-19-01 to FR-19-06, decisions C and D of 29 Oct 2026).
 */
class CourseCategoryTest {

    private SopTestStore store;
    private CourseCategoryServiceImpl categories;
    private Staff weiMing, karim, jason, hr, aisyah;
    private Viewer asWeiMing, asKarim, asJason, asHr, asAisyah;
    private CourseCategory general, sales, leadership;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        categories = (CourseCategoryServiceImpl) store.courseCategories();
        weiMing = store.person("Tan Wei Ming", null);
        karim = store.person("Karim Abdullah", null);
        jason = store.person("Jason Lim", null);
        hr = store.person("Hana Yusof", null);
        aisyah = store.person("Nur Aisyah", null);
        asWeiMing = new Viewer(weiMing.getId(), false, true);
        asKarim = new Viewer(karim.getId(), false, true);
        asJason = new Viewer(jason.getId(), false, true); // a manager
        asHr = new Viewer(hr.getId(), true, true);
        asAisyah = new Viewer(aisyah.getId(), false, false);
        // As patch P012 adds them (decision C).
        general = stored("General", "#475569", "appstore", "GENERAL");
        sales = stored("Sales", "#B45309", "shop", "SALES");
        leadership = stored("Leadership", "#0F766E", "crown", "LEADERSHIP");
        store.doc(1L, "Cash Handling", weiMing.getId());
        store.doc(2L, "E-Hailing", karim.getId());
    }

    private CourseCategory stored(String name, String color, String icon, String code) {
        CourseCategory c = new CourseCategory();
        c.setName(name);
        c.setColor(color);
        c.setIcon(icon);
        c.setCode(code);
        c.setCreatedAt(LocalDateTime.of(2026, 10, 29, 9, 0));
        c.setUpdatedAt(c.getCreatedAt());
        return store.repository(CourseCategoryRepository.class).save(c);
    }

    private static CourseCategoryRequest request(String name, String color, String icon) {
        CourseCategoryRequest r = new CourseCategoryRequest();
        r.setName(name);
        r.setColor(color);
        r.setIcon(icon);
        return r;
    }

    private static CourseCategoryRequest course(Long primary, Long... ids) {
        CourseCategoryRequest r = new CourseCategoryRequest();
        r.setCategoryIds(List.of(ids));
        r.setPrimaryId(primary);
        return r;
    }

    private SopViewer owner(Staff s) {
        return new SopViewer(s.getId(), false);
    }

    private static List<String> names(List<CourseCategoryChipDto> chips) {
        return chips.stream().map(c -> c.getName() + (c.isPrimary() ? " (primary)" : "")).toList();
    }

    private CourseCategoryDto row(Viewer viewer, String name) {
        return categories.list(viewer).stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    private static void refused(String message, Executable action) {
        assertEquals(message, assertThrows(BadRequestException.class, action).getMessage());
    }

    @Test
    @DisplayName("Trainers, managers, HR and the Super Admin create categories with a name, colour and icon (FR-19-01)")
    void create() {
        CourseCategoryDto hrdc = categories.create(request("  HRDC   Claimable ", "#9333ea", "tag"), asWeiMing);
        categories.create(request("New Hire", "#EA580C", "rocket"), asJason);

        assertEquals("HRDC Claimable", hrdc.getName());
        assertEquals("#9333EA", hrdc.getColor());
        assertFalse(hrdc.isDefaultCategory());
        assertThrows(ForbiddenRequestException.class, () -> categories.create(request("Mine", "#9333EA", "tag"), asAisyah));
        refused("There is already a category called hrdc claimable.",
                () -> categories.create(request("hrdc claimable", "#9333EA", "tag"), asKarim));
        refused("Choose one of the colours offered.", () -> categories.create(request("Odd", "#123456", "tag"), asKarim));
        refused("Choose one of the icons offered.", () -> categories.create(request("Odd", "#9333EA", "unicorn"), asKarim));
        refused("Enter the category's name.", () -> categories.create(request(" ", "#9333EA", "tag"), asKarim));

        List<CourseCategoryDto> list = categories.list(asHr);
        assertEquals(List.of("General", "HRDC Claimable", "Leadership", "New Hire", "Sales"),
                list.stream().map(CourseCategoryDto::getName).toList());
        assertEquals("Tan Wei Ming", row(asHr, "HRDC Claimable").getCreatedByName());
        assertEquals(TrainingAuditAction.CATEGORY_CREATED.name(),
                store.auditLog.values().stream().findFirst().map(TrainingAuditLog::getAction).orElseThrow());
    }

    @Test
    @DisplayName("A custom category is changed by its creator, HR or the Super Admin; the defaults by HR only; General keeps its name (FR-19-02)")
    void change() {
        Long hrdc = categories.create(request("HRDC Claimable", "#9333EA", "tag"), asWeiMing).getId();

        assertEquals("HRDC", categories.update(hrdc, request("HRDC", "#059669", "dollar"), asWeiMing).getName());
        assertThrows(ForbiddenRequestException.class, () -> categories.update(hrdc, request("Mine", "#059669", "tag"), asKarim));
        assertEquals("HRDC Claims", categories.update(hrdc, request("HRDC Claims", "#059669", "tag"), asHr).getName());
        assertThrows(ForbiddenRequestException.class,
                () -> categories.update(sales.getCategoryId(), request("Selling", "#B45309", "shop"), asWeiMing));
        assertEquals("#1D4ED8", categories.update(sales.getCategoryId(), request("Sales", "#1D4ED8", "shop"), asHr).getColor());
        refused("General keeps its name: courses with no other category belong to it.",
                () -> categories.update(general.getCategoryId(), request("Other", "#475569", "appstore"), asHr));
        assertEquals("#059669", categories.update(general.getCategoryId(), request("General", "#059669", "appstore"), asHr).getColor());
        refused("There is already a category called Sales.",
                () -> categories.update(hrdc, request("Sales", "#059669", "tag"), asHr));

        CourseCategoryDto mine = row(asWeiMing, "HRDC Claims");
        assertTrue(mine.isCanEdit() && mine.isCanRename() && mine.isCanDelete() && !mine.isCanRetire());
        CourseCategoryDto theirs = row(asKarim, "HRDC Claims");
        assertFalse(theirs.isCanEdit() || theirs.isCanDelete());
        CourseCategoryDto generalForHr = row(asHr, "General");
        assertTrue(generalForHr.isCanEdit());
        assertFalse(generalForHr.isCanRename() || generalForHr.isCanRetire() || generalForHr.isCanDelete());
        assertTrue(row(asHr, "Sales").isCanRetire());
    }

    @Test
    @DisplayName("HR retires a default: no longer offered, kept on the courses that have it; restoring offers it again (FR-19-03)")
    void retire() {
        categories.setCourse(1L, course(null, sales.getCategoryId()), owner(weiMing));
        Long hrdc = categories.create(request("HRDC Claimable", "#9333EA", "tag"), asWeiMing).getId();

        assertThrows(ForbiddenRequestException.class, () -> categories.retire(sales.getCategoryId(), asWeiMing));
        assertTrue(categories.retire(sales.getCategoryId(), asHr).isRetired());
        refused("General can't be retired: courses with no other category belong to it.",
                () -> categories.retire(general.getCategoryId(), asHr));
        refused("Only default categories are retired. Delete a custom one instead.", () -> categories.retire(hrdc, asHr));

        List<CourseCategoryChipDto> kept = categories.ofCourse(1L);
        assertEquals(List.of("Sales (primary)"), names(kept));
        assertTrue(kept.get(0).isRetired());
        refused("Sales is retired, so it can't be added to a course.",
                () -> categories.setCourse(2L, course(null, sales.getCategoryId()), owner(karim)));
        // Already on the course, it can stay while other categories change.
        assertEquals(List.of("Sales (primary)", "Leadership"),
                names(categories.setCourse(1L, course(sales.getCategoryId(), sales.getCategoryId(), leadership.getCategoryId()), owner(weiMing))));

        assertFalse(categories.restore(sales.getCategoryId(), asHr).isRetired());
        assertEquals(List.of("Sales (primary)"), names(categories.setCourse(2L, course(null, sales.getCategoryId()), owner(karim))));
    }

    @Test
    @DisplayName("Deleting a custom category takes it off every course; a course left with none moves to General (FR-19-04)")
    void delete() {
        Long hrdc = categories.create(request("HRDC Claimable", "#9333EA", "tag"), asWeiMing).getId();
        categories.setCourse(1L, course(hrdc, hrdc, sales.getCategoryId()), owner(weiMing)); // HRDC primary, Sales too
        categories.setCourse(2L, course(null, hrdc), owner(karim));                          // HRDC only

        assertThrows(ForbiddenRequestException.class, () -> categories.delete(hrdc, asKarim));
        refused("A default category can't be deleted. Retire it instead: it stays on the courses that have it.",
                () -> categories.delete(sales.getCategoryId(), asHr));
        refused("General can't be deleted: courses with no other category belong to it.",
                () -> categories.delete(general.getCategoryId(), asHr));
        categories.delete(hrdc, asWeiMing);

        assertEquals(List.of("Sales (primary)"), names(categories.ofCourse(1L)));
        assertEquals(List.of("General (primary)"), names(categories.ofCourse(2L)));
        assertTrue(store.categoryLinks.values().stream().anyMatch(l -> l.getCourseKey().equals(2L)
                && l.getCategoryId().equals(general.getCategoryId())), "moved to General, not just shown as it");
        assertFalse(store.categories.containsKey(hrdc));
        TrainingAuditLog logged = store.auditLog.values().stream()
                .filter(a -> a.getAction().equals(TrainingAuditAction.CATEGORY_DELETED.name())).findFirst().orElseThrow();
        assertEquals("HRDC Claimable: taken off 2 courses, 1 moved to General", logged.getDetail());
    }

    @Test
    @DisplayName("A course's owner sets its categories, one primary, at least one; every version shares them (FR-19-05)")
    void courseCategories() {
        SopDocument draft = store.doc(3L, "Cash Handling", weiMing.getId());
        draft.setCourseId(1L);
        draft.setVersionNo(2);

        assertEquals(List.of("General (primary)"), names(categories.ofCourse(1L))); // nothing set yet
        assertEquals(List.of("Leadership (primary)", "Sales"),
                names(categories.setCourse(1L, course(leadership.getCategoryId(), sales.getCategoryId(), leadership.getCategoryId()), owner(weiMing))));
        assertEquals(List.of("Leadership (primary)", "Sales"), names(categories.setCourse(3L,
                course(leadership.getCategoryId(), sales.getCategoryId(), leadership.getCategoryId()), owner(weiMing))));
        assertEquals(2, store.categoryLinks.size(), "one set for the course, shared by its versions");

        refused("A course needs at least one category.", () -> categories.setCourse(1L, course(null), owner(weiMing)));
        assertThrows(ForbiddenRequestException.class,
                () -> categories.setCourse(1L, course(null, sales.getCategoryId()), owner(karim)));
        assertThrows(ForbiddenRequestException.class,
                () -> categories.setCourse(1L, course(null, sales.getCategoryId()), new SopViewer(hr.getId(), true)));
        refused("A chosen category no longer exists. Reload the page and choose again.",
                () -> categories.setCourse(1L, course(null, 999L), owner(weiMing)));
        store.docs.get(1L).setCourseStatus(SopCourseStatus.ARCHIVED);
        refused("This course is archived. Restore it to change its categories.",
                () -> categories.setCourse(1L, course(null, sales.getCategoryId()), owner(weiMing)));
    }

    @Test
    @DisplayName("Courses count under their categories; General counts those with none (FR-19-06 filters use the same)")
    void counts() {
        categories.setCourse(1L, course(null, sales.getCategoryId(), leadership.getCategoryId()), owner(weiMing));

        assertEquals(1, row(asHr, "Sales").getCourseCount());
        assertEquals(1, row(asHr, "Leadership").getCourseCount());
        assertEquals(1, row(asHr, "General").getCourseCount()); // E-Hailing has none
        store.docs.get(1L).setDeleted(true);
        assertEquals(0, row(asHr, "Sales").getCourseCount(), "a deleted course doesn't count");
    }

    @Test
    @DisplayName("My Learning shows a course's primary category, with its colour, and every category for the filters")
    void myLearning() {
        store.module(1L, 5);
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, store.today(), store.today().plusDays(30), 14, 70);
        categories.setCourse(1L, course(sales.getCategoryId(), leadership.getCategoryId(), sales.getCategoryId()), owner(weiMing));
        SopLearningServiceImpl learning = store.wire(new SopLearningServiceImpl());

        SopLearningCourseDto c = learning.home(aisyah.getId()).getCourses().get(0);

        assertEquals("Sales", c.getCategory());
        assertEquals("#B45309", c.getCategoryColor());
        assertEquals("shop", c.getCategoryIcon());
        assertEquals(List.of("Sales", "Leadership"), c.getCategories().stream().map(CourseCategoryChipDto::getName).toList());
        assertEquals("#0F766E", c.getCategories().get(1).getColor(), "the filter shows each category in its own colour");
    }
}
