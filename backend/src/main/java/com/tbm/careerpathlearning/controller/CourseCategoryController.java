package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.CourseCategoryRequest;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.service.CourseCategoryService;
import com.tbm.careerpathlearning.service.SopViewer;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import com.tbm.careerpathlearning.service.impl.CourseCategoryServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Course categories (FR-19), for staff with Trainer access, managers, HR and the Super Admin; the service decides who
 * may change which (decision D, 29 Oct 2026).
 */
@RestController
@RequestMapping("/api/course-categories")
@PreAuthorize("""
        hasAnyAuthority(
            T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_TRAINING.getAuthorityName(),
            T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_ASSIGN_TRAINING.getAuthorityName(),
            T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName(),
            T(com.tbm.careerpathlearning.service.TrainerAccessService).TEAM_TRAINING_AUTHORITY)
        """)
public class CourseCategoryController {

    @Autowired
    private CourseCategoryService categories;

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        return ResponseEntity.ok(categories.list(viewer(authentication)));
    }

    /** The colours and icons a category can have. */
    @GetMapping("/options")
    public ResponseEntity<?> options() {
        return ResponseEntity.ok(Map.of("colors", CourseCategoryServiceImpl.COLORS, "icons", CourseCategoryServiceImpl.ICONS));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CourseCategoryRequest request, Authentication authentication) {
        return ResponseEntity.ok(categories.create(request, viewer(authentication)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CourseCategoryRequest request,
                                    Authentication authentication) {
        return ResponseEntity.ok(categories.update(id, request, viewer(authentication)));
    }

    @PostMapping("/{id}/retire")
    public ResponseEntity<?> retire(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(categories.retire(id, viewer(authentication)));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restore(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(categories.restore(id, viewer(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        categories.delete(id, viewer(authentication));
        return ResponseEntity.noContent().build();
    }

    /** FR-19-05: a course's categories, set by its owner. */
    @PutMapping("/courses/{documentId}")
    public ResponseEntity<?> setCourse(@PathVariable Long documentId, @RequestBody CourseCategoryRequest request,
                                       Authentication authentication) {
        Set<String> granted = granted(authentication);
        SopViewer viewer = new SopViewer(UUID.fromString(authentication.getPrincipal().toString()),
                granted.contains(AuthorityName.CAN_ASSIGN_TRAINING.getAuthorityName())
                        || granted.contains(AuthorityName.CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()));
        return ResponseEntity.ok(categories.setCourse(documentId, request, viewer));
    }

    /** HR and the Super Admin manage every category; Trainer access or a team lets someone create one (FR-19-01). */
    private static CourseCategoryService.Viewer viewer(Authentication authentication) {
        Set<String> granted = granted(authentication);
        boolean admin = granted.contains(AuthorityName.CAN_ASSIGN_TRAINING.getAuthorityName())
                || granted.contains(AuthorityName.CAN_MANAGE_ACCESS_CONTROL.getAuthorityName());
        boolean creates = admin || granted.contains(AuthorityName.CAN_MANAGE_TRAINING.getAuthorityName())
                || granted.contains(TrainerAccessService.TEAM_TRAINING_AUTHORITY);
        return new CourseCategoryService.Viewer(UUID.fromString(authentication.getPrincipal().toString()), admin, creates);
    }

    private static Set<String> granted(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
