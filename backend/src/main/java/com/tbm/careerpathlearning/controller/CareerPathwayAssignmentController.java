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
@RequestMapping("/api/career-pathway-assignment")
public class CareerPathwayAssignmentController {

    private static final Logger logger = LoggerFactory.getLogger(CareerPathwayAssignmentController.class);

    @Autowired
    private CareerPathwayService careerPathwayService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private StaffService staffService;

    @Autowired
    private CareerPathwayRoleService careerPathwayRoleService;

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE = "career.pathway.outside.err.msg";

    private static final String IMPORT_CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE = "import.career.pathway.outside.err.msg";

    private static final String CAREER_PATHWAY_ASSIGNMENT = "Assign Career Pathway";

    private static final String CAREER_PATHWAY_UPDATE = "Update Career Pathway Assignment";

    private static final String CAREER_PATHWAY_DELETE = "Remove Career Pathway Assignment";

    private static final String CAREER_PATHWAY_ASSIGNMENT_CREATION_OK = "career.pathway.assignment.creation.ok.msg";

    private static final String CAREER_PATHWAY_ASSIGNMENT_UPDATE_OK = "career.pathway.assignment.update.ok.msg";

    private static final String CAREER_PATHWAY_DELETE_OK = "career.pathway.assignment.delete.ok.msg";

    private static final String DEPT_NAME_NOTES = "career.pathway.assignment.department.name.notes";

    private static final String COMPETENCY_NAME_NOTES = "career.pathway.assignment.career.pathway.name.notes";

    private static final String STAFF_EMAIL_NOTES = "career.pathway.assignment.staff.email.notes";

    private static final String DEPT_NAME_COLUMN = "Department Name";

