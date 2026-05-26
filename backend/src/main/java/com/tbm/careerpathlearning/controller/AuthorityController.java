package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.model.RoleAuthorityId;
import com.tbm.careerpathlearning.service.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthorityController {

    @Autowired
    private RoleService roleService;

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private RoleAuthorityService roleAuthorityService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private OrgChartService orgChartService;

    @Autowired
    private ValidationService validationService;

    private static final Logger logger = LoggerFactory.getLogger(AuthorityController.class);

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String GRANT_ACCESS_OPERATION = "Grant Access";

    private static final String EDIT_GRANT_ACCESS_OPERATION = "Edit Granted Access";

    private static final String REMOVE_GRANT_ACCESS_OPERATION = "Remove Granted Access";

    private static final String GRANTED_ACCESS_REDUNDANT_ERR_TITLE_CODE = "database.record.redundant.err.title";

    private static final String GRANTED_ACCESS_REDUNDANT_ERR_MSG_CODE = "database.access.redundant.err.msg";

    private static final String ACCESS_GRANTED_OK = "access.granted.ok.msg";

    private static final String ACCESS_REMOVED_OK = "access.remove.ok.msg";

    private static final String DEPT_NAME_NOTES = "access.control.department.name.notes";

    private static final String ROLE_NAME_NOTES = "access.control.role.name.notes";

    private static final String USER_NOTES = "auth.role.user.desc";

    private static final String VIEW_ACCESS_CONTROL_NOTES = "auth.can.view.access.control.desc";

    private static final String MANAGE_ACCESS_CONTROL_NOTES = "auth.can.manage.access.control.desc";

    private static final String VIEW_STAFF_NOTES = "auth.can.view.staff.desc";

    private static final String MANAGE_STAFF_NOTES = "auth.can.manage.staff.desc";

    private static final String VIEW_INVISIBLE_ROLE_NOTES = "auth.can.view.invisible.role.desc";

    private static final String MANAGE_ROLE_NOTES = "auth.can.manage.role.desc";

    private static final String MANAGE_COMPETENCY_NOTES = "auth.can.manage.competency.desc";

    private static final String PROPOSE_ROLE_COMPETENCIES_NOTES = "auth.can.propose.role.competencies.desc";

    private static final String MANAGE_CAREER_PATHWAY_NOTES = "auth.can.manage.career.pathway.desc";

    private static final String MANAGE_ORG_CHART_NOTES = "auth.can.manage.org.chart.desc";

    private static final String MANAGE_TRAINING_NOTES = "auth.can.manage.training.desc";

    private static final String ASSIGN_TRAINING_NOTES = "auth.can.assign.training.desc";

    private static final String MANAGE_LEARNING_MATERIAL_NOTES = "auth.can.manage.learning.material.desc";

    private static final String MANAGE_EVALUATION_NOTES = "auth.can.manage.evaluation.desc";

    private static final String MANAGE_EVALUATION_CYCLE_NOTES = "auth.can.manage.evaluation.cycle.desc";

    private static final String DEPT_NAME_COLUMN = "Department Name";

    private static final String ROLE_NAME_COLUMN = "Role Name";

    private static final String USER_COLUMN = "User";

    private static final String VIEW_ACCESS_CONTROL_COLUMN = "View Access Control";

    private static final String MANAGE_ACCESS_CONTROL_COLUMN = "Manage Access Control";

    private static final String VIEW_STAFF_COLUMN = "View Staff Account";

    private static final String MANAGE_STAFF_COLUMN = "Manage Staff Account";

    private static final String VIEW_INVISIBLE_ROLE_COLUMN = "View Invisible Role";

    private static final String MANAGE_ROLE_COLUMN = "Manage Role";

    private static final String MANAGE_COMPETENCY_COLUMN = "Manage Competency";

    private static final String PROPOSE_ROLE_COMPETENCIES_COLUMN = "Propose Role Competencies";

    private static final String MANAGE_CAREER_PATHWAY_COLUMN = "Manage Career Pathway";

    private static final String MANAGE_ORG_CHART_COLUMN = "Manage Org Chart";

    private static final String MANAGE_TRAINING_COLUMN = "Manage Training";

    private static final String ASSIGN_TRAINING_COLUMN = "Assign Training";

    private static final String MANAGE_LEARNING_MATERIAL_COLUMN = "Manage Learning Material";

    private static final String MANAGE_EVALUATION_COLUMN = "Manage Evaluation";

    private static final String MANAGE_EVALUATION_CYCLE_COLUMN = "Manage Evaluation Cycle";

    private static final String INTERNAL_SERVER_ERROR_EXP_ERR_TITLE_CODE = "internal.server.err.title";

    private static final String EXPORT_ERR_MSG_CODE = "export.data.err.msg";

    private static final String IMPORT_OPERATION = "Import Role Assignment Data";

    private static final String IMPORT_INVALID_DATA_ERR_MSG_CODE = "import.invalid.data.err.msg";

    private static final String IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE = "import.file.too.large.err.msg";

    private static final String IMPORT_FILE_INVALID_ERR_MSG_CODE = "import.file.invalid.err.msg";

    private static final String IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE = "import.duplicate.entry.err.msg";

    private static final String IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE = "import.data.not.found.err.msg";

    private static final String YES = "Yes";

    private static final String IMPORT_OK = "data.import.ok.msg";

    private static final int MAX_FILE_SIZE_IN_MB = 5;

    private static final String ACCEPTED_IMPORT_FILE_TYPE = ".xlsx";

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_VIEW_ACCESS_CONTROL.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()
            )
            """)
    @GetMapping("/access-control-overview")
    public ResponseEntity<?> getStaffAccessControlOverview(Authentication auth) {
        List<TranslatedAuthorityDto> translatedAuthorityDtoList = authorityService.getAllTranslatedAuthorities();

        if (translatedAuthorityDtoList.isEmpty()) {
            String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());

            throw new DataAccessException(errorMessage);
        }

        List<RoleDto> roleWithPermissionList = roleService.getAllByDeletedIsFalse();
        Map<Long, RoleAuthorityOverviewDto> overviewMap = new HashMap<>();

        for (RoleDto roleDto : roleWithPermissionList) {
            Long roleId = roleDto.getId();
            String roleName = roleDto.getName();
            OrgChartDto orgChartDto = roleDto.getOrgChart();

            Map<Long, Boolean> roleAuthorityMap = roleAuthorityService.getRoleAuthorityMapByRoleId(roleId);

            RoleAuthorityMapDto roleAuthorityMapDto = new RoleAuthorityMapDto();
            roleAuthorityMapDto.setRoleId(roleId);
            roleAuthorityMapDto.setRoleName(roleName);
            roleAuthorityMapDto.setAuthorityMap(roleAuthorityMap);

            RoleAuthorityOverviewDto overviewDto = overviewMap.computeIfAbsent(
                    orgChartDto.getId(),
                    id -> new RoleAuthorityOverviewDto(
                            orgChartDto.getId(),
                            orgChartDto.getName(),
                            orgChartDto.isDeleted(),
                            new ArrayList<>()
                    )
            );
            overviewDto.getRoles().add(roleAuthorityMapDto);
        }

        List<RoleAuthorityOverviewDto> roleAuthorityOverviewDtoList = new ArrayList<>(overviewMap.values());

        return ResponseEntity.ok(Map.of(
                "permissions", translatedAuthorityDtoList,
                "departments", roleAuthorityOverviewDtoList
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()
            )
            """)
    @PostMapping("/grant-access")
    @Transactional
    public ResponseEntity<?> grantAccess(@RequestBody GrantAccessDto grantAccessDto, Authentication auth) {
        if (grantAccessDto == null || grantAccessDto.getOrgChartId() == null || grantAccessDto.getRoleId() == null ||
                grantAccessDto.getSelectedAuthorities() == null || grantAccessDto.getSelectedAuthorities().isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{GRANT_ACCESS_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Long orgChartId = grantAccessDto.getOrgChartId();
        Long roleId = grantAccessDto.getRoleId();
        List<Long> selectedAuthorities = grantAccessDto.getSelectedAuthorities();

        RoleDto roleDto = roleService.getAllById(roleId);

        if (!Objects.equals(roleDto.getOrgChart().getId(), orgChartId)) {
            String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());

            throw new DataAccessException(errorMessage);
        }

        List<AuthorityDto> allAuthorities = authorityService.findAll();

        List<RoleAuthorityDto> existedRoleAuthority = roleAuthorityService.getAllByRoleId(roleId);
        Set<Long> existedAuthorityIds = existedRoleAuthority.stream()
                .map(dto -> dto.getId().getAuthorityId())
                .collect(Collectors.toSet());

        Set<Long> toAdd = new HashSet<>(selectedAuthorities);
        toAdd.removeAll(existedAuthorityIds);

        UUID userId = UUID.fromString(auth.getName());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));

        if (!toAdd.isEmpty()) {
            List<RoleAuthorityDto> roleAuthorityDtoList = toAdd.stream()
                    .map(authorityId -> new RoleAuthorityDto(
                            new RoleAuthorityId(roleId, authorityId),
                            roleDto,
                            Objects.requireNonNull(allAuthorities.stream().filter(dto -> dto.getId().equals(authorityId))
                                    .findFirst().orElse(null)),
                            userId,
                            now,
                            userId,
                            now
                    ))
                    .collect(Collectors.toList());

            this.roleAuthorityService.createAll(roleAuthorityDtoList);
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ACCESS_GRANTED_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()
            )
            """)
    @PutMapping("/edit-granted-access")
    @Transactional
    public ResponseEntity<?> editGrantedAccess(@RequestBody GrantAccessDto grantAccessDto, Authentication auth) {

        if (grantAccessDto == null || grantAccessDto.getOrgChartId() == null || grantAccessDto.getRoleId() == null ) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{EDIT_GRANT_ACCESS_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Long orgChartId = grantAccessDto.getOrgChartId();
        Long roleId = grantAccessDto.getRoleId();
        List<Long> selectedAuthorities = grantAccessDto.getSelectedAuthorities();

        RoleDto roleDto = roleService.getAllById(roleId);

        if (!Objects.equals(roleDto.getOrgChart().getId(), orgChartId)) {
            String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());

            throw new DataAccessException(errorMessage);
        }

        List<RoleAuthorityDto> existedRoleAuthorityList = roleAuthorityService.getAllByRoleId(roleId);
        Set<Long> existedAuthorityIds = existedRoleAuthorityList.stream()
                .map(existedRoleAuthority -> existedRoleAuthority.getAuthority().getId())
                .collect(Collectors.toSet());

        Set<Long> selectedAuthorityIds = new HashSet<>(selectedAuthorities);

        if (Objects.equals(existedAuthorityIds, selectedAuthorityIds)) { //skip editing the existing record if the input value same with the existing record
            return ResponseEntity.ok(Map.of(
                    "message", messageSource.getMessage(ACCESS_GRANTED_OK, null, Locale.getDefault())
            ));
        }

        Set<Long> toAdd = new HashSet<>(selectedAuthorities);
        toAdd.removeAll(existedAuthorityIds);

        Set<Long> toRemove = new HashSet<>(existedAuthorityIds);
        toRemove.removeAll(selectedAuthorityIds);

        UUID userId = UUID.fromString(auth.getName());

        // Add new roleAuthority
        if (!toAdd.isEmpty()) {
            List<RoleAuthorityDto> roleAuthorityDtoToAddList = new ArrayList<>();
            for (Long selectedAuthorityId : toAdd) {
                RoleAuthorityDto roleAuthorityDto = new RoleAuthorityDto();
                RoleAuthorityId roleAuthorityId = new RoleAuthorityId(roleId, selectedAuthorityId);
                AuthorityDto authorityDto = this.authorityService.findById(selectedAuthorityId);

                roleAuthorityDto.setId(roleAuthorityId);
                roleAuthorityDto.setRole(roleDto);
                roleAuthorityDto.setAuthority(authorityDto);
                roleAuthorityDto.setCreatedAt(OffsetDateTime.now());
                roleAuthorityDto.setUpdatedAt(OffsetDateTime.now());
                roleAuthorityDto.setCreatedBy(userId);
                roleAuthorityDto.setUpdatedBy(userId);

                roleAuthorityDtoToAddList.add(roleAuthorityDto);
            }

            this.roleAuthorityService.createAll(roleAuthorityDtoToAddList);
        }

        // Remove abandoned roleAuthority
        if (!toRemove.isEmpty()) {
            roleAuthorityService.deleteByRoleIdAndAuthorityIdIn(roleId, toRemove);
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ACCESS_GRANTED_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()
            )
            """)
    @DeleteMapping("/delete")
    @Transactional
    public ResponseEntity<?> deleteRoleAuthority(
            @RequestParam Long orgChartId,
            @RequestParam Long roleId, Authentication authentication) {
        if (orgChartId == null || roleId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{REMOVE_GRANT_ACCESS_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        RoleDto roleDto = roleService.getAllById(roleId);

        if (!Objects.equals(roleDto.getOrgChart().getId(), orgChartId)) {
            String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());

            throw new DataAccessException(errorMessage);
        }

        List<RoleAuthorityDto> existedRoleAuthorityList = roleAuthorityService.getAllByRoleId(roleId);

        if (existedRoleAuthorityList.isEmpty()) {
            String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());

            throw new DataAccessException(errorMessage);
        }

        Set<RoleAuthorityId> existedRoleAuthorityIds = existedRoleAuthorityList.stream()
                .map(RoleAuthorityDto::getId)
                .collect(Collectors.toSet());

        this.roleAuthorityService.deleteAllByIdIn(existedRoleAuthorityIds);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ACCESS_REMOVED_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()
            )
            """)
    @DeleteMapping("/bulk-delete")
    @Transactional
    public ResponseEntity<?> bulkDeleteRoleAuthority(@RequestBody List<Long> selectedRoleIds, Authentication auth) {
        if (selectedRoleIds == null || selectedRoleIds.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{REMOVE_GRANT_ACCESS_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Set<Long> selectedRoleIdsSet = new HashSet<>(selectedRoleIds);

        List<RoleDto> roleDtoList = roleService.getAllByIsDeletedIsFalseAndIdIn(selectedRoleIdsSet);

        if (roleDtoList.size() != selectedRoleIds.size()) {
            String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());

            throw new DataAccessException(errorMessage);
        }

        List<RoleAuthorityDto> existedRoleAuthorityList = roleAuthorityService.getAllByRoleIdIn(selectedRoleIdsSet);

        if (existedRoleAuthorityList.isEmpty()) {
            String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());

            throw new DataAccessException(errorMessage);
        }

        this.roleAuthorityService.deleteAllByRoleIdIn(selectedRoleIdsSet);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ACCESS_REMOVED_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()
            )
            """)
    @GetMapping("/export")
    public ResponseEntity<?> exportRoleAssignmentData(Authentication authentication) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sheet 1");
            XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
            int rowIndex = 0;

            // -- header
            Row header = sheet.createRow(rowIndex);

            Cell cellDeptName = header.createCell(0);
            cellDeptName.setCellValue(DEPT_NAME_COLUMN);
            createCellComment(drawing, cellDeptName, messageSource.getMessage(DEPT_NAME_NOTES, null, Locale.getDefault()));

            Cell cellRoleName = header.createCell(1);
            cellRoleName.setCellValue(ROLE_NAME_COLUMN);
            createCellComment(drawing, cellRoleName, messageSource.getMessage(ROLE_NAME_NOTES, null, Locale.getDefault()));

            Cell cellUser = header.createCell(2);
            cellUser.setCellValue(USER_COLUMN);
            createCellComment(drawing, cellUser, messageSource.getMessage(USER_NOTES, null, Locale.getDefault()));

            Cell cellViewAccessControl = header.createCell(3);
            cellViewAccessControl.setCellValue(VIEW_ACCESS_CONTROL_COLUMN);
            createCellComment(drawing, cellViewAccessControl, messageSource.getMessage(VIEW_ACCESS_CONTROL_NOTES, null, Locale.getDefault()));

            Cell cellManageAccessControl = header.createCell(4);
            cellManageAccessControl.setCellValue(MANAGE_ACCESS_CONTROL_COLUMN);
            createCellComment(drawing, cellManageAccessControl, messageSource.getMessage(MANAGE_ACCESS_CONTROL_NOTES, null, Locale.getDefault()));

            Cell cellViewStaff = header.createCell(5);
            cellViewStaff.setCellValue(VIEW_STAFF_COLUMN);
            createCellComment(drawing, cellViewStaff, messageSource.getMessage(VIEW_STAFF_NOTES, null, Locale.getDefault()));

            Cell cellManageStaff = header.createCell(6);
            cellManageStaff.setCellValue(MANAGE_STAFF_COLUMN);
            createCellComment(drawing, cellManageStaff, messageSource.getMessage(MANAGE_STAFF_NOTES, null, Locale.getDefault()));

            Cell cellViewInvisibleRole = header.createCell(7);
            cellViewInvisibleRole.setCellValue(VIEW_INVISIBLE_ROLE_COLUMN);
            createCellComment(drawing, cellViewInvisibleRole, messageSource.getMessage(VIEW_INVISIBLE_ROLE_NOTES, null, Locale.getDefault()));

            Cell cellManageRole = header.createCell(8);
            cellManageRole.setCellValue(MANAGE_ROLE_COLUMN);
            createCellComment(drawing, cellManageRole, messageSource.getMessage(MANAGE_ROLE_NOTES, null, Locale.getDefault()));

            Cell cellManageCompetency = header.createCell(9);
            cellManageCompetency.setCellValue(MANAGE_COMPETENCY_COLUMN);
            createCellComment(drawing, cellManageCompetency, messageSource.getMessage(MANAGE_COMPETENCY_NOTES, null, Locale.getDefault()));

            Cell cellPropose = header.createCell(10);
            cellPropose.setCellValue(PROPOSE_ROLE_COMPETENCIES_COLUMN);
            createCellComment(drawing, cellPropose, messageSource.getMessage(PROPOSE_ROLE_COMPETENCIES_NOTES, null, Locale.getDefault()));

            Cell cellManageCareerPathway = header.createCell(11);
            cellManageCareerPathway.setCellValue(MANAGE_CAREER_PATHWAY_COLUMN);
            createCellComment(drawing, cellManageCareerPathway, messageSource.getMessage(MANAGE_CAREER_PATHWAY_NOTES, null, Locale.getDefault()));

            Cell cellManageOrgChart = header.createCell(12);
            cellManageOrgChart.setCellValue(MANAGE_ORG_CHART_COLUMN);
            createCellComment(drawing, cellManageOrgChart, messageSource.getMessage(MANAGE_ORG_CHART_NOTES, null, Locale.getDefault()));

            Cell cellManageTraining = header.createCell(13);
            cellManageTraining.setCellValue(MANAGE_TRAINING_COLUMN);
            createCellComment(drawing, cellManageTraining, messageSource.getMessage(MANAGE_TRAINING_NOTES, null, Locale.getDefault()));

            Cell cellAssignTraining = header.createCell(14);
            cellAssignTraining.setCellValue(ASSIGN_TRAINING_COLUMN);
            createCellComment(drawing, cellAssignTraining, messageSource.getMessage(ASSIGN_TRAINING_NOTES, null, Locale.getDefault()));

            Cell cellManageLearningMaterial = header.createCell(15);
            cellManageLearningMaterial.setCellValue(MANAGE_LEARNING_MATERIAL_COLUMN);
            createCellComment(drawing, cellManageLearningMaterial, messageSource.getMessage(MANAGE_LEARNING_MATERIAL_NOTES, null, Locale.getDefault()));

            Cell cellManageEvaluation = header.createCell(16);
            cellManageEvaluation.setCellValue(MANAGE_EVALUATION_COLUMN);
            createCellComment(drawing, cellManageEvaluation, messageSource.getMessage(MANAGE_EVALUATION_NOTES, null, Locale.getDefault()));

            Cell cellManageEvaluationCycle = header.createCell(17);
            cellManageEvaluationCycle.setCellValue(MANAGE_EVALUATION_CYCLE_COLUMN);
            createCellComment(drawing, cellManageEvaluationCycle, messageSource.getMessage(MANAGE_EVALUATION_CYCLE_NOTES, null, Locale.getDefault()));

            rowIndex++;

            List<RoleDto> allRoles = roleService.getAllByDeletedIsFalse();
            List<RoleAuthorityDto> allAuthorities = roleAuthorityService.getAll();

            for (RoleDto dto : allRoles) {
                Set<AuthorityName> assignedAuthorities = allAuthorities.stream().filter(authority ->
                                Objects.equals(authority.getId().getRoleId(), dto.getId()))
                        .map(authority -> authority.getAuthority().getName())
                        .collect(Collectors.toSet());

                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(dto.getOrgChart().getName());
                row.createCell(1).setCellValue(dto.getName());
                row.createCell(2).setCellValue(assignedAuthorities.contains(AuthorityName.ROLE_USER) ? YES : null);
                row.createCell(3).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_VIEW_ACCESS_CONTROL) ? YES : null);
                row.createCell(4).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_MANAGE_ACCESS_CONTROL) ? YES : null);
                row.createCell(5).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_VIEW_STAFF) ? YES : null);
                row.createCell(6).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_MANAGE_STAFF) ? YES : null);
                row.createCell(7).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_VIEW_INVISIBLE_ROLE) ? YES : null);
                row.createCell(8).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_MANAGE_ROLE) ? YES : null);
                row.createCell(9).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_MANAGE_COMPETENCY) ? YES : null);
                row.createCell(10).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES) ? YES : null);
                row.createCell(11).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_MANAGE_CAREER_PATHWAY) ? YES : null);
                row.createCell(12).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_MANAGE_ORG_CHART) ? YES : null);
                row.createCell(13).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_MANAGE_TRAINING) ? YES : null);
                row.createCell(14).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_ASSIGN_TRAINING) ? YES : null);
                row.createCell(15).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_MANAGE_LEARNING_MATERIAL) ? YES : null);
                row.createCell(16).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_MANAGE_EVALUATION) ? YES : null);
                row.createCell(17).setCellValue(assignedAuthorities.contains(AuthorityName.CAN_MANAGE_EVALUATION_CYCLE) ? YES : null);

                rowIndex++;
            }

            workbook.write(out);
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Access_Control_Data.xlsx");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .body(resource);

        } catch (Exception e) {
            String errorTitle = messageSource.getMessage(INTERNAL_SERVER_ERROR_EXP_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(EXPORT_ERR_MSG_CODE, null, Locale.getDefault());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("title", errorTitle,
                            "message", errorMessage
                    ));
        }
    }

    private static void createCellComment(XSSFDrawing drawing, Cell cell, String text) {
        CreationHelper factory = cell.getSheet().getWorkbook().getCreationHelper();
        ClientAnchor anchor = factory.createClientAnchor();

        // Position the comment box (column + row offset)
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 3);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 3);

        // Create the comment and assign it
        Comment comment = drawing.createCellComment(anchor);
        RichTextString str = factory.createRichTextString(text);
        comment.setString(str);
        comment.setAuthor("System");

        cell.setCellComment(comment);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName()
            )
            """)
    @PostMapping("/import")
    @Transactional
    public ResponseEntity<?> importData(@RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {

        if (file.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{IMPORT_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        if (file.getSize() > MAX_FILE_SIZE_IN_MB * 1024 * 1024) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        List<String> acceptedTypes = List.of(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/octet-stream",
                ""
        );

        if (!acceptedTypes.contains(file.getContentType())) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(ACCEPTED_IMPORT_FILE_TYPE)) {
            String errorMessage = messageSource.getMessage(
                    IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        InputStream inputStream = file.getInputStream();
        Workbook workbook = new XSSFWorkbook(inputStream);

        // -- basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        List<OrgChartDto> allDepartment = orgChartService.findAllByIsDeletedIsFalse();
        List<RoleDto> allRoles = roleService.getAllByDeletedIsFalse();
        Map<String, Set<String>> alldepartmentRoleNameMap = allDepartment.stream().collect(Collectors.toMap(
                dto -> dto.getName().trim().toLowerCase(),
                dto -> allRoles.stream().filter(role ->
                                Objects.equals(role.getOrgChart().getId(), dto.getId()))
                        .map(role -> role.getName().toLowerCase().trim())
                        .collect(Collectors.toSet())));

        List<RoleAuthorityDto> allRoleAuthorities = roleAuthorityService.getAll();

        List<AuthorityDto> allAuthority = authorityService.findAll();

        Map<AuthorityName, Long> allAuthorityNameMap = allAuthority.stream().collect(Collectors.toMap(
                AuthorityDto::getName,
                AuthorityDto::getId
        ));
        Map<Long, AuthorityDto> allAuthorityMap = allAuthority.stream().collect(Collectors.toMap(
                AuthorityDto::getId,
                Function.identity()
        ));

        Sheet sheet = workbook.getSheetAt(0);

        // -- check headers --
        Row header = sheet.getRow(0);
        if (!getCellValueAsString(header.getCell(0)).equalsIgnoreCase(DEPT_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(1)).equalsIgnoreCase(ROLE_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(2)).equalsIgnoreCase(USER_COLUMN)
                || !getCellValueAsString(header.getCell(3)).equalsIgnoreCase(VIEW_ACCESS_CONTROL_COLUMN)
                || !getCellValueAsString(header.getCell(4)).equalsIgnoreCase(MANAGE_ACCESS_CONTROL_COLUMN)
                || !getCellValueAsString(header.getCell(5)).equalsIgnoreCase(VIEW_STAFF_COLUMN)
                || !getCellValueAsString(header.getCell(6)).equalsIgnoreCase(MANAGE_STAFF_COLUMN)
                || !getCellValueAsString(header.getCell(7)).equalsIgnoreCase(VIEW_INVISIBLE_ROLE_COLUMN)
                || !getCellValueAsString(header.getCell(8)).equalsIgnoreCase(MANAGE_ROLE_COLUMN)
                || !getCellValueAsString(header.getCell(9)).equalsIgnoreCase(MANAGE_COMPETENCY_COLUMN)
                || !getCellValueAsString(header.getCell(10)).equalsIgnoreCase(PROPOSE_ROLE_COMPETENCIES_COLUMN)
                || !getCellValueAsString(header.getCell(11)).equalsIgnoreCase(MANAGE_CAREER_PATHWAY_COLUMN)
                || !getCellValueAsString(header.getCell(12)).equalsIgnoreCase(MANAGE_ORG_CHART_COLUMN)
                || !getCellValueAsString(header.getCell(13)).equalsIgnoreCase(MANAGE_TRAINING_COLUMN)
                || !getCellValueAsString(header.getCell(14)).equalsIgnoreCase(ASSIGN_TRAINING_COLUMN)
                || !getCellValueAsString(header.getCell(15)).equalsIgnoreCase(MANAGE_LEARNING_MATERIAL_COLUMN)
                || !getCellValueAsString(header.getCell(16)).equalsIgnoreCase(MANAGE_EVALUATION_COLUMN)
                || !getCellValueAsString(header.getCell(17)).equalsIgnoreCase(MANAGE_EVALUATION_CYCLE_COLUMN)

        ) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        Map<String, Set<String>> duplicatedRow = new HashMap<>();
        List<RoleAuthorityDto> roleAuthorityDtoToCreate = new ArrayList<>();
        Set<RoleAuthorityId> roleAuthorityIdToDelete = new HashSet<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header (row 0)
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(0) == null || getCellValueAsString(row.getCell(0)) == null
                    || getCellValueAsString(row.getCell(0)).isBlank()) continue;

            // -- data --
            String departmentName = getCellValueAsString(row.getCell(0));
            String roleName = getCellValueAsString(row.getCell(1));
            String user = getCellValueAsString(row.getCell(2));
            String viewAccessControl = getCellValueAsString(row.getCell(3));
            String manageAccessControl = getCellValueAsString(row.getCell(4));
            String viewStaffAccount = getCellValueAsString(row.getCell(5));
            String manageStaffAccount = getCellValueAsString(row.getCell(6));
            String viewInvisibleRole = getCellValueAsString(row.getCell(7));
            String manageRole = getCellValueAsString(row.getCell(8));
            String manageCompetency = getCellValueAsString(row.getCell(9));
            String proposeRoleCompetency = getCellValueAsString(row.getCell(10));
            String manageCareerPathway = getCellValueAsString(row.getCell(11));
            String manageOrgChart = getCellValueAsString(row.getCell(12));
            String manageTraining = getCellValueAsString(row.getCell(13));
            String assignTraining = getCellValueAsString(row.getCell(14));
            String manageLearning = getCellValueAsString(row.getCell(15));
            String manageEvaluation = getCellValueAsString(row.getCell(16));
            String manageEvaluationCycle = getCellValueAsString(row.getCell(17));

            if (validationService.isNullOrBlank(departmentName)) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{DEPT_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (validationService.isNullOrBlank(roleName)) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{ROLE_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (!alldepartmentRoleNameMap.containsKey(departmentName.trim().toLowerCase())) {
                String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{departmentName}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (!alldepartmentRoleNameMap.get(departmentName.trim().toLowerCase()).contains(roleName.trim().toLowerCase())) {
                String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{roleName}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (duplicatedRow.get(departmentName.toLowerCase().trim()) != null) {
                if (duplicatedRow.get(departmentName.toLowerCase().trim()).contains(roleName.toLowerCase().trim())) {
                    String errorMessage = messageSource.getMessage(IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE, new String[]{Integer.toString(i + 1)}, Locale.getDefault());
                    throw new BadRequestException(errorMessage);
                }
            }

            duplicatedRow.computeIfAbsent(departmentName.toLowerCase().trim(), k -> new HashSet<>())
                    .add(roleName.toLowerCase().trim());

            RoleDto selectedRoleDto = Objects.requireNonNull(
                    allRoles.stream().filter(dto ->
                                    roleName.trim().equalsIgnoreCase(dto.getName().trim()))
                            .findFirst().orElse(null));
            Long roleId = selectedRoleDto.getId();

            Set<RoleAuthorityId> assignedAuthority = allRoleAuthorities.stream().map(RoleAuthorityDto::getId)
                    .filter(id ->
                            Objects.equals(id.getRoleId(), selectedRoleDto.getId())).collect(Collectors.toSet());

            Set<RoleAuthorityId> inputtedAuthority = new HashSet<>();

            if (!validationService.isNullOrBlank(user) && user.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.ROLE_USER)));
            }

            if (!validationService.isNullOrBlank(viewAccessControl) && viewAccessControl.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_VIEW_ACCESS_CONTROL)));
            }

            if (!validationService.isNullOrBlank(manageAccessControl) && manageAccessControl.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_MANAGE_ACCESS_CONTROL)));
            }

            if (!validationService.isNullOrBlank(viewStaffAccount) && viewStaffAccount.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_VIEW_STAFF)));
            }

            if (!validationService.isNullOrBlank(manageStaffAccount) && manageStaffAccount.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_MANAGE_STAFF)));
            }

            if (!validationService.isNullOrBlank(viewInvisibleRole) && viewInvisibleRole.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_VIEW_INVISIBLE_ROLE)));
            }

            if (!validationService.isNullOrBlank(manageRole) && manageRole.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_MANAGE_ROLE)));
            }

            if (!validationService.isNullOrBlank(manageCompetency) && manageCompetency.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_MANAGE_COMPETENCY)));
            }

            if (!validationService.isNullOrBlank(proposeRoleCompetency) && proposeRoleCompetency.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_PROPOSE_ROLE_COMPETENCIES)));
            }

            if (!validationService.isNullOrBlank(manageCareerPathway) && manageCareerPathway.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_MANAGE_CAREER_PATHWAY)));
            }

            if (!validationService.isNullOrBlank(manageOrgChart) && manageOrgChart.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_MANAGE_ORG_CHART)));
            }

            if (!validationService.isNullOrBlank(manageTraining) && manageTraining.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_MANAGE_TRAINING)));
            }

            if (!validationService.isNullOrBlank(assignTraining) && assignTraining.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_ASSIGN_TRAINING)));
            }

            if (!validationService.isNullOrBlank(manageLearning) && manageLearning.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_MANAGE_LEARNING_MATERIAL)));
            }

            if (!validationService.isNullOrBlank(manageEvaluation) && manageEvaluation.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_MANAGE_EVALUATION)));
            }

            if (!validationService.isNullOrBlank(manageEvaluationCycle) && manageEvaluationCycle.equalsIgnoreCase(YES)) {
                inputtedAuthority.add(new RoleAuthorityId(roleId, allAuthorityNameMap.get(AuthorityName.CAN_MANAGE_EVALUATION_CYCLE)));
            }

            Set<RoleAuthorityId> toAdd = new HashSet<>(inputtedAuthority);
            toAdd.removeAll(assignedAuthority);

            Set<RoleAuthorityId> toRemove = new HashSet<>(assignedAuthority);
            toRemove.removeAll(inputtedAuthority);
            roleAuthorityIdToDelete.addAll(toRemove);

            if (!toAdd.isEmpty()) {
                roleAuthorityDtoToCreate.addAll(toAdd.stream().map(id ->
                                new RoleAuthorityDto(
                                        id,
                                        selectedRoleDto,
                                        allAuthorityMap.get(id.getAuthorityId()),
                                        userUUID,
                                        now,
                                        userUUID,
                                        now))
                        .collect(Collectors.toSet()));
            }
        }

        if (!roleAuthorityDtoToCreate.isEmpty()) {
            roleAuthorityService.createAll(roleAuthorityDtoToCreate);
        }

        if (!roleAuthorityIdToDelete.isEmpty()) {
            roleAuthorityService.deleteAllByIdIn(roleAuthorityIdToDelete);
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(IMPORT_OK, null, Locale.getDefault())
        ));
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        } else if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        } else {
            return cell.getStringCellValue().trim();
        }
    }
}