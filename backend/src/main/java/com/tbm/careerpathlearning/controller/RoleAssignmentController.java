package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
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
@RequestMapping("/api/role/assignment")
public class RoleAssignmentController {

    @Autowired
    private RoleService roleService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private StaffService staffService;

    @Autowired
    private OrgChartService orgChartService;

    @Autowired
    private ValidationService validationService;

    private static final Logger logger = LoggerFactory.getLogger(RoleAssignmentController.class);

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String ROLE_ASSIGNMENT_CREATE_OPERATION = "Create Role Assignment";

    private static final String ROLE_ASSIGNMENT_EDIT_OPERATION = "Update Role Assignment";

    private static final String ROLE_ASSIGNMENT_REMOVE_OPERATION = "Remove Role Assignment";

    private static final String ROLE_ASSIGNMENT_CREATE_OK = "role.assignment.creation.ok.msg";

    private static final String ROLE_ASSIGNMENT_EDIT_OK = "role.assignment.edit.ok.msg";

    private static final String ROLE_ASSIGNMENT_DELETE_OK = "role.assignment.delete.ok.msg";

    private static final String DEPT_NAME_NOTES = "role.assignment.department.name.notes";

    private static final String ROLE_NAME_NOTES = "role.assignment.role.name.notes";

    private static final String STAFF_EMAIL_NOTES = "role.assignment.staff.email.notes";

    private static final String DEPT_NAME_COLUMN = "Department Name";

    private static final String ROLE_NAME_COLUMN = "Role Name";

    private static final String STAFF_EMAIL_COLUMN = "Staff Email";

    private static final String INTERNAL_SERVER_ERROR_EXP_ERR_TITLE_CODE = "internal.server.err.title";

    private static final String EXPORT_ERR_MSG_CODE = "export.data.err.msg";

    private static final String IMPORT_OPERATION = "Import Role Assignment Data";

    private static final String IMPORT_INVALID_DATA_ERR_MSG_CODE = "import.invalid.data.err.msg";

    private static final String IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE = "import.file.too.large.err.msg";

    private static final String IMPORT_FILE_INVALID_ERR_MSG_CODE = "import.file.invalid.err.msg";

    private static final String IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE = "import.duplicate.entry.err.msg";

    private static final String IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE = "import.data.not.found.err.msg";

    private static final String IMPORT_OK = "data.import.ok.msg";

    private static final int MAX_FILE_SIZE_IN_MB = 5;

    private static final String ACCEPTED_IMPORT_FILE_TYPE = ".xlsx";

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @GetMapping("/overview")
    public ResponseEntity<?> getRoleAssignmentOverview(Authentication authentication) {
        List<RoleAssignmentOverviewDto> roleAssignmentOverviewDtoList = new ArrayList<>();

        List<RoleDto> existingRoles = roleService.getAll();
        List<StaffDto> existingStaffs = staffService.findAllByIsDeletedIsFalse();

        for (RoleDto existingRoleDto : existingRoles) {
            RoleAssignmentOverviewDto roleAssignmentOverviewDto = new RoleAssignmentOverviewDto();

            roleAssignmentOverviewDto.setOrgChartId(existingRoleDto.getOrgChart().getId());
            roleAssignmentOverviewDto.setOrgChartName(existingRoleDto.getOrgChart().getName());
            roleAssignmentOverviewDto.setOrgChartDeleted(existingRoleDto.getOrgChart().isDeleted());
            roleAssignmentOverviewDto.setRoleId(existingRoleDto.getId());
            roleAssignmentOverviewDto.setRoleName(existingRoleDto.getName());
            roleAssignmentOverviewDto.setRoleDeleted(existingRoleDto.isDeleted());

            List<StaffDto> assignedStaffs = existingStaffs.stream()
                    .filter(staffDto ->
                            staffDto.getRole() != null &&
                                    Objects.equals(staffDto.getRole().getId(), existingRoleDto.getId())
                    )// role possibly could be null
                    .map(staffDto -> new StaffDto(staffDto.getId(), staffDto.getName(), staffDto.getEmail()))
                    .collect(Collectors.toList());

            roleAssignmentOverviewDto.setStaffList(assignedStaffs);

            if (!assignedStaffs.isEmpty() || !existingRoleDto.isDeleted()) {
                roleAssignmentOverviewDtoList.add(roleAssignmentOverviewDto);
            }
        }

        return ResponseEntity.ok(roleAssignmentOverviewDtoList);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @PostMapping
    @Transactional
    public ResponseEntity<?> createRoleAssignment(@RequestBody UpdateRoleAssignmentRequestDto requestDto, Authentication authentication) {
        if (requestDto == null || requestDto.getRoleId() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{ROLE_ASSIGNMENT_CREATE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);

        RoleDto roleDto = roleService.getAllById(requestDto.getRoleId());

        Set<UUID> staffIdsToBeUpdated = new HashSet<>(requestDto.getStaffIds());

        staffService.updateRoleByStaffIdIn(staffIdsToBeUpdated, roleDto, userUUID);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ROLE_ASSIGNMENT_CREATE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @PutMapping("edit-role-assignment")
    @Transactional
    public ResponseEntity<?> updateRoleAssignment(@RequestBody UpdateRoleAssignmentRequestDto requestDto, Authentication authentication) {
        if (requestDto == null || requestDto.getRoleId() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{ROLE_ASSIGNMENT_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getPrincipal().toString();
        UUID userUUID = UUID.fromString(userId);

        RoleDto roleDto = roleService.getAllById(requestDto.getRoleId());

        List<StaffDto> staffToBeUpdated = staffService.findAllByRoleId(requestDto.getRoleId());
        Set<UUID> staffIdsToBeUpdated = staffToBeUpdated.stream().map(StaffDto::getId).collect(Collectors.toSet());

        Set<UUID> staffIds = new HashSet<>(requestDto.getStaffIds());

        Set<UUID> toRemove = new HashSet<>(staffIdsToBeUpdated);
        toRemove.removeAll(staffIds);

        Set<UUID> toAdd = new HashSet<>(staffIds);
        toAdd.removeAll(staffIdsToBeUpdated);

        if (!toRemove.isEmpty()) {
            staffService.updateRoleByStaffIdIn(toRemove, null, userUUID);
        }

        if (!toAdd.isEmpty()) {
            staffService.updateRoleByStaffIdIn(toAdd, roleDto, userUUID);
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ROLE_ASSIGNMENT_EDIT_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @DeleteMapping("/delete")
    @Transactional
    public ResponseEntity<?> deleteRole(
            @RequestParam Long roleId, Authentication authentication) {
        if (roleId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{ROLE_ASSIGNMENT_REMOVE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        List<StaffDto> staffsToBeUpdated = staffService.findAllByRoleId(roleId);
        Set<UUID> staffIdsToBeUpdated = staffsToBeUpdated.stream().map(StaffDto::getId).collect(Collectors.toSet());

        staffService.updateRoleByStaffIdIn(staffIdsToBeUpdated, null, userUUID);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ROLE_ASSIGNMENT_DELETE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @DeleteMapping("/bulk-delete")
    @Transactional
    public ResponseEntity<?> bulkDeleteRole(
            @RequestParam List<Long> roleIds, Authentication authentication) {
        if (roleIds == null || roleIds.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{ROLE_ASSIGNMENT_REMOVE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<Long> roleIdsToBeRemoved = new HashSet<>(roleIds);

        List<StaffDto> staffsToBeUpdated = staffService.findAllByRoleIdIn(roleIdsToBeRemoved);
        Set<UUID> staffIdsToBeUpdated = staffsToBeUpdated.stream().map(StaffDto::getId).collect(Collectors.toSet());

        staffService.updateRoleByStaffIdIn(staffIdsToBeUpdated, null, userUUID);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ROLE_ASSIGNMENT_DELETE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
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

            Cell cellDesc = header.createCell(2);
            cellDesc.setCellValue(STAFF_EMAIL_COLUMN);
            createCellComment(drawing, cellDesc, messageSource.getMessage(STAFF_EMAIL_NOTES, null, Locale.getDefault()));

            rowIndex++;

            List<RoleDto> allRoles = roleService.getAllByDeletedIsFalse();
            List<StaffDto> allStaff = staffService.findAllByIsDeletedIsFalse();

            for (RoleDto dto : allRoles) {
                String assignedStaff = allStaff.stream().filter(staff ->
                                staff.getRole() != null && Objects.equals(staff.getRole().getId(), dto.getId()))
                        .map(StaffDto::getEmail)
                        .collect(Collectors.joining("; "));

                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(dto.getOrgChart().getName());
                row.createCell(1).setCellValue(dto.getName());
                row.createCell(2).setCellValue(assignedStaff);

                rowIndex++;
            }

            workbook.write(out);
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray());


            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Role_Assignment_Data.xlsx");
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
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
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

        Map<String, StaffDto> allEmailStaffMap = staffService.findAllByIsDeletedIsFalse().stream()
                .collect(Collectors.toMap(
                        StaffDto::getEmail,
                        Function.identity()
                ));

        Map<Long, Set<String>> allRoleStaffMap = new HashMap<>();

        allEmailStaffMap.values().forEach(staffMap -> {
            if (staffMap.getRole() != null) {
                allRoleStaffMap.computeIfAbsent(staffMap.getRole().getId(), k -> new HashSet<>())
                        .add(staffMap.getEmail().toLowerCase().trim());
            }
        });

        Sheet sheet = workbook.getSheetAt(0);

        // -- check headers --
        Row header = sheet.getRow(0);
        if (!getCellValueAsString(header.getCell(0)).equalsIgnoreCase(DEPT_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(1)).equalsIgnoreCase(ROLE_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(2)).equalsIgnoreCase(STAFF_EMAIL_COLUMN)) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        Map<UUID, RoleDto> pendingRoleAssignments = new HashMap<>();
        Map<String, Set<String>> duplicatedRow = new HashMap<>();
        Set<String> emailSeen = new HashSet<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header (row 0)
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(0) == null || getCellValueAsString(row.getCell(0)) == null
                    || getCellValueAsString(row.getCell(0)).isBlank()) continue;

            // -- data --
            String departmentName = getCellValueAsString(row.getCell(0));
            String roleName = getCellValueAsString(row.getCell(1));
            String staffEmails = getCellValueAsString(row.getCell(2));

            Set<String> emails;
            if (!validationService.isNullOrBlank(staffEmails)) {
                int finalI = i;
                emails = Arrays.stream(staffEmails.split(";"))
                        .map(String::trim)
                        .filter(email -> !validationService.isNullOrBlank(email))
                        .peek(email -> {
                            if (email.length() > 255 || !allEmailStaffMap.containsKey(email)) {
                                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                                        new String[]{STAFF_EMAIL_COLUMN, Integer.toString(finalI + 1)}, Locale.getDefault());
                                throw new BadRequestException(errorMessage);
                            }

                            if (emailSeen.contains(email)) {
                                String errorMessage = messageSource.getMessage(IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE, new String[]{Integer.toString(finalI + 1)}, Locale.getDefault());

                                throw new BadRequestException(errorMessage);
                            }

                            emailSeen.add(email);
                        })
                        .collect(Collectors.toSet());
            } else {
                emails = Collections.emptySet();
            }

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

            duplicatedRow.computeIfAbsent(departmentName.trim().toLowerCase(), k -> new HashSet<>())
                    .add(roleName.trim().toLowerCase());

            RoleDto selectedRoleDto = Objects.requireNonNull(
                    allRoles.stream().filter(dto ->
                                    roleName.trim().equalsIgnoreCase(dto.getName().trim()))
                            .findFirst().orElse(null));

            Set<String> assignedStaffs = allRoleStaffMap.get(selectedRoleDto.getId()) == null ?
                    new HashSet<>() : allRoleStaffMap.get(selectedRoleDto.getId());

            Set<String> toAdd = new HashSet<>(emails);
            toAdd.removeAll(assignedStaffs);

            Set<String> toRemove = new HashSet<>(assignedStaffs);
            toRemove.removeAll(emails);

            if (!toAdd.isEmpty()) {
                List<StaffDto> staffToAdd = allEmailStaffMap.entrySet().stream().filter(entry ->
                                toAdd.contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .toList();

                staffToAdd.forEach(staff -> {
                    pendingRoleAssignments.put(staff.getId(), selectedRoleDto);
                });
            }

            if (!toRemove.isEmpty()) {
                List<StaffDto> staffToRemove = allEmailStaffMap.entrySet().stream().filter(entry ->
                                toRemove.contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .toList();

                staffToRemove.forEach(staff -> {
                    pendingRoleAssignments.putIfAbsent(staff.getId(), null);
                });
            }

        }
        List<StaffDto> finalStaffToUpdate = new ArrayList<>();

        for (Map.Entry<UUID, RoleDto> entry : pendingRoleAssignments.entrySet()) {
            UUID staffId = entry.getKey();
            RoleDto newRole = entry.getValue();

            StaffDto dto = allEmailStaffMap.values().stream()
                    .filter(s -> s.getId().equals(staffId))
                    .findFirst()
                    .orElse(null);

            if (dto != null) {
                boolean isRoleChanging = (dto.getRole() == null && newRole != null) ||
                        (dto.getRole() != null && newRole == null) ||
                        (dto.getRole() != null && newRole != null && !dto.getRole().getId().equals(newRole.getId()));

                if (isRoleChanging) {
                    dto.setRole(newRole);
                    dto.setUpdatedBy(userUUID);
                    dto.setUpdatedAt(now);
                    finalStaffToUpdate.add(dto);
                }
            }
        }

        if (!finalStaffToUpdate.isEmpty()) {
            staffService.updateAll(finalStaffToUpdate);
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
