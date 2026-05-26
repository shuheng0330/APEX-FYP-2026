package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.model.RoleAuthorityId;
import com.tbm.careerpathlearning.model.RoleJobScopeId;
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
@RequestMapping("/api/role")
public class RoleController {

    private static final Logger logger = LoggerFactory.getLogger(RoleController.class);

    @Autowired
    private RoleService roleService;

    @Autowired
    private OrgChartService orgChartService;

    @Autowired
    private RoleJobScopeService roleJobScopeService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private JobScopeService jobScopeService;

    @Autowired
    private RoleAuthorityService roleAuthorityService;

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private RoleCompetencyService roleCompetencyService;

    @Autowired
    private ValidationService validationService;

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String TOGGLE_VISIBILITY_OPERATION = "Toggling Role's Visibility";

    private static final String ROLE_CREATION_OPERATION = "Role Creation";

    private static final String ROLE_EDIT_OPERATION = "Role Update";

    private static final String REMOVE_ROLE_OPERATION = "Remove Role";

    private static final String TOGGLE_VISIBILITY_OK = "toggle.role.visibility.ok.msg";

    private static final String ROLE_CREATION_OK = "role.creation.ok.msg";

    private static final String ROLE_EDIT_OK = "role.edit.ok.msg";

    private static final String ROLE_DELETE_OK = "role.delete.ok.msg";

    private static final String DEPT_NAME_NOTES = "role.overview.department.name.notes";

    private static final String ROLE_NAME_NOTES = "role.overview.role.name.notes";

    private static final String ROLE_DESC_NOTES = "role.overview.role.desc.notes";

    private static final String JOB_SCOPE_NOTES = "role.overview.jobScopes.notes";

    private static final String VISIBILITY_NOTES = "role.overview.visibility.notes";

    private static final String NEW_DEPT_NAME_NOTES = "role.overview.new.department.name.notes";

    private static final String NEW_ROLE_NAME_NOTES = "role.overview.new.role.name.notes";

    private static final String DELETED_NOTES = "role.overview.new.role.deleted.notes";

    private static final String INTERNAL_SERVER_ERROR_EXP_ERR_TITLE_CODE = "internal.server.err.title";

    private static final String EXPORT_ERR_MSG_CODE = "export.data.err.msg";

    private static final String IMPORT_OPERATION = "Import Role Overview Data";

    private static final String IMPORT_INVALID_DATA_ERR_MSG_CODE = "import.invalid.data.err.msg";

    private static final String DEPT_NAME_COLUMN = "Department Name";

    private static final String ROLE_NAME_COLUMN = "Role Name";

    private static final String ROLE_DESC_COLUMN = "Role Description";

    private static final String JOB_SCOPE_COLUMN = "Job Scope";

    private static final String IMPORT_UNIQUE_NAME_ERR_MSG_CODE = "import.unique.name.err.msg";

    private static final String IMPORT_ACTION_CONFLICT_ERR_MSG_CODE = "import.action.conflict.err.msg";

    private static final String IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE = "import.file.too.large.err.msg";

    private static final String IMPORT_FILE_INVALID_ERR_MSG_CODE = "import.file.invalid.err.msg";

    private static final String IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE = "import.duplicate.entry.err.msg";

    private static final String IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE = "import.data.not.found.err.msg";

    private static final String IMPORT_OK = "data.import.ok.msg";

    private static final String YES = "Yes";

    private static final int MAX_FILE_SIZE_IN_MB = 5;