    private static final String CAREER_PATHWAY_NAME_COLUMN = "Career Pathway Name";

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
    @Autowired
    private OrgChartService orgChartService;

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
            )
            """)
    @GetMapping("/overview")
    @Transactional
    public ResponseEntity<?> overview(Authentication authentication) {

        List<CareerPathwayDto> existedCareerPathway = careerPathwayService.getAllByIsDeletedIsFalse();
        List<StaffDto> existedStaffs = staffService.findAllByIsDeletedIsFalse();

        List<CareerPathwayAssignmentOverviewDto> result = existedCareerPathway.stream().map(dto -> {
                    List<StaffDto> assignedStaffs = existedStaffs.stream().filter(staffDto ->
                                    staffDto.getCareerPathway() != null
                                            &&
                                            Objects.equals(staffDto.getCareerPathway().getId(), dto.getId()))
                            .peek(staffDto -> staffDto.setPassword(null))
                            .toList();

                    return new CareerPathwayAssignmentOverviewDto(
                            dto.getOrgChart().getId(),
                            dto.getOrgChart().getName(),
                            dto.getOrgChart().isDeleted(),
                            dto.getId(),
                            dto.getName(),
                            assignedStaffs
                    );
                }).
                toList();

        return ResponseEntity.ok(result);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
            )
            """)
    @PostMapping()
    @Transactional
    public ResponseEntity<?> assignCareerPathway(@RequestBody AssignCareerPathwayRequestDto req,
                                                 Authentication authentication) {
        if (req == null || req.getCareerPathwayId() == null || req.getStaffIds() == null || req.getStaffIds().isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{CAREER_PATHWAY_ASSIGNMENT}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        CareerPathwayDto selectedCareerPathway = careerPathwayService.getById(req.getCareerPathwayId());
        List<CareerPathwayRoleDto> relation = careerPathwayRoleService.getAllByCareerPathwayId(req.getCareerPathwayId());
        Set<RoleDto> assignedRoles = relation.stream().map(CareerPathwayRoleDto::getParentRole).collect(Collectors.toSet());
        assignedRoles.addAll(relation.stream().map(CareerPathwayRoleDto::getChildRole).collect(Collectors.toSet()));
        assignedRoles.add(selectedCareerPathway.getRootRole());

        Set<Long> assignedRoleIds = assignedRoles.stream().map(RoleDto::getId).collect(Collectors.toSet());

        List<StaffDto> selectedStaffs = staffService.findAllByIdIn(req.getStaffIds());
        Set<UUID> selectedStaffUUID = selectedStaffs.stream().map(StaffDto::getId).collect(Collectors.toSet());
        Set<Long> selectedRoleIds = selectedStaffs.stream().filter(dto -> dto.getRole() != null).
                map(dto -> dto.getRole().getId()).collect(Collectors.toSet());

        if (!assignedRoleIds.containsAll(selectedRoleIds)) {
            String errorMessage = messageSource.getMessage(CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        staffService.updateCareerPathwayByIdIn(selectedStaffUUID, selectedCareerPathway, userUUID, now);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(CAREER_PATHWAY_ASSIGNMENT_CREATION_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
            )
            """)
    @PutMapping("/edit")
    @Transactional
    public ResponseEntity<?> update(@RequestBody AssignCareerPathwayRequestDto req,
                                    Authentication authentication) {
        if (req == null || req.getCareerPathwayId() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{CAREER_PATHWAY_UPDATE}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        CareerPathwayDto selectedCareerPathway = careerPathwayService.getById(req.getCareerPathwayId());
        List<CareerPathwayRoleDto> relation = careerPathwayRoleService.getAllByCareerPathwayId(req.getCareerPathwayId());
        Set<RoleDto> assignedRoles = relation.stream().map(CareerPathwayRoleDto::getParentRole).collect(Collectors.toSet());
        assignedRoles.addAll(relation.stream().map(CareerPathwayRoleDto::getChildRole).collect(Collectors.toSet()));
        assignedRoles.add(selectedCareerPathway.getRootRole());

        Set<Long> assignedRoleIds = assignedRoles.stream().map(RoleDto::getId).collect(Collectors.toSet());

        List<StaffDto> selectedStaffs = staffService.findAllByIdIn(req.getStaffIds());
        Set<UUID> selectedStaffUUID = selectedStaffs.stream().map(StaffDto::getId).collect(Collectors.toSet());
        Set<Long> selectedRoleIds = selectedStaffs.stream().
                filter(dto -> dto.getRole() != null)
                .map(dto -> dto.getRole().getId()).collect(Collectors.toSet());

        Set<UUID> assignedStaffUUID = staffService.findAllByCareerPathwayId(req.getCareerPathwayId())
                .stream().map(StaffDto::getId).collect(Collectors.toSet());

        if (!assignedRoleIds.containsAll(selectedRoleIds)) {
            String errorMessage = messageSource.getMessage(CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        Set<UUID> toAdd = new HashSet<>(selectedStaffUUID);
        toAdd.removeAll(assignedStaffUUID);

        Set<UUID> toRemove = new HashSet<>(assignedStaffUUID);
        toRemove.removeAll(selectedStaffUUID);

        if (!toAdd.isEmpty()) {
            staffService.updateCareerPathwayByIdIn(toAdd, selectedCareerPathway, userUUID, now);
        }

        if (!toRemove.isEmpty()) {
            staffService.updateCareerPathwayByIdIn(toRemove, null, userUUID, now);
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(CAREER_PATHWAY_ASSIGNMENT_UPDATE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
            )
            """)
    @DeleteMapping("/delete")
    @Transactional
    public ResponseEntity<?> delete(@RequestParam Long selectedCareerPathwayId,
                                    Authentication authentication) {
        if (selectedCareerPathwayId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{CAREER_PATHWAY_DELETE}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<UUID> assignedStaffUUID = staffService.findAllByCareerPathwayId(selectedCareerPathwayId)
                .stream().map(StaffDto::getId).collect(Collectors.toSet());

        staffService.updateCareerPathwayByIdIn(assignedStaffUUID, null, userUUID, now);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(CAREER_PATHWAY_DELETE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
            )
            """)
    @DeleteMapping("/bulk-delete")
    @Transactional
    public ResponseEntity<?> bulkDelete(@RequestParam Set<Long> selectedCareerPathwayIds,
                                        Authentication authentication) {
        if (selectedCareerPathwayIds == null || selectedCareerPathwayIds.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{CAREER_PATHWAY_DELETE}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // -- basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<UUID> assignedStaffUUID = staffService.findAllByCareerPathwayIdIn(selectedCareerPathwayIds)
                .stream().map(StaffDto::getId).collect(Collectors.toSet());

        staffService.updateCareerPathwayByIdIn(assignedStaffUUID, null, userUUID, now);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(CAREER_PATHWAY_DELETE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
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
            cellRoleName.setCellValue(CAREER_PATHWAY_NAME_COLUMN);
            createCellComment(drawing, cellRoleName, messageSource.getMessage(COMPETENCY_NAME_NOTES, null, Locale.getDefault()));

            Cell cellDesc = header.createCell(2);
            cellDesc.setCellValue(STAFF_EMAIL_COLUMN);
            createCellComment(drawing, cellDesc, messageSource.getMessage(STAFF_EMAIL_NOTES, null, Locale.getDefault()));

            rowIndex++;

            List<CareerPathwayDto> allCareerPathway = careerPathwayService.getAllByIsDeletedIsFalse();
            List<StaffDto> allStaff = staffService.findAllByIsDeletedIsFalse();

            for (CareerPathwayDto dto : allCareerPathway) {
                String assignedStaff = allStaff.stream().filter(staff ->
                                staff.getCareerPathway() != null && Objects.equals(staff.getCareerPathway().getId(), dto.getId()))
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
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Career_Pathway_Assignment_Data.xlsx");
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
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
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
        List<CareerPathwayDto> allCareerPathway = careerPathwayService.getAllByIsDeletedIsFalse();
        Map<String, Set<String>> alldepartmentCareerPathwayNameMap = allDepartment.stream().collect(Collectors.toMap(
                dto -> dto.getName().trim().toLowerCase(),
                dto -> allCareerPathway.stream().filter(careerPathway ->
                                Objects.equals(careerPathway.getOrgChart().getId(), dto.getId()))
                        .map(careerPathway -> careerPathway.getName().toLowerCase().trim())
                        .collect(Collectors.toSet())));

        Map<String, StaffDto> allEmailStaffMap = staffService.findAllByIsDeletedIsFalse().stream()
                .collect(Collectors.toMap(
                        StaffDto::getEmail,
                        Function.identity()
                ));

        List<CareerPathwayRoleDto> allRelation = careerPathwayRoleService.getAll();

        Map<Long, Set<String>> allCareerPathwayStaffMap = new HashMap<>();

        allEmailStaffMap.values().forEach(staffMap -> {
            if (staffMap.getCareerPathway() != null) {
                allCareerPathwayStaffMap.computeIfAbsent(staffMap.getCareerPathway().getId(), k -> new HashSet<>())
                        .add(staffMap.getEmail().toLowerCase().trim());
            }
        });

        Sheet sheet = workbook.getSheetAt(0);

        // -- check headers --
        Row header = sheet.getRow(0);
        if (!getCellValueAsString(header.getCell(0)).equalsIgnoreCase(DEPT_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(1)).equalsIgnoreCase(CAREER_PATHWAY_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(2)).equalsIgnoreCase(STAFF_EMAIL_COLUMN)) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        Map<UUID, CareerPathwayDto> pendingRoleAssignments = new HashMap<>();
        Map<String, Set<String>> duplicatedRow = new HashMap<>();
        Set<String> emailSeen = new HashSet<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header (row 0)
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(0) == null || getCellValueAsString(row.getCell(0)) == null
                    || getCellValueAsString(row.getCell(0)).isBlank()) {
                continue;
            }

            // -- data --
            String departmentName = getCellValueAsString(row.getCell(0));
            String careerPathwayName = getCellValueAsString(row.getCell(1));
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

            if (validationService.isNullOrBlank(careerPathwayName)) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{CAREER_PATHWAY_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (!alldepartmentCareerPathwayNameMap.containsKey(departmentName.trim().toLowerCase())) {
                String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{departmentName}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (!alldepartmentCareerPathwayNameMap.get(departmentName.trim().toLowerCase()).contains(careerPathwayName.trim().toLowerCase())) {
                String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{careerPathwayName}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (duplicatedRow.get(departmentName.toLowerCase().trim()) != null
                    && duplicatedRow.get(departmentName.toLowerCase().trim()).contains(careerPathwayName.toLowerCase().trim())) {
                String errorMessage = messageSource.getMessage(IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE, new String[]{Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            duplicatedRow
                    .computeIfAbsent(departmentName.toLowerCase().trim(), k -> new HashSet<>())
                    .add(careerPathwayName.toLowerCase().trim());


            CareerPathwayDto selectedCareerPathwayDto = Objects.requireNonNull(
                    allCareerPathway.stream().filter(dto ->
                                    careerPathwayName.trim().equalsIgnoreCase(dto.getName().trim())
                                            && departmentName.trim().equalsIgnoreCase(dto.getOrgChart().getName().trim()))
                            .findFirst().orElse(null));

            List<CareerPathwayRoleDto> assignedRole = allRelation.stream().filter(relation ->
                            Objects.equals(relation.getId().getCareerPathwayId(), selectedCareerPathwayDto.getId()))
                    .toList();

            Set<Long> assignedRoleIds = new HashSet<>();
            assignedRoleIds.add(selectedCareerPathwayDto.getRootRole().getId());
            assignedRoleIds.addAll(assignedRole.stream().map(role -> role.getId().getParentId()).collect(Collectors.toSet()));
            assignedRoleIds.addAll(assignedRole.stream().map(role -> role.getId().getChildId()).collect(Collectors.toSet()));

            int finalI = i;

            Set<String> assignedStaffs = allCareerPathwayStaffMap.get(selectedCareerPathwayDto.getId()) == null ?
                    new HashSet<>() : allCareerPathwayStaffMap.get(selectedCareerPathwayDto.getId());

            Set<String> toAdd = new HashSet<>(emails);
            toAdd.removeAll(assignedStaffs);

            Set<String> toRemove = new HashSet<>(assignedStaffs);
            toRemove.removeAll(emails);

            if (!toAdd.isEmpty()) {
                List<StaffDto> staffToAdd = allEmailStaffMap.entrySet().stream().filter(entry ->
                                toAdd.contains(entry.getKey()))
                        .map(entry -> {
                            if (entry.getValue().getRole() == null || !assignedRoleIds.contains(entry.getValue().getRole().getId())) {
                                String errorMessage = messageSource.getMessage(IMPORT_CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE,
                                        new String[]{Integer.toString(finalI + 1)}, Locale.getDefault());
                                throw new BadRequestException(errorMessage);
                            }
                            return entry.getValue();
                        })
                        .toList();

                staffToAdd.forEach(staff -> {
                    pendingRoleAssignments.put(staff.getId(), selectedCareerPathwayDto);
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

        for (Map.Entry<UUID, CareerPathwayDto> entry : pendingRoleAssignments.entrySet()) {
            UUID staffId = entry.getKey();
            CareerPathwayDto newCareerPathway = entry.getValue();

            StaffDto dto = allEmailStaffMap.values().stream()
                    .filter(s -> s.getId().equals(staffId))
                    .findFirst()
                    .orElse(null);

            if (dto != null) {
                boolean isRoleChanging = (dto.getCareerPathway() == null && newCareerPathway != null) ||
                        (dto.getCareerPathway() != null && newCareerPathway == null) ||
                        (dto.getCareerPathway() != null && newCareerPathway != null && !dto.getCareerPathway().getId().equals(newCareerPathway.getId()));

                if (isRoleChanging) {
                    dto.setCareerPathway(newCareerPathway);
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