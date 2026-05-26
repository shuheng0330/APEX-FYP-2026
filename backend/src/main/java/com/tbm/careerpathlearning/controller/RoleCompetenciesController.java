package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.RoleCompetencyId;
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
@RequestMapping("/api/role-competency")
public class RoleCompetenciesController {

    @Autowired
    private CompetencyService competencyService;

    @Autowired
    private RoleCompetencyService roleCompetencyService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleJobScopeService roleJobScopeService;

    @Autowired
    private StaffService staffService;

    @Autowired
    private CompetencyCompTagService competencyCompTagService;

    @Autowired
    private OrgChartService orgChartService;

    @Autowired
    private ValidationService validationService;

    private static final Logger logger = LoggerFactory.getLogger(RoleCompetenciesController.class);

    private static final Integer WEIGHTAGE_MAX = 100;

    private static final Integer WEIGHTAGE_MIN = 1;

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String COMPETENCY_ASSIGNMENT_OPERATION = "Competency Assignment";

    private static final String COMPETENCY_ASSIGNMENT_DELETE_OPERATION = "Competency Assignment Deletion";

    private static final String COMPETENCY_ASSIGNMENT_CREATION_OK = "competency.assignment.creation.ok.msg";

    private static final String COMPETENCY_ASSIGNMENT_DELETE_OK = "competency.assignment.delete.ok.msg";

    private static final String VIEW_ROLE_DETAILS = "Viewing role details";

    private static final String DEPT_NAME_NOTES = "competency.assignment.department.name.notes";

    private static final String ROLE_NAME_NOTES = "competency.assignment.role.name.notes";

    private static final String COMPETENCY_NAME_NOTES = "competency.assignment.competency.weightage.notes";

    private static final String DEPT_NAME_COLUMN = "Department Name";

    private static final String ROLE_NAME_COLUMN = "Role Name";

    private static final String COMPETENCY_WEIGHTAGE_COLUMN = "Competency Name > Weightage";

    private static final String WEIGHTAGE_COLUMN = "Weightage";

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

    @GetMapping
    public ResponseEntity<List<RoleCompetencyDto>> getAllRoleCompetencies() {
        return ResponseEntity.ok(roleCompetencyService.findAll());
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @GetMapping("/overview")
    public ResponseEntity<?> getCompetencyAssignmentOverview(Authentication authentication) {
        List<CompetencyAssignmentOverviewDto> competencyAssignmentOverviewDtoList = new ArrayList<>();

        List<RoleDto> existingRoleDtoList = roleService.getAllByDeletedIsFalse();
        List<RoleCompetencyDto> existingRoleCompetencyDtoList = roleCompetencyService.findAll();
        List<CompetencyCompTagDto> allCompTags = competencyCompTagService.findAll();

        existingRoleDtoList.forEach((roleDto) -> {
            Map<Long, CompetencyAssignmentDto> assignedCompetency = existingRoleCompetencyDtoList.stream()
                    .filter(roleCompetencyDto ->
                            roleCompetencyDto.getId().getRoleId().equals(roleDto.getId())
                    ).collect(Collectors.toMap(
                            roleCompetencyDto -> roleCompetencyDto.getId().getCompetencyId(),
                            roleCompetencyDto -> new CompetencyAssignmentDto(
                                    roleCompetencyDto.getId().getCompetencyId(),
                                    roleCompetencyDto.getCompetency().getName(),
                                    roleCompetencyDto.getCompetency().getDescription(),
                                    roleCompetencyDto.getCompetency().isDeleted(),
                                    roleCompetencyDto.getWeightage()
                            )
                    ));

            int totalWeightage = assignedCompetency.values().stream()
                    .mapToInt(CompetencyAssignmentDto::getWeightage)
                    .sum();

            List<CompetencyCompTagDto> assignedCompetencyCompTagDto = allCompTags.stream()
                    .filter(compTag ->
                            assignedCompetency.containsKey(compTag.getId().getCompetencyId()))
                    .toList();

            Map<Long, List<String>> assignedCompTags = assignedCompetency.keySet().stream()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            id -> assignedCompetencyCompTagDto.stream()
                                    .filter(compTag -> compTag.getId().getCompetencyId().equals(id))
                                    .map(compTag -> compTag.getCompTag().getTag()).toList()
                    ));

            competencyAssignmentOverviewDtoList.add(new CompetencyAssignmentOverviewDto(
                    roleDto.getOrgChart().getId(),
                    roleDto.getOrgChart().getName(),
                    roleDto.getOrgChart().isDeleted(),
                    roleDto.getId(),
                    roleDto.getName(),
                    totalWeightage,
                    assignedCompetency.values().stream().toList(),
                    assignedCompTags
            ));
        });

        return ResponseEntity.ok(competencyAssignmentOverviewDtoList);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @GetMapping("/role-details")
    public ResponseEntity<?> getRoleDetails(@RequestParam Long roleId, Authentication authentication) {
        if (roleId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{VIEW_ROLE_DETAILS}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        RoleDto selectedRoleDto = roleService.getAllById(roleId);

        List<JobScopeDto> jobScopeDtoList = roleJobScopeService.findAllByRoleId(roleId).stream().map(RoleJobScopeDto::getJobScope).toList();

        Map<Long, RoleCompetencyDto> roleCompetencyDto = roleCompetencyService.findAllByRoleId(roleId).stream()
                .collect(Collectors.toMap(
                        dto -> dto.getId().getCompetencyId(),
                        Function.identity()
                ));

        int totalWeightage = roleCompetencyDto.values().stream().mapToInt(RoleCompetencyDto::getWeightage).sum();

        List<StaffDto> staffDto = staffService.findAllByRoleId(roleId).stream().peek(dto -> dto.setPassword(null))
                .toList();

        List<CompetencyCompTagDto> competencyCompTags = competencyCompTagService
                .findAllByCompetencyIdIn(roleCompetencyDto.keySet());

        Map<Long, List<String>> assignedCompTags = roleCompetencyDto.keySet().stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> competencyCompTags.stream()
                                .filter(compTag -> compTag.getId().getCompetencyId().equals(id))
                                .map(compTag -> compTag.getCompTag().getTag())
                                .toList()
                ));

        RoleDetailsDto roleDetailsDto = new RoleDetailsDto(
                selectedRoleDto.getOrgChart().getId(),
                selectedRoleDto.getOrgChart().getName(),
                selectedRoleDto.getId(),
                selectedRoleDto.getName(),
                selectedRoleDto.getDescription(),
                selectedRoleDto.isVisible(),
                jobScopeDtoList,
                roleCompetencyDto.values().stream().toList(),
                staffDto,
                totalWeightage,
                assignedCompTags
        );

        return ResponseEntity.ok(roleDetailsDto);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @GetMapping("/role-details-overview")
    public ResponseEntity<?> getRoleDetailsOverview(Authentication authentication) {

        List<RoleDto> allRoles = roleService.getAllByDeletedIsFalse();
        List<RoleJobScopeDto> allJobScopes = roleJobScopeService.findAll();
        List<RoleCompetencyDto> allCompetencies = roleCompetencyService.findAll();
        List<StaffDto> allStaffs = staffService.findAllByIsDeletedIsFalse();
        List<CompetencyCompTagDto> allCompTags = competencyCompTagService.findAll();

        List<RoleDetailsDto> result = allRoles.stream().map(dto -> {

                    List<RoleJobScopeDto> assignedRoleJobScopes = allJobScopes.stream()
                            .filter(jobScope ->
                                    Objects.equals(jobScope.getId().getRoleId(), dto.getId()))
                            .toList();
                    List<JobScopeDto> assignedJobScopes = assignedRoleJobScopes.stream().map(RoleJobScopeDto::getJobScope).collect(Collectors.toList());

                    Map<Long, RoleCompetencyDto> assignedCompetency = allCompetencies.stream()
                            .filter(competency ->
                                    Objects.equals(competency.getId().getRoleId(), dto.getId()))
                            .collect(Collectors.toMap(
                                    competency -> competency.getId().getCompetencyId(),
                                    Function.identity()
                            ));

                    List<StaffDto> assignedStaff = allStaffs.stream()
                            .filter(staff -> staff.getRole() != null &&
                                    Objects.equals(staff.getRole().getId(), dto.getId()))
                            .toList();

                    int totalWeightage = assignedCompetency.values().stream().mapToInt(RoleCompetencyDto::getWeightage).sum();

                    List<CompetencyCompTagDto> assignedCompetencyCompTagDto = allCompTags.stream()
                            .filter(compTag ->
                                    assignedCompetency.containsKey(compTag.getId().getCompetencyId()))
                            .toList();

                    Map<Long, List<String>> assignedCompTags = assignedCompetency.keySet().stream()
                            .collect(Collectors.toMap(
                                    Function.identity(),
                                    id -> assignedCompetencyCompTagDto.stream()
                                            .filter(compTag -> compTag.getId().getCompetencyId().equals(id))
                                            .map(compTag -> compTag.getCompTag().getTag()).toList()
                            ));

                    return new RoleDetailsDto(
                            dto.getOrgChart().getId(),
                            dto.getOrgChart().getName(),
                            dto.getId(),
                            dto.getName(),
                            dto.getDescription(),
                            dto.isVisible(),
                            assignedJobScopes,
                            assignedCompetency.values().stream().toList(),
                            assignedStaff,
                            totalWeightage,
                            assignedCompTags
                    );
                })
                .toList();

        return ResponseEntity.ok(result);
    }


    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @PostMapping
    @Transactional
    public ResponseEntity<?> createRoleCompetency(@RequestBody CreateRoleCompetencyRequestDto requestDto, Authentication authentication) {
        if (requestDto == null || requestDto.getRoleId() == null || requestDto.getCompetencyAssignment().isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_ASSIGNMENT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        for (CompetencyAssignmentDto assignment: requestDto.getCompetencyAssignment()){
            if(assignment.getWeightage() < WEIGHTAGE_MIN ||  assignment.getWeightage() > WEIGHTAGE_MAX){
                String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_ASSIGNMENT_OPERATION}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        RoleDto roleDto = roleService.getAllById(requestDto.getRoleId());

        List<RoleCompetencyDto> existingRoleCompetencyDtoList = roleCompetencyService.findAllByRoleId(requestDto.getRoleId());
        Set<Long> existingAssignedCompetencyIds = existingRoleCompetencyDtoList.stream()
                .map(roleCompetencyDto -> roleCompetencyDto.getId().getCompetencyId()).collect(Collectors.toSet());

        Map<Long, Integer> selectedCompetencyList = requestDto.getCompetencyAssignment().stream()
                .collect(Collectors.toMap(
                        CompetencyAssignmentDto::getCompetencyId,
                        CompetencyAssignmentDto::getWeightage,
                        (w1, w2) -> w2
                ));

        Set<Long> selectedCompetencyIds = selectedCompetencyList.keySet();

        Set<Long> toRemove = new HashSet<>(existingAssignedCompetencyIds);
        toRemove.removeAll(selectedCompetencyIds);

        if (!toRemove.isEmpty()) {
            Set<RoleCompetencyId> idToRemove = toRemove.stream()
                    .map(competencyId -> new RoleCompetencyId(requestDto.getRoleId(), competencyId))
                    .collect(Collectors.toSet());

            roleCompetencyService.deleteAllByIdIn(idToRemove);
        }

        Set<Long> toAdd = new HashSet<>(selectedCompetencyIds);
        toAdd.removeAll(existingAssignedCompetencyIds);

        OffsetDateTime now = OffsetDateTime.now();

        if (!toAdd.isEmpty()) {
            List<CompetencyDto> competencyDtoToAdd = competencyService.findAllByIsDeletedIsFalseAndIdIn(toAdd);

            List<RoleCompetencyDto> roleCompetencyDtoToAdd = competencyDtoToAdd.stream().map(competencyDto -> {
                RoleCompetencyDto roleCompetencyDto = new RoleCompetencyDto();

                roleCompetencyDto.setId(new RoleCompetencyId(requestDto.getRoleId(), competencyDto.getId()));
                roleCompetencyDto.setRole(roleDto);
                roleCompetencyDto.setCompetency(competencyDto);
                roleCompetencyDto.setWeightage(selectedCompetencyList.get(competencyDto.getId()));

                roleCompetencyDto.setCreatedBy(userUUID);
                roleCompetencyDto.setUpdatedBy(userUUID);
                roleCompetencyDto.setCreatedAt(now);
                roleCompetencyDto.setUpdatedAt(now);

                return roleCompetencyDto;
            }).toList();

            roleCompetencyService.createAll(roleCompetencyDtoToAdd);
        }

        Set<Long> toCheckWeightage = new HashSet<>(existingAssignedCompetencyIds);
        toCheckWeightage.retainAll(selectedCompetencyIds);

        if (!toCheckWeightage.isEmpty()) {

            Map<Long, RoleCompetencyDto> roleCompetencyDtoToCheckMap = existingRoleCompetencyDtoList.stream().filter(roleCompetencyDto ->
                            toCheckWeightage.contains(roleCompetencyDto.getId().getCompetencyId()))
                    .collect(Collectors.toMap(
                            roleCompetencyDto -> roleCompetencyDto.getId().getCompetencyId(),
                            roleCompetencyDto -> roleCompetencyDto
                    ));


            toCheckWeightage.removeIf(competencyId -> Objects.equals(
                    selectedCompetencyList.get(competencyId),
                    roleCompetencyDtoToCheckMap.get(competencyId).getWeightage()
            ));

            List<RoleCompetencyDto> roleCompetencyDtosToBeUpdated =
                    toCheckWeightage.stream().map(competencyId -> {
                        RoleCompetencyDto roleCompetencyDto = roleCompetencyDtoToCheckMap.get(competencyId);

                        roleCompetencyDto.setWeightage(selectedCompetencyList.get(competencyId));
                        roleCompetencyDto.setUpdatedBy(userUUID);
                        roleCompetencyDto.setUpdatedAt(now);

                        return roleCompetencyDto;
                    }).toList();

            Set<RoleCompetencyId> roleCompetencyIdsToBeUpdated = roleCompetencyDtosToBeUpdated.stream()
                    .map(RoleCompetencyDto::getId).collect(Collectors.toSet());

            if (!roleCompetencyIdsToBeUpdated.isEmpty()) {
                roleCompetencyService.updateAll(roleCompetencyIdsToBeUpdated, roleCompetencyDtosToBeUpdated);
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_ASSIGNMENT_CREATION_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @DeleteMapping("/delete")
    @Transactional
    public ResponseEntity<?> deleteCompetency(
            @RequestParam Long roleId, Authentication authentication) {
        if (roleId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_ASSIGNMENT_DELETE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        roleCompetencyService.deleteAllByRoleId(roleId);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_ASSIGNMENT_DELETE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @DeleteMapping("/bulk-delete")
    @Transactional
    public ResponseEntity<?> bulkDeleteCompetency(
            @RequestParam List<Long> roleIds, Authentication authentication) {
        if (roleIds == null || roleIds.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{COMPETENCY_ASSIGNMENT_DELETE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        Set<Long> toDelete = new HashSet<>(roleIds);
        roleCompetencyService.deleteAllByRoleIdIn(toDelete);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(COMPETENCY_ASSIGNMENT_DELETE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName()
            )
            """)
    @GetMapping("/export")
    public ResponseEntity<?> exportCompetencyAssignmentData(Authentication authentication) {
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

            Cell cellComp = header.createCell(2);
            cellComp.setCellValue(COMPETENCY_WEIGHTAGE_COLUMN);
            createCellComment(drawing, cellComp, messageSource.getMessage(COMPETENCY_NAME_NOTES, null, Locale.getDefault()));

            rowIndex++;

            List<RoleDto> allRoles = roleService.getAllByDeletedIsFalse();
            List<RoleCompetencyDto> allAssignedCompetency = roleCompetencyService.findAll();

            for (RoleDto dto : allRoles) {
                String assignedCompetency = allAssignedCompetency.stream()
                        .filter(c -> Objects.equals(c.getRole().getId(), dto.getId()))
                        .map(c -> String.format("%s > %d", c.getCompetency().getName(), c.getWeightage()))
                        .collect(Collectors.joining("; "));

                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(dto.getOrgChart().getName());
                row.createCell(1).setCellValue(dto.getName());
                row.createCell(2).setCellValue(assignedCompetency);

                rowIndex++;
            }

            workbook.write(out);
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray());


            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Competency_Assignment_Data.xlsx");
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
        List<RoleDto> allRoles = roleService.getAllByDeletedIsFalse();
        Map<String, Set<String>> alldepartmentRoleNameMap = allDepartment.stream().collect(Collectors.toMap(
                dto -> dto.getName().trim().toLowerCase(),
                dto -> allRoles.stream().filter(role ->
                                Objects.equals(role.getOrgChart().getId(), dto.getId()))
                        .map(role -> role.getName().toLowerCase().trim())
                        .collect(Collectors.toSet())));

        List<CompetencyDto> allCompetency = competencyService.findAllByIsDeletedIsFalse();
        Map<String, CompetencyDto> allCompetencyNameMap = allCompetency.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getName().trim().toLowerCase(),
                        Function.identity()
                ));
        Map<Long, CompetencyDto> allCompetencyIdMap = allCompetency.stream()
                .collect(Collectors.toMap(
                        CompetencyDto::getId,
                        Function.identity()
                ));

        List<RoleCompetencyDto> allAssignedCompetency = roleCompetencyService.findAll();
        Map<RoleCompetencyId, RoleCompetencyDto> allAssignedCompetencyMap = allAssignedCompetency.stream()
                .collect(Collectors.toMap(
                        RoleCompetencyDto::getId,
                        Function.identity()
                ));

        Sheet sheet = workbook.getSheetAt(0);

        // -- check headers --
        Row header = sheet.getRow(0);
        if (!getCellValueAsString(header.getCell(0)).equalsIgnoreCase(DEPT_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(1)).equalsIgnoreCase(ROLE_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(2)).equalsIgnoreCase(COMPETENCY_WEIGHTAGE_COLUMN)) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        List<RoleCompetencyDto> roleCompetencyToBeCreated = new ArrayList<>();
        Set<RoleCompetencyId> roleCompetencyIdsToRemove = new HashSet<>();

        Map<String, Set<String>> duplicatedRow = new HashMap<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header (row 0)
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(0) == null || getCellValueAsString(row.getCell(0)) == null
                    || getCellValueAsString(row.getCell(0)).isBlank()) continue;

            // -- data --
            String departmentName = getCellValueAsString(row.getCell(0));
            String roleName = getCellValueAsString(row.getCell(1));
            String competencies = getCellValueAsString(row.getCell(2));

            Map<Long, Integer> competencyWeightageMap;
            if (!validationService.isNullOrBlank(competencies)) {
                int finalI = i;
                competencyWeightageMap = Arrays.stream(competencies.split(";"))
                        .map(String::trim)
                        .filter(competencyWeightage -> !validationService.isNullOrBlank(competencyWeightage))
                        .peek(competencyWeightage -> {
                            String[] parts = competencyWeightage.split(">");
                            if (parts.length != 2) {
                                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                                        new String[]{COMPETENCY_WEIGHTAGE_COLUMN, Integer.toString(finalI + 1)}, Locale.getDefault());
                                throw new BadRequestException(errorMessage);
                            }

                            String competencyName = parts[0].trim();
                            String weightage = parts[1].trim();

                            if (!allCompetencyNameMap.containsKey(competencyName.toLowerCase())) {
                                String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{competencyName}, Locale.getDefault());
                                throw new BadRequestException(errorMessage);
                            }

                            if (!weightage.matches("^\\d{1,3}$")) {
                                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                                        new String[]{WEIGHTAGE_COLUMN, Integer.toString(finalI + 1)}, Locale.getDefault());
                                throw new BadRequestException(errorMessage);
                            }

                            int weightValue = Integer.parseInt(weightage);
                            if (weightValue < WEIGHTAGE_MIN || weightValue > WEIGHTAGE_MAX) {
                                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                                        new String[]{WEIGHTAGE_COLUMN, Integer.toString(finalI + 1)}, Locale.getDefault());
                                throw new BadRequestException(errorMessage);
                            }
                        })
                        .collect(Collectors.toMap(
                                cw -> Objects.requireNonNull(allCompetencyNameMap.get(cw.split(">")[0].toLowerCase().trim())).getId(),
                                cw -> Integer.parseInt(cw.split(">")[1].trim())
                        ));
            } else {
                competencyWeightageMap = Collections.emptyMap();
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

            duplicatedRow.computeIfAbsent(departmentName.toLowerCase().trim(), k -> new HashSet<>())
                    .add(roleName.toLowerCase().trim());

            RoleDto selectedRoleDto = Objects.requireNonNull(
                    allRoles.stream().filter(dto ->
                                    roleName.trim().equalsIgnoreCase(dto.getName().trim()))
                            .findFirst().orElse(null));

            if (!competencyWeightageMap.isEmpty()) {

                Map<Long, Integer> assignedWeightageMap = allAssignedCompetency.stream().filter(dto ->
                                Objects.equals(dto.getId().getRoleId(), selectedRoleDto.getId()))
                        .collect(Collectors.toMap(
                                dto -> dto.getId().getCompetencyId(),
                                RoleCompetencyDto::getWeightage
                        ));

                Set<Long> selectedCompetencyId = competencyWeightageMap.keySet();

                Set<Long> toAdd = new HashSet<>(selectedCompetencyId);
                toAdd.removeAll(assignedWeightageMap.keySet());

                Set<Long> toRemove = new HashSet<>(assignedWeightageMap.keySet());
                toRemove.removeAll(selectedCompetencyId);

                Set<Long> toCheck = new HashSet<>(selectedCompetencyId);
                toCheck.retainAll(assignedWeightageMap.keySet());

                if (!toRemove.isEmpty()) {
                    roleCompetencyIdsToRemove.addAll(
                            toRemove.stream().map(competencyId -> new RoleCompetencyId(selectedRoleDto.getId(), competencyId))
                                    .collect(Collectors.toSet())
                    );
                }

                if (!toCheck.isEmpty()) {
                    List<RoleCompetencyDto> toUpdate = toCheck.stream()
                            .map(competencyId -> {
                                RoleCompetencyDto assignedCompetency = allAssignedCompetencyMap.get(
                                        new RoleCompetencyId(selectedRoleDto.getId(), competencyId));

                                Integer newWeightage = competencyWeightageMap.get(competencyId);
                                Integer oldWeightage = assignedCompetency.getWeightage();

                                if (!Objects.equals(oldWeightage, newWeightage)) {
                                    assignedCompetency.setWeightage(newWeightage);
                                    assignedCompetency.setUpdatedAt(now);
                                    assignedCompetency.setCreatedBy(userUUID);
                                    return assignedCompetency;
                                }
                                return null;
                            })
                            .filter(Objects::nonNull)
                            .toList();

                    roleCompetencyToBeCreated.addAll(toUpdate);
                }

                if (!toAdd.isEmpty()) {
                    List<RoleCompetencyDto> toCreate = toAdd.stream().map(competencyId ->
                                    new RoleCompetencyDto(
                                            new RoleCompetencyId(selectedRoleDto.getId(), competencyId),
                                            selectedRoleDto,
                                            allCompetencyIdMap.get(competencyId),
                                            competencyWeightageMap.get(competencyId),
                                            userUUID,
                                            now,
                                            userUUID,
                                            now
                                    ))
                            .toList();

                    roleCompetencyToBeCreated.addAll(toCreate);
                }
            }
        }

        Set<RoleCompetencyId> roleCompetencyIdToBeCreated = roleCompetencyToBeCreated.stream()
                .map(RoleCompetencyDto::getId).collect(Collectors.toSet());

        if (!roleCompetencyIdToBeCreated.isEmpty()) {
            roleCompetencyService.updateAll(roleCompetencyIdToBeCreated, roleCompetencyToBeCreated);
        }

        if (!roleCompetencyIdsToRemove.isEmpty()) {
            roleCompetencyService.deleteAllByIdIn(roleCompetencyIdsToRemove);
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