    private static final String ACCEPTED_IMPORT_FILE_TYPE = ".xlsx";

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @GetMapping
    public ResponseEntity<?> getAllRoles(Authentication authentication) {
        List<RoleDto> roleDtoList = roleService.getAllByDeletedIsFalse();
        return ResponseEntity.ok(roleDtoList);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_VIEW_INVISIBLE_ROLE.getAuthorityName()
            )
            """)
    @GetMapping("/overview")
    public ResponseEntity<?> getRoleOverview(Authentication authentication) {
        List<RoleOverviewDto> roleOverviewDtoList = new ArrayList<>();

        List<RoleDto> roleDtoList = roleService.getAllByDeletedIsFalse();
        List<RoleJobScopeDto> roleJobScopeDtoList = roleJobScopeService.findAll();

        for (RoleDto roleDto : roleDtoList) {
            RoleOverviewDto roleOverviewDto = new RoleOverviewDto();

            List<RoleJobScopeDto> assignedRoleJobScopeDtoList = roleJobScopeDtoList.stream()
                    .filter(roleJobScopeDto -> Objects.equals(roleJobScopeDto.getRole().getId(), roleDto.getId()))
                    .toList();

            List<JobScopeDto> assignedJobScopeDtoList = assignedRoleJobScopeDtoList.stream()
                    .map(RoleJobScopeDto::getJobScope).collect(Collectors.toList());

            roleOverviewDto.setVisible(roleDto.isVisible());
            roleOverviewDto.setOrgChartId(roleDto.getOrgChart().getId());
            roleOverviewDto.setOrgChartName(roleDto.getOrgChart().getName());
            roleOverviewDto.setOrgChartDeleted(roleDto.getOrgChart().isDeleted());
            roleOverviewDto.setRoleId(roleDto.getId());
            roleOverviewDto.setRoleName(roleDto.getName());
            roleOverviewDto.setDescription(roleDto.getDescription());
            roleOverviewDto.setAssignedJobScopes(assignedJobScopeDtoList);

            roleOverviewDtoList.add(roleOverviewDto);
        }

        return ResponseEntity.ok(roleOverviewDtoList);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @GetMapping("/jobScope-map")
    public ResponseEntity<?> getAllRoleJobScopeMap() {
        List<RoleJobScopeDto> roleJobScopeDtoList = roleJobScopeService.findAll();
        Set<Long> roleIds = roleJobScopeDtoList.stream().map(dto -> dto.getId().getRoleId()).collect(Collectors.toSet());

        List<RoleJobScopeMapDto> roleJobScopeMapDtoList = roleIds.stream().map(id ->
                        new RoleJobScopeMapDto(
                                id,
                                roleJobScopeDtoList.stream()
                                        .filter(dto -> Objects.equals(dto.getId().getRoleId(), id))
                                        .map(RoleJobScopeDto::getJobScope)
                                        .collect(Collectors.toList())))
                .toList();

        return ResponseEntity.ok(roleJobScopeMapDtoList);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @PutMapping("/toggle-visibility")
    @Transactional
    public ResponseEntity<?> toggleVisibility(@RequestBody ToggleRoleVisibilityRequest request, Authentication authentication) {
        if (request == null || request.getRoleId() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{TOGGLE_VISIBILITY_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        Long roleId = request.getRoleId();
        boolean visibility = request.getVisibility();

        RoleDto roleDto = roleService.getAllById(roleId);
        if (roleDto == null) {
            String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
            throw new DataAccessException(errorMessage);
        }
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);
        roleDto.setVisible(visibility);
        roleDto.setUpdatedBy(userUUID);
        roleDto.setUpdatedAt(OffsetDateTime.now());

        this.roleService.update(roleId, roleDto);
        return ResponseEntity.ok(Map.of("message", messageSource.getMessage(TOGGLE_VISIBILITY_OK, null, Locale.getDefault())));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @PostMapping
    @Transactional
    public ResponseEntity<?> createRole(@RequestBody CreateRoleRequestDto requestDto, Authentication authentication) {
        if (requestDto == null || requestDto.getOrgChartId() == null || requestDto.getRoleName() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{ROLE_CREATION_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        OrgChartDto existedOrgChartDto = orgChartService.getByById(requestDto.getOrgChartId());

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        RoleDto roleDto = new RoleDto();
        roleDto.setName(requestDto.getRoleName().trim());
        roleDto.setDescription(requestDto.getDescription() == null || requestDto.getDescription().trim().isEmpty() ? null : requestDto.getDescription().trim());
        roleDto.setVisible(requestDto.getVisibility());
        roleDto.setDeleted(false);
        roleDto.setCreatedBy(userUUID);
        roleDto.setCreatedAt(OffsetDateTime.now());
        roleDto.setUpdatedBy(userUUID);
        roleDto.setUpdatedAt(OffsetDateTime.now());
        roleDto.setOrgChart(existedOrgChartDto);

        RoleDto createdRoleDto = roleService.create(roleDto); //create role first

        AuthorityDto authorityDto = authorityService.findByName(AuthorityName.ROLE_USER);
        RoleAuthorityDto roleAuthorityDto = new RoleAuthorityDto();
        roleAuthorityDto.setRole(createdRoleDto);
        roleAuthorityDto.setAuthority(authorityDto);
        roleAuthorityDto.setCreatedAt(OffsetDateTime.now());
        roleAuthorityDto.setUpdatedAt(OffsetDateTime.now());
        roleAuthorityDto.setCreatedBy(userUUID);
        roleAuthorityDto.setUpdatedBy(userUUID);
        roleAuthorityDto.setId(new RoleAuthorityId(createdRoleDto.getId(), authorityDto.getId()));

        roleAuthorityService.create(roleAuthorityDto); // assign basic authority (ROLE_USER)

        if (requestDto.getJobScopeList() != null && !requestDto.getJobScopeList().isEmpty()) {
            List<JobScopeDto> jobScopeToBeCreated = new ArrayList<>();

            for (String jobScope : requestDto.getJobScopeList()) {
                JobScopeDto jobScopeDto = new JobScopeDto();

                jobScopeDto.setJobScope(jobScope);
                jobScopeDto.setDeleted(false);
                jobScopeDto.setCreatedBy(userUUID);
                jobScopeDto.setCreatedAt(OffsetDateTime.now());
                jobScopeDto.setUpdatedBy(userUUID);
                jobScopeDto.setUpdatedAt(OffsetDateTime.now());

                jobScopeToBeCreated.add(jobScopeDto);
            }

            List<JobScopeDto> createdJobScopeDtoList = jobScopeService.createAll(jobScopeToBeCreated);
            // create job Scope if it doesn't exist

            List<RoleJobScopeDto> roleJobScopeDtoList = new ArrayList<>();

            for (JobScopeDto createdJobScopeDto : createdJobScopeDtoList) {
                RoleJobScopeDto roleJobScopeDto = new RoleJobScopeDto();

                roleJobScopeDto.setId(new RoleJobScopeId(createdRoleDto.getId(), createdJobScopeDto.getId()));
                roleJobScopeDto.setRole(createdRoleDto);
                roleJobScopeDto.setJobScope(createdJobScopeDto);
                roleJobScopeDto.setCreatedBy(userUUID);
                roleJobScopeDto.setCreatedAt(OffsetDateTime.now());
                roleJobScopeDto.setUpdatedBy(userUUID);
                roleJobScopeDto.setUpdatedAt(OffsetDateTime.now());

                roleJobScopeDtoList.add(roleJobScopeDto);
            }

            //create role jobScope
            roleJobScopeService.createAll(roleJobScopeDtoList);
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ROLE_CREATION_OK, null, Locale.getDefault()),
                "createdRole", createdRoleDto
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @PutMapping("/edit-role")
    @Transactional
    public ResponseEntity<?> updateRole(@RequestBody EditRoleRequestDto requestDto, Authentication authentication) {
        if (requestDto == null || requestDto.getOrgChartId() == null || requestDto.getRoleId() == null || requestDto.getRoleName() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{ROLE_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        Long orgChartId = requestDto.getOrgChartId();
        OrgChartDto existedOrgChartDto = orgChartService.getByById(orgChartId);

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Long roleId = requestDto.getRoleId();

        RoleDto roleDto = new RoleDto();
        roleDto.setId(roleId);
        roleDto.setName(requestDto.getRoleName());
        roleDto.setDescription(requestDto.getDescription());
        roleDto.setVisible(requestDto.getVisibility());
        roleDto.setDeleted(false);
        roleDto.setOrgChart(existedOrgChartDto);
        roleDto.setUpdatedBy(userUUID);
        roleDto.setUpdatedAt(OffsetDateTime.now());

        RoleDto updatedRoleDto = roleService.update(roleId, roleDto);

        List<RoleJobScopeDto> existingRoleJobScopeDtoList = roleJobScopeService.findAllByRoleId(roleId);
        Set<Long> existingJobScopeIds = existingRoleJobScopeDtoList.stream()
                .map(rjs -> rjs.getId().getJobScopeId())
                .collect(Collectors.toSet());

        List<JobScopeDto> requestedJobScopes;

        if (requestDto.getJobScopeList() != null && !requestDto.getJobScopeList().isEmpty()) {
            requestedJobScopes = jobScopeService.createAll(
                    requestDto.getJobScopeList().stream()
                            .map(name -> {
                                JobScopeDto dto = new JobScopeDto();
                                dto.setJobScope(name.trim());
                                dto.setDeleted(false);
                                dto.setCreatedBy(userUUID);
                                dto.setCreatedAt(OffsetDateTime.now());
                                dto.setUpdatedBy(userUUID);
                                dto.setUpdatedAt(OffsetDateTime.now());
                                return dto;
                            })
                            .collect(Collectors.toList())
            );
        } else {
            requestedJobScopes = Collections.emptyList();
        }

        Set<Long> requestedJobScopeIds = requestedJobScopes.stream()
                .map(JobScopeDto::getId)
                .collect(Collectors.toSet());

        Set<Long> toAdd = new HashSet<>(requestedJobScopeIds);
        toAdd.removeAll(existingJobScopeIds);

        Set<Long> toRemove = new HashSet<>(existingJobScopeIds);
        toRemove.removeAll(requestedJobScopeIds);

        if (!toAdd.isEmpty()) {
            List<RoleJobScopeDto> roleJobScopeDtoList = toAdd.stream()
                    .map(jobScopeId -> {
                        RoleJobScopeDto rjs = new RoleJobScopeDto();
                        rjs.setId(new RoleJobScopeId(roleId, jobScopeId));
                        rjs.setRole(updatedRoleDto);
                        rjs.setJobScope(requestedJobScopes.stream()
                                .filter(js -> js.getId().equals(jobScopeId))
                                .findFirst().orElseThrow());
                        rjs.setCreatedBy(userUUID);
                        rjs.setCreatedAt(OffsetDateTime.now());
                        rjs.setUpdatedBy(userUUID);
                        rjs.setUpdatedAt(OffsetDateTime.now());
                        return rjs;
                    })
                    .collect(Collectors.toList());

            roleJobScopeService.createAll(roleJobScopeDtoList);
        }

        if (!toRemove.isEmpty()) {
            roleJobScopeService.deleteByRoleIdAndJobScopeIdIn(roleId, toRemove);

            // check if jobScopeIds are used by other roles
            Set<Long> stillUsed = roleJobScopeService.findJobScopesUsedByOtherRoles(toRemove, roleId).stream()
                    .map(rjs -> rjs.getId().getJobScopeId())
                    .collect(Collectors.toSet());

            Set<Long> toDeleteJobScopes = new HashSet<>(toRemove);
            toDeleteJobScopes.removeAll(stillUsed);

            if (!toDeleteJobScopes.isEmpty()) {
                jobScopeService.deleteAllByIdIn(toDeleteJobScopes, userUUID);
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ROLE_EDIT_OK, null, Locale.getDefault()),
                "updatedRole", updatedRoleDto
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @DeleteMapping("/delete")
    @Transactional
    public ResponseEntity<?> deleteRole(
            @RequestParam Long roleId, Authentication authentication) {
        if (roleId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{REMOVE_ROLE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        List<RoleJobScopeDto> existingAssignedRoleJobScopeDtoList = roleJobScopeService.findAllByRoleId(roleId);

        if (!existingAssignedRoleJobScopeDtoList.isEmpty()) { //delete roleJobScope
            Set<RoleJobScopeId> existingAssignedRoleJobScopeIds = existingAssignedRoleJobScopeDtoList.stream()
                    .map(RoleJobScopeDto::getId)
                    .collect(Collectors.toSet());

            Set<Long> jobScopesToCheck = existingAssignedRoleJobScopeDtoList.stream()
                    .map(rjs -> rjs.getId().getJobScopeId())
                    .collect(Collectors.toSet());

            Set<Long> stillUsedJobScopeIds = roleJobScopeService.findJobScopesUsedByOtherRoles(jobScopesToCheck, roleId)
                    .stream().map(stillUsedJobScope -> stillUsedJobScope.getId().getJobScopeId())
                    .collect(Collectors.toSet());

            Set<Long> jobScopeToRemove = new HashSet<>(jobScopesToCheck);
            jobScopeToRemove.removeAll(stillUsedJobScopeIds); //delete job scopes that are no longer used

            roleJobScopeService.deleteAllByIdIn(existingAssignedRoleJobScopeIds);
            jobScopeService.deleteAllByIdIn(jobScopeToRemove, userUUID);
        }

        //delete roleAuthority
        roleAuthorityService.deleteAllByRoleId(roleId);

        roleCompetencyService.deleteAllByRoleId(roleId);

        //delete role
        roleService.delete(roleId, userUUID);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ROLE_DELETE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @DeleteMapping("/bulk-delete")
    @Transactional
    public ResponseEntity<?> bulkDeleteRole(
            @RequestParam List<Long> roleIds, Authentication authentication) {
        if (roleIds == null || roleIds.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{REMOVE_ROLE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<Long> roleIdsToRemove = new HashSet<>(roleIds);

        List<RoleJobScopeDto> existingAssignedRoleJobScopeDtoList = roleJobScopeService.findAllByRoleIdIn(roleIdsToRemove);

        if (!existingAssignedRoleJobScopeDtoList.isEmpty()) { //delete roleJobScope
            Set<RoleJobScopeId> existingAssignedRoleJobScopeIds = existingAssignedRoleJobScopeDtoList.stream()
                    .map(RoleJobScopeDto::getId)
                    .collect(Collectors.toSet());

            Set<Long> jobScopesToCheck = existingAssignedRoleJobScopeDtoList.stream()
                    .map(rjs -> rjs.getId().getJobScopeId())
                    .collect(Collectors.toSet());

            Set<Long> stillUsedJobScopeIds = roleJobScopeService.findJobScopesUsedByRoleIdNotIn(jobScopesToCheck, roleIdsToRemove)
                    .stream().map(stillUsedJobScope -> stillUsedJobScope.getId().getJobScopeId())
                    .collect(Collectors.toSet());

            Set<Long> jobScopeToRemove = new HashSet<>(jobScopesToCheck);
            jobScopeToRemove.removeAll(stillUsedJobScopeIds); //delete job scopes that are no longer used

            roleJobScopeService.deleteAllByIdIn(existingAssignedRoleJobScopeIds);
            jobScopeService.deleteAllByIdIn(jobScopeToRemove, userUUID);
        }

        //delete roleAuthority
        roleAuthorityService.deleteAllByRoleIdIn(roleIdsToRemove);

        roleCompetencyService.deleteAllByRoleIdIn(roleIdsToRemove);

        //delete role
        roleService.deleteAllByRoleIdIn(roleIdsToRemove, userUUID);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ROLE_DELETE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @GetMapping("/export")
    public ResponseEntity<?> exportRoleOverviewData(Authentication authentication) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sheet 1");
            XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
            int rowIndex = 0;

            // -- header
            Row header = sheet.createRow(rowIndex);

            Cell cellDeptName = header.createCell(0);
            cellDeptName.setCellValue("Department Name");
            createCellComment(drawing, cellDeptName, messageSource.getMessage(DEPT_NAME_NOTES, null, Locale.getDefault()));

            Cell cellRoleName = header.createCell(1);
            cellRoleName.setCellValue("Role Name");
            createCellComment(drawing, cellRoleName, messageSource.getMessage(ROLE_NAME_NOTES, null, Locale.getDefault()));

            Cell cellDesc = header.createCell(2);
            cellDesc.setCellValue("Role Description");
            createCellComment(drawing, cellDesc, messageSource.getMessage(ROLE_DESC_NOTES, null, Locale.getDefault()));

            Cell cellJobScope = header.createCell(3);
            cellJobScope.setCellValue("Job Scopes");
            createCellComment(drawing, cellJobScope, messageSource.getMessage(JOB_SCOPE_NOTES, null, Locale.getDefault()));

            Cell cellVisibility = header.createCell(4);
            cellVisibility.setCellValue("Visibility");
            createCellComment(drawing, cellVisibility, messageSource.getMessage(VISIBILITY_NOTES, null, Locale.getDefault()));

            Cell cellNewDeptName = header.createCell(5);
            cellNewDeptName.setCellValue("New Department Name");
            createCellComment(drawing, cellNewDeptName, messageSource.getMessage(NEW_DEPT_NAME_NOTES, null, Locale.getDefault()));

            Cell cellNewRoleName = header.createCell(6);
            cellNewRoleName.setCellValue("New Role Name");
            createCellComment(drawing, cellNewRoleName, messageSource.getMessage(NEW_ROLE_NAME_NOTES, null, Locale.getDefault()));

            Cell cellDeleted = header.createCell(7);
            cellDeleted.setCellValue("To Be Deleted");
            createCellComment(drawing, cellDeleted, messageSource.getMessage(DELETED_NOTES, null, Locale.getDefault()));

            rowIndex++;

            List<RoleDto> allRoles = roleService.getAllByDeletedIsFalse();
            List<RoleJobScopeDto> allAssignedJobScopes = roleJobScopeService.findAll();

            for (RoleDto dto : allRoles) {
                List<String> assignedJobScopes = allAssignedJobScopes.stream().filter(jobScope ->
                                Objects.equals(jobScope.getId().getRoleId(), dto.getId()))
                        .map(jobScope -> jobScope.getJobScope().getJobScope())
                        .toList();

                String jobScopes = "";
                if (!assignedJobScopes.isEmpty()) {
                    jobScopes = String.join("; ", assignedJobScopes);
                }

                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(dto.getOrgChart().getName());
                row.createCell(1).setCellValue(dto.getName());
                row.createCell(2).setCellValue(dto.getDescription());
                row.createCell(3).setCellValue(jobScopes);
                row.createCell(4).setCellValue(dto.isVisible() ? "Yes" : "");

                rowIndex++;
            }

            workbook.write(out);
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray());


            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Role_Overview_Data.xlsx");
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
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
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

        Map<String, OrgChartDto> allDepartmentMap = allDepartment.stream().collect(Collectors.toMap(
                dto -> dto.getName().trim().toLowerCase(),
                Function.identity()
        ));
        List<RoleDto> allRoles = roleService.getAllByDeletedIsFalse();
        Map<String, Set<String>> alldepartmentRoleNameMap = allDepartment.stream().collect(Collectors.toMap(
                dto -> dto.getName().trim().toLowerCase(),
                dto -> allRoles.stream().filter(role ->
                                Objects.equals(role.getOrgChart().getId(), dto.getId()))
                        .map(role -> role.getName().toLowerCase().trim())
                        .collect(Collectors.toSet())));
        List<RoleJobScopeDto> allAssignedJobScope = roleJobScopeService.findAll();

        Sheet sheet = workbook.getSheetAt(0);

        // -- check headers --
        Row header = sheet.getRow(0);
        if (!getCellValueAsString(header.getCell(0)).equalsIgnoreCase("Department Name")
                || !getCellValueAsString(header.getCell(1)).equalsIgnoreCase("Role Name")
                || !getCellValueAsString(header.getCell(2)).equalsIgnoreCase("Role Description")
                || !getCellValueAsString(header.getCell(3)).equalsIgnoreCase("Job Scopes")
                || !getCellValueAsString(header.getCell(4)).equalsIgnoreCase("Visibility")
                || !getCellValueAsString(header.getCell(5)).equalsIgnoreCase("New Department Name")
                || !getCellValueAsString(header.getCell(6)).equalsIgnoreCase("New Role Name")
                || !getCellValueAsString(header.getCell(7)).equalsIgnoreCase("To Be Deleted")) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        List<RoleDto> roleDtoToBeUpdatedOrCreated = new ArrayList<>();
        Set<String> jobScopeToBeCreated = new HashSet<>();
        Map<String, Map<String, String>> roleJobScopeToBeCreated = new HashMap<>();
        Set<RoleJobScopeId> roleJobScopeIdsToBeDeleted = new HashSet<>();
        Map<String, Set<String>> roleToBeCreated = new HashMap<>();
        Set<Long> deletedRoleIds = new HashSet<>();

        Map<String, Set<String>> duplicatedRow = new HashMap<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header (row 0)
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(0) == null || getCellValueAsString(row.getCell(0)) == null
                    || getCellValueAsString(row.getCell(0)).isBlank()) continue;

            // -- data --
            String departmentName = getCellValueAsString(row.getCell(0));
            String roleName = getCellValueAsString(row.getCell(1));
            String roleDescription = getCellValueAsString(row.getCell(2));
            String jobScopesList = getCellValueAsString(row.getCell(3));
            String visibility = getCellValueAsString(row.getCell(4));
            String newDepartment = getCellValueAsString(row.getCell(5));
            String newRoleName = getCellValueAsString(row.getCell(6));
            String toBeDeleted = getCellValueAsString(row.getCell(7));

            Set<String> inputtedJobScopes;
            if (!validationService.isNullOrBlank(jobScopesList)) {
                int finalI = i;
                inputtedJobScopes = Arrays.stream(jobScopesList.split(";"))
                        .map(String::trim)
                        .filter(jobScope -> !validationService.isNullOrBlank(jobScope))
                        .peek(jobScope -> {
                            if (jobScope.length() > 1000) {
                                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE, new String[]{JOB_SCOPE_COLUMN, Integer.toString(finalI + 1)}, Locale.getDefault());
                                throw new BadRequestException(errorMessage);
                            }
                        })
                        .collect(Collectors.toSet());
            } else {
                inputtedJobScopes = Collections.emptySet();
            }

            if (validationService.isNullOrBlank(departmentName)) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE, new String[]{DEPT_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (validationService.isNullOrBlank(roleName) || roleName.trim().length() > 255 ||
                    (!validationService.isNullOrBlank(newRoleName) && newRoleName.trim().length() > 255)) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE, new String[]{ROLE_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(roleDescription) && roleDescription.trim().length() > 1000) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE, new String[]{ROLE_DESC_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (!allDepartmentMap.containsKey(departmentName.trim().toLowerCase())) {
                String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{departmentName}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(newDepartment) && !allDepartmentMap.containsKey(newDepartment.toLowerCase().trim())) {
                String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{newDepartment}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (toBeDeleted != null && toBeDeleted.equalsIgnoreCase(YES) &&
                    (!validationService.isNullOrBlank(newDepartment) || !validationService.isNullOrBlank(newRoleName))) { // perform deletion and update at the same time
                String errorMessage = messageSource.getMessage(IMPORT_ACTION_CONFLICT_ERR_MSG_CODE,
                        new String[]{Integer.toString(i + 1)},
                        Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (duplicatedRow.get(departmentName.toLowerCase().trim()) != null) {
                if (duplicatedRow.get(departmentName.toLowerCase().trim()).contains(roleName.toLowerCase().trim())) {
                    String errorMessage = messageSource.getMessage(IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE, new String[]{Integer.toString(i + 1)}, Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                Set<String> assignedRoleNameSet = duplicatedRow.get(departmentName.toLowerCase().trim());
                assignedRoleNameSet.add(roleName.toLowerCase().trim());
                duplicatedRow.replace(departmentName.toLowerCase().trim(), assignedRoleNameSet);
            } else {
                Set<String> assignedRoleNameSet = new HashSet<>();
                assignedRoleNameSet.add(roleName.toLowerCase().trim());
                duplicatedRow.put(departmentName.toLowerCase().trim(), assignedRoleNameSet);
            }

            OrgChartDto selectedDepartment = Objects.requireNonNull(
                    allDepartment.stream().filter(dto ->
                                    departmentName.trim().equalsIgnoreCase(dto.getName().trim()))
                            .findFirst().orElse(null));

            RoleDto roleDto;
            Map<String, RoleJobScopeDto> assignedJobScopeMap = new HashMap<>();
            Set<String> assignedJobScopes = new HashSet<>();

            if (alldepartmentRoleNameMap.get(departmentName.toLowerCase()).contains(roleName.toLowerCase().trim())) { // update existing record
                roleDto = Objects.requireNonNull(
                        allRoles.stream().filter(dto ->
                                        roleName.trim().equalsIgnoreCase(dto.getName().trim())
                                                && departmentName.trim().equalsIgnoreCase(dto.getOrgChart().getName().trim()))
                                .findFirst().orElse(null));
                roleDto.setUpdatedBy(userUUID);
                roleDto.setUpdatedAt(now);

                // rename & reassignment
                if (!validationService.isNullOrBlank(newDepartment)) {
                    if (!validationService.isNullOrBlank(newRoleName)) { // reassign & rename
                        if (alldepartmentRoleNameMap.get(newDepartment.toLowerCase()).contains(newRoleName.toLowerCase())) {
                            String errorMessage = messageSource.getMessage(IMPORT_UNIQUE_NAME_ERR_MSG_CODE,
                                    new String[]{newRoleName, Integer.toString(i + 1)}, Locale.getDefault());

                            throw new BadRequestException(errorMessage);
                        }

                        roleDto.setName(newRoleName.trim());

                        alldepartmentRoleNameMap.get(departmentName.toLowerCase()).remove(roleName.toLowerCase().trim());
                        alldepartmentRoleNameMap.get(newDepartment.toLowerCase()).add(newRoleName.toLowerCase().trim());
                    } else { // reassign only
                        if (alldepartmentRoleNameMap.get(newDepartment.toLowerCase()).contains(roleName.toLowerCase())) {
                            String errorMessage = messageSource.getMessage(IMPORT_UNIQUE_NAME_ERR_MSG_CODE,
                                    new String[]{roleName, Integer.toString(i + 1)}, Locale.getDefault());

                            throw new BadRequestException(errorMessage);
                        }

                        roleDto.setName(roleName.trim());
                        alldepartmentRoleNameMap.get(departmentName.toLowerCase()).remove(roleName.toLowerCase().trim());
                        alldepartmentRoleNameMap.get(newDepartment.toLowerCase()).add(roleName.toLowerCase().trim());
                    }
                    roleDto.setOrgChart(allDepartmentMap.get(newDepartment.toLowerCase().trim()));
                } else {
                    if (!validationService.isNullOrBlank(newRoleName)) { // rename only
                        if (alldepartmentRoleNameMap.get(departmentName.toLowerCase()).contains(newRoleName.toLowerCase())) {
                            String errorMessage = messageSource.getMessage(IMPORT_UNIQUE_NAME_ERR_MSG_CODE,
                                    new String[]{newRoleName, Integer.toString(i + 1)}, Locale.getDefault());

                            throw new BadRequestException(errorMessage);
                        }

                        roleDto.setName(newRoleName.trim());
                        alldepartmentRoleNameMap.get(departmentName.toLowerCase()).remove(roleName.toLowerCase().trim());
                        alldepartmentRoleNameMap.get(departmentName.toLowerCase()).add(newRoleName.toLowerCase().trim());
                    }
                }

                assignedJobScopeMap.putAll(allAssignedJobScope.stream().filter(dto ->
                                Objects.equals(dto.getId().getRoleId(), roleDto.getId()))
                        .collect(Collectors.toMap(
                                dto -> dto.getJobScope().getJobScope().toLowerCase().trim(),
                                Function.identity()
                        )));
                assignedJobScopes.addAll(assignedJobScopeMap.values().stream()
                        .map(dto -> dto.getJobScope().getJobScope().toLowerCase().trim())
                        .collect(Collectors.toSet()));

                if (!validationService.isNullOrBlank(toBeDeleted) && toBeDeleted.equalsIgnoreCase(YES)) {
                    roleDto.setDeleted(true);
                    roleDtoToBeUpdatedOrCreated.add(roleDto);

                    deletedRoleIds.add(roleDto.getId());
                    roleJobScopeIdsToBeDeleted.addAll(assignedJobScopeMap.values().stream()
                            .map(RoleJobScopeDto::getId).collect(Collectors.toSet()));
                    continue;
                }

                roleDto.setDescription(validationService.isNullOrBlank(roleDescription) ? null : roleDescription.trim());
                roleDto.setVisible(!validationService.isNullOrBlank(visibility) && visibility.equalsIgnoreCase(YES));
            } else { // create new role
                if (!validationService.isNullOrBlank(newRoleName) || !validationService.isNullOrBlank(newDepartment) ||
                        (!validationService.isNullOrBlank(toBeDeleted) && toBeDeleted.equalsIgnoreCase(YES))) {
                    String errorMessage = messageSource.getMessage(IMPORT_ACTION_CONFLICT_ERR_MSG_CODE,
                            new String[]{Integer.toString(i + 1)},
                            Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                if (alldepartmentRoleNameMap.get(departmentName.trim().toLowerCase()).contains(roleName.trim().toLowerCase())) {
                    String errorMessage = messageSource.getMessage(IMPORT_UNIQUE_NAME_ERR_MSG_CODE,
                            new String[]{roleName, Integer.toString(i + 1)}, Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                roleDto = new RoleDto(
                        roleName.trim(),
                        validationService.isNullOrBlank(roleDescription) ? null : roleDescription.trim(),
                        !validationService.isNullOrBlank(visibility) && visibility.equalsIgnoreCase(YES),
                        false,
                        userUUID,
                        now,
                        userUUID,
                        now,
                        selectedDepartment
                );

                roleToBeCreated
                        .computeIfAbsent(departmentName.toLowerCase().trim(), d -> new HashSet<>())
                        .add(roleName.toLowerCase().trim());
            }


            Set<String> lowerCaseInputtedJobScopes = inputtedJobScopes.stream().map(jobScope -> jobScope.toLowerCase().trim()).collect(Collectors.toSet());
            Set<String> toAdd = new HashSet<>(lowerCaseInputtedJobScopes);
            toAdd.removeAll(assignedJobScopes);

            Set<String> toRemove = new HashSet<>(assignedJobScopes);
            toRemove.removeAll(lowerCaseInputtedJobScopes);

            if (!toRemove.isEmpty()) { // remove assigned job Scope
                Set<RoleJobScopeId> idToRemove = toRemove.stream().map(jobScopeToRemove ->
                                Objects.requireNonNull(assignedJobScopeMap.get(jobScopeToRemove.toLowerCase().trim()).getId()))
                        .collect(Collectors.toSet());

                roleJobScopeIdsToBeDeleted.addAll(idToRemove);
            }

            if (!toAdd.isEmpty()) { // add existing or newly created job scope
                Set<String> toAddBeforeToLowerCase = inputtedJobScopes.stream().filter(jobScope ->
                                toAdd.contains(jobScope.toLowerCase().trim()))
                        .collect(Collectors.toSet());
                jobScopeToBeCreated.addAll(toAddBeforeToLowerCase);

                toAddBeforeToLowerCase.forEach(jobScope -> {
                    if (roleJobScopeToBeCreated.containsKey(jobScope.toLowerCase())) {
                        Map<String, String> assignedRoleMap = roleJobScopeToBeCreated.get(jobScope.toLowerCase());
                        assignedRoleMap.put(selectedDepartment.getName().toLowerCase(), roleDto.getName().toLowerCase());
                        roleJobScopeToBeCreated.replace(jobScope.toLowerCase(), assignedRoleMap);
                    } else {
                        Map<String, String> departmentRoleMap = new HashMap<>();
                        departmentRoleMap.put(selectedDepartment.getName().toLowerCase(), roleDto.getName().toLowerCase());

                        roleJobScopeToBeCreated.put(jobScope.toLowerCase(), departmentRoleMap);
                    }
                });
            }

            roleDtoToBeUpdatedOrCreated.add(roleDto);
        }

        Map<Map<String, String>, RoleDto> createdRoleDtoMap;
        if (!roleDtoToBeUpdatedOrCreated.isEmpty()) {
            createdRoleDtoMap = roleService.createAndUpdateAll(roleDtoToBeUpdatedOrCreated)
                    .stream().collect(Collectors.toMap(
                            dto -> {
                                Map<String, String> orgChatRoleNameMap = new HashMap<>();
                                orgChatRoleNameMap.put(dto.getOrgChart().getName().toLowerCase().trim(), dto.getName().toLowerCase().trim());
                                return orgChatRoleNameMap;
                            },
                            Function.identity()
                    ));
        } else {
            createdRoleDtoMap = Collections.emptyMap();
        }

        if (!roleToBeCreated.isEmpty()) {
            AuthorityDto authorityDto = authorityService.findByName(AuthorityName.ROLE_USER);

            List<RoleAuthorityDto> roleAuthorityToBeCreated = roleToBeCreated.entrySet().stream().flatMap(entry ->
                    {
                        List<RoleAuthorityDto> roleAuthorityDtoList = entry.getValue().stream()
                                .map(roleName -> {
                                    Map<String, String> orgChartRoleNameMap = new HashMap<>();
                                    orgChartRoleNameMap.put(entry.getKey(), roleName);
                                    RoleDto roleDto = Objects.requireNonNull(createdRoleDtoMap.get(orgChartRoleNameMap));

                                    return new RoleAuthorityDto(
                                            new RoleAuthorityId(roleDto.getId(), authorityDto.getId()),
                                            roleDto,
                                            authorityDto,
                                            userUUID,
                                            now,
                                            userUUID,
                                            now
                                    );
                                })
                                .toList();

                        return roleAuthorityDtoList.stream();
                    })
                    .toList();

            roleAuthorityService.createAll(roleAuthorityToBeCreated);
        }

        if (!jobScopeToBeCreated.isEmpty()) {
            List<JobScopeDto> jobScopeDtoToBeCreated = jobScopeToBeCreated.stream().map(jobScope ->
                            new JobScopeDto(
                                    jobScope.trim(),
                                    false,
                                    userUUID,
                                    now,
                                    userUUID,
                                    now))
                    .toList();

            Map<String, JobScopeDto> createdJobScopeMap = jobScopeService.createAll(jobScopeDtoToBeCreated).stream()
                    .collect(Collectors.toMap(
                            jobScope -> jobScope.getJobScope().trim().toLowerCase(),
                            Function.identity()
                    ));

            List<RoleJobScopeDto> roleJobScopeDtoToBeCreated = roleJobScopeToBeCreated.entrySet().stream().flatMap(entrySet -> {
                JobScopeDto createdJobScope = createdJobScopeMap.get(entrySet.getKey());
                List<RoleJobScopeDto> toAdd = entrySet.getValue().entrySet().stream().map(orgChartRoleSet -> {
                    Map<String, String> orgChartRoleStringMap = new HashMap<>();
                    orgChartRoleStringMap.put(orgChartRoleSet.getKey(), orgChartRoleSet.getValue());
                    RoleDto createdRoleDto = createdRoleDtoMap.get(orgChartRoleStringMap);

                    return new RoleJobScopeDto(
                            new RoleJobScopeId(createdRoleDto.getId(), createdJobScope.getId()),
                            createdRoleDto,
                            createdJobScope,
                            userUUID,
                            now,
                            userUUID,
                            now
                    );
                }).toList();

                return toAdd.stream();
            }).toList();

            roleJobScopeService.createAll(roleJobScopeDtoToBeCreated);
        }

        if (!deletedRoleIds.isEmpty()) {
            roleAuthorityService.deleteAllByRoleIdIn(deletedRoleIds);

            roleCompetencyService.deleteAllByRoleIdIn(deletedRoleIds);
        }

        if (!roleJobScopeIdsToBeDeleted.isEmpty()) {
            roleJobScopeService.deleteAllByIdIn(roleJobScopeIdsToBeDeleted);

            Set<Long> jobScopeIdToCheck = roleJobScopeIdsToBeDeleted.stream().map(RoleJobScopeId::getJobScopeId).collect(Collectors.toSet());
            Set<Long> stillInUsed = roleJobScopeService.findAllByJobScopeIdIn(jobScopeIdToCheck)
                    .stream().map(dto -> dto.getId().getJobScopeId()).collect(Collectors.toSet());

            Set<Long> toRemove = new HashSet<>(jobScopeIdToCheck);
            toRemove.removeAll(stillInUsed);

            jobScopeService.deleteAllByIdIn(toRemove, userUUID);
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
