package com.tbm.careerpathlearning.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tbm.careerpathlearning.dto.AuthorityDto;
import com.tbm.careerpathlearning.dto.OrgChartDepartmentNodeDto;
import com.tbm.careerpathlearning.dto.OrgChartDto;
import com.tbm.careerpathlearning.dto.OrgChartGraphDto;
import com.tbm.careerpathlearning.dto.OrgChartGraphNodeDto;
import com.tbm.careerpathlearning.dto.OrgChartPersonNodeDto;
import com.tbm.careerpathlearning.dto.ParentChildNodeDto;
import com.tbm.careerpathlearning.dto.RoleAuthorityDto;
import com.tbm.careerpathlearning.dto.RoleDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.StaffProfileDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.enums.RelationType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.RoleAuthorityId;
import com.tbm.careerpathlearning.service.AuthorityService;
import com.tbm.careerpathlearning.service.OrgChartService;
import com.tbm.careerpathlearning.service.ParentChildNodeService;
import com.tbm.careerpathlearning.service.RoleAuthorityService;
import com.tbm.careerpathlearning.service.RoleCompetencyService;
import com.tbm.careerpathlearning.service.RoleService;
import com.tbm.careerpathlearning.service.StaffProfileService;
import com.tbm.careerpathlearning.service.StaffService;
import com.tbm.careerpathlearning.service.ValidationService;

@RestController
@RequestMapping("/api/orgChart")
public class OrgChartController {

    @Autowired
    private OrgChartService orgChartService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private ParentChildNodeService parentChildNodeService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private StaffService staffService;

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private RoleAuthorityService roleAuthorityService;

    @Autowired
    private RoleCompetencyService roleCompetencyService;

    @Autowired
    private StaffProfileService staffProfileService;

    private static final Logger logger = LoggerFactory.getLogger(OrgChartController.class);

    private static final String TYPE_NOTES = "orgchart.type.notes";

    private static final String NAME_NOTES = "orgchart.name.notes";

    private static final String PARENT_NAME_NOTES = "orgchart.parent.name.notes";

    private static final String RENAME_NOTES = "orgchart.rename.notes";

    private static final String DELETED_NOTES = "orgchart.deleted.notes";

    private static final String INTERNAL_SERVER_ERROR_EXP_ERR_TITLE_CODE = "internal.server.err.title";

    private static final String EXPORT_ERR_MSG_CODE = "export.data.err.msg";

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String IMPORT_OPERATION = "Import Organizational Chart Data";

    private static final String IMPORT_INVALID_DATA_ERR_MSG_CODE = "import.invalid.data.err.msg";

    private static final String NAME_COLUMN = "Node Name";

    private static final String TYPE_COLUMN = "Node Type";

    private static final String IMPORT_UNIQUE_NAME_ERR_MSG_CODE = "import.unique.name.err.msg";

    private static final String IMPORT_PARENT_NAME_CONFLICT_ERR_MSG_CODE = "import.parent.name.conflict.err.msg";

    private static final String IMPORT_RECURSIVE_REFERENCE_ERR_MSG_CODE = "import.recursive.reference.err.msg";

    private static final String IMPORT_ORG_CHART_TYPE_CHANGE_ERR_MSG_CODE = "import.org.chart.type.change.err.msg";

    private static final String IMPORT_ACTION_CONFLICT_ERR_MSG_CODE = "import.action.conflict.err.msg";

    private static final String IMPORT_INVALID_HIERARCHY_ERR_MSG_CODE = "import.invalid.hierarchy.err.msg";

    private static final String IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE = "import.file.too.large.err.msg";

    private static final String IMPORT_FILE_INVALID_ERR_MSG_CODE = "import.file.invalid.err.msg";

    private static final String IMPORT_CHILD_NODE_NULL_ERR_MSG_CODE = "import.org.chart.child.null.err.msg";

    private static final String IMPORT_PARENT_NODE_NULL_ERR_MSG_CODE = "import.org.chart.parent.null.err.msg";

    private static final String IMPORT_OK = "data.import.ok.msg";

    private static final String YES = "Yes";

    private static final int MAX_FILE_SIZE_IN_MB = 5;

    private static final String ACCEPTED_IMPORT_FILE_TYPE = ".xlsx";

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ACCESS_CONTROL.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ORG_CHART.getAuthorityName()
            )
            """)
    @GetMapping
    public ResponseEntity<?> getAllOrgChart(Authentication authentication) {
        List<OrgChartDto> orgChartDtoList = this.orgChartService.findAllByIsDeletedIsFalse();
        return ResponseEntity.ok(orgChartDtoList);
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
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @GetMapping("/show-org-chart")
    public ResponseEntity<?> showOrgChart(Authentication authentication) {

        List<OrgChartDto> orgChartDtoList = orgChartService.findAllByIsDeletedIsFalse();
        List<ParentChildNodeDto> relations = parentChildNodeService.findAllByRelationType(RelationType.ORG_CHART);
        List<RoleDto> roleDtoList = roleService.getAllByDeletedIsFalse();
        List<StaffDto> staffDtoList = staffService.findAllByIsDeletedIsFalse();

        Map<Long, List<Long>> childrenMap = new HashMap<>(relations.size());
        relations.forEach(rel ->
                childrenMap.computeIfAbsent(rel.getParentId(), k -> new ArrayList<>()).add(rel.getChildId())
        );

        Map<Long, OrgChartDepartmentNodeDto> departmentNodeMap = orgChartDtoList.stream()
                .filter(dto -> Objects.equals(dto.getType(), OrgChartType.D))
                .collect(Collectors.toMap(
                        OrgChartDto::getId,
                        dto -> {
                            Map<Long, RoleDto> assignedRoleMap = roleDtoList.stream()
                                    .filter(role -> Objects.equals(role.getOrgChart().getId(), dto.getId()))
                                    .collect(Collectors.toMap(RoleDto::getId, Function.identity()));

                            Map<Long, List<StaffDto>> assignedStaffMap = assignedRoleMap.keySet().stream().collect(Collectors.toMap(
                                    Function.identity(),
                                    key -> staffDtoList.stream()
                                            .filter(staff -> staff.getRole() != null && Objects.equals(staff.getRole().getId(), key))
                                            .collect(Collectors.toList())
                            ));

                            Map<Long, Integer> roleSatffMap = assignedRoleMap.keySet().stream().collect(Collectors.toMap(
                                    Function.identity(),
                                    key -> assignedStaffMap.get(key).size()
                            ));

                            return new OrgChartDepartmentNodeDto(
                                    dto.getId(),
                                    dto.getName(),
                                    assignedRoleMap,
                                    assignedStaffMap,
                                    roleSatffMap
                            );
                        }));

        Map<Long, OrgChartPersonNodeDto> personNodeMap = orgChartDtoList.stream()
                .filter(dto -> Objects.equals(dto.getType(), OrgChartType.P))
                .collect(Collectors.toMap(
                        OrgChartDto::getId,
                        dto -> {
                            RoleDto roleDto = Objects.requireNonNull(
                                    roleDtoList.stream().filter(role -> Objects.equals(role.getOrgChart().getId(), dto.getId()))
                                            .findAny().orElse(null)
                            );

                            StaffDto staffDto = staffDtoList.stream().filter(staff -> staff.getRole() != null && Objects.equals(staff.getRole().getId(), roleDto.getId()))
                                    .findAny().orElse(null);

                            StaffProfileDto staffProfileDto;
                            if (staffDto != null) {
                                staffProfileDto = staffProfileService.findById(staffDto.getId());
                            } else {
                                staffProfileDto = null;
                            }

                            return new OrgChartPersonNodeDto(
                                    staffDto == null ? null : staffDto.getId(),
                                    dto.getId(),
                                    roleDto.getId(),
                                    roleDto.getName(),
                                    staffDto == null ? null : staffDto.getName(),
                                    staffDto == null ? null : staffDto.getEmail(),
                                    staffProfileDto == null ? null : staffProfileDto.getProfilePicturePath()
                            );
                        }));

        Map<Long, OrgChartGraphDto> graphMap = orgChartDtoList.stream().collect(Collectors.toMap(
                OrgChartDto::getId,
                dto -> {
                    OrgChartGraphNodeDto node = new OrgChartGraphNodeDto(
                            dto.getType().getKey(),
                            dto.getType() == OrgChartType.D ? departmentNodeMap.get(dto.getId()) : null,
                            dto.getType() == OrgChartType.P ? personNodeMap.get(dto.getId()) : null
                    );
                    return new OrgChartGraphDto(
                            dto.getId(),
                            dto.getName(),
                            node,
                            new ArrayList<>()
                    );
                }
        ));

        for (Map.Entry<Long, List<Long>> entry : childrenMap.entrySet()) {
            Long parentId = entry.getKey();
            OrgChartGraphDto parentNode = graphMap.get(parentId);
            if (parentNode != null) {
                List<OrgChartGraphDto> childNodes = entry.getValue().stream()
                        .map(graphMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                parentNode.setChildren(childNodes);
            }
        }

        Set<Long> allParents = childrenMap.keySet();
        Set<Long> allChildren = childrenMap.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        Set<Long> rootIds = allParents.stream()
                .filter(p -> !allChildren.contains(p))
                .collect(Collectors.toSet());
        List<OrgChartGraphDto> roots = rootIds.stream()
                .map(graphMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return ResponseEntity.ok(roots);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ORG_CHART.getAuthorityName()
            )
            """)
    @GetMapping("/export")
    public ResponseEntity<?> exportOrgChartData(Authentication authentication) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sheet 1");
            XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
            int rowIndex = 0;

            // -- header
            Row header = sheet.createRow(rowIndex);

            Cell cellType = header.createCell(0);
            cellType.setCellValue("Node Type");
            createCellComment(drawing, cellType, messageSource.getMessage(TYPE_NOTES, null, Locale.getDefault()));

            Cell cellName = header.createCell(1);
            cellName.setCellValue("Node Name");
            createCellComment(drawing, cellName, messageSource.getMessage(NAME_NOTES, null, Locale.getDefault()));

            Cell cellParentName = header.createCell(2);
            cellParentName.setCellValue("Parent Node Name");
            createCellComment(drawing, cellParentName, messageSource.getMessage(PARENT_NAME_NOTES, null, Locale.getDefault()));

            Cell cellRename = header.createCell(3);
            cellRename.setCellValue("Rename To");
            createCellComment(drawing, cellRename, messageSource.getMessage(RENAME_NOTES, null, Locale.getDefault()));

            Cell cellDeleted = header.createCell(4);
            cellDeleted.setCellValue("To Be Deleted");
            createCellComment(drawing, cellDeleted, messageSource.getMessage(DELETED_NOTES, null, Locale.getDefault()));

            rowIndex++;

            Map<Long, OrgChartDto> existedOrgChartMap = orgChartService.findAllByIsDeletedIsFalse().stream()
                    .collect(Collectors.toMap(
                            OrgChartDto::getId,
                            Function.identity()
                    ));

            List<ParentChildNodeDto> existedParentChildNodeDtos = parentChildNodeService.findAllByRelationType(RelationType.ORG_CHART);

            for (OrgChartDto orgChartDto : existedOrgChartMap.values()) {
                List<ParentChildNodeDto> parentNodes = existedParentChildNodeDtos.stream()
                        .filter(dto -> Objects.equals(dto.getChildId(), orgChartDto.getId()))
                        .toList();

                if (!parentNodes.isEmpty()) {
                    for (ParentChildNodeDto parentNode : parentNodes) {
                        Row row = sheet.createRow(rowIndex);
                        row.createCell(0).setCellValue(orgChartDto.getType().getKey());
                        row.createCell(1).setCellValue(orgChartDto.getName());
                        row.createCell(2).setCellValue(existedOrgChartMap.get(parentNode.getParentId()).getName());

                        rowIndex++;
                    }
                } else {
                    Row row = sheet.createRow(rowIndex);
                    row.createCell(0).setCellValue(orgChartDto.getType().getKey());
                    row.createCell(1).setCellValue(orgChartDto.getName());

                    rowIndex++;
                }
            }

            workbook.write(out);
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray());


            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Organizational_Chart_Data.xlsx");
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

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ORG_CHART.getAuthorityName()
            )
            """)
    @PostMapping("/import")
    @Transactional
    public ResponseEntity<?> importOrgChartData(@RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {

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

        List<OrgChartDto> existingOrgChartDtos = orgChartService.findAllByIsDeletedIsFalse();
        List<String> existingOrgChartName = existingOrgChartDtos.stream().map(dto -> dto.getName().toLowerCase().trim()).toList();
        Set<String> nameSeen = new HashSet<>(existingOrgChartName);
        Map<Integer, String> rootNameMap = new HashMap<>();
        Map<Integer, String> nonRootNameMap = new HashMap<>();

        List<String> roleNameToBeAdded = new ArrayList<>();
        Set<OrgChartDto> orgChartDtoToBeAddedOrUpdated = new HashSet<>();
        Set<Long> nodeIdToBeDeleted = new HashSet<>();
        Set<Long> roleToBeRemoved = new HashSet<>();
        Map<String, List<String>> hierarchyGraph = new HashMap<>(); // parent & child list

        Sheet sheet = workbook.getSheetAt(0);

        // -- check headers --
        Row header = sheet.getRow(0);
        if (!getCellValueAsString(header.getCell(0)).equalsIgnoreCase("Node Type")
                || !getCellValueAsString(header.getCell(1)).equalsIgnoreCase("Node Name")
                || !getCellValueAsString(header.getCell(2)).equalsIgnoreCase("Parent Node Name")
                || !getCellValueAsString(header.getCell(3)).equalsIgnoreCase("Rename To")
                || !getCellValueAsString(header.getCell(4)).equalsIgnoreCase("To Be Deleted")) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header (row 0)
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(0) == null || getCellValueAsString(row.getCell(0)) == null
                    || getCellValueAsString(row.getCell(0)).isBlank()) continue;

            // -- data --
            String type = getCellValueAsString(row.getCell(0));
            String name = getCellValueAsString(row.getCell(1));
            String parentName = getCellValueAsString(row.getCell(2));
            String renameTo = getCellValueAsString(row.getCell(3));
            String toBeDeleted = getCellValueAsString(row.getCell(4));

            if (validationService.isNullOrBlank(name) || name.length() > 255
                    || (!validationService.isNullOrBlank(renameTo) && renameTo.trim().length() > 255)) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE, new String[]{NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (!type.equalsIgnoreCase("P") && !type.equalsIgnoreCase("D")) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE, new String[]{TYPE_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (toBeDeleted != null && toBeDeleted.equalsIgnoreCase(YES) && !validationService.isNullOrBlank(renameTo)) { // perform deletion and update at the same time
                String errorMessage = messageSource.getMessage(IMPORT_ACTION_CONFLICT_ERR_MSG_CODE,
                        new String[]{Integer.toString(i + 1)},
                        Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            // -- parent name --
            if (validationService.isNullOrBlank(parentName)) { // root node
                if (nonRootNameMap.containsValue(name.toLowerCase())) {
                    String errorMessage = messageSource.getMessage(IMPORT_PARENT_NAME_CONFLICT_ERR_MSG_CODE,
                            new String[]{String.valueOf(getKeyFromValue(nonRootNameMap, name.toLowerCase())) + 1, Integer.toString(i + 1)}, // non empty row, empty row
                            Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                rootNameMap.put(i, name.toLowerCase());
            } else if (validationService.isNullOrBlank(toBeDeleted)) { // non root node & not going to be deleted
                if (rootNameMap.containsValue(name.toLowerCase())) { // parent name conflicts
                    String errorMessage = messageSource.getMessage(IMPORT_PARENT_NAME_CONFLICT_ERR_MSG_CODE,
                            new String[]{Integer.toString(i + 1), String.valueOf(getKeyFromValue(rootNameMap, name.toLowerCase())) + 1}, // non empty row, empty row
                            Locale.getDefault());
                    throw new BadRequestException(errorMessage);
                }

                if (Objects.equals(parentName.toLowerCase(), name.toLowerCase())) { // recursive reference
                    String errorMessage = messageSource.getMessage(IMPORT_RECURSIVE_REFERENCE_ERR_MSG_CODE,
                            new String[]{Integer.toString(i + 1)},
                            Locale.getDefault());
                    throw new BadRequestException(errorMessage);
                }

                nonRootNameMap.put(i, name.toLowerCase());
            }

            // -- name --
            if (existingOrgChartName.contains(name.toLowerCase())) { // existed record
                OrgChartDto orgChartDto = Objects.requireNonNull(existingOrgChartDtos.stream()
                        .filter(dto -> name.equalsIgnoreCase(dto.getName()))
                        .findFirst().orElse(null));

                boolean isUpdating = false;

                if (toBeDeleted != null && toBeDeleted.equalsIgnoreCase(YES)) { // perform deletion
                    nodeIdToBeDeleted.add(orgChartDto.getId());

                    if (type.equalsIgnoreCase("P")) {
                        roleToBeRemoved.add(orgChartDto.getId());
                    }

                    continue;
                }

                if (!type.equalsIgnoreCase(orgChartDto.getType().getKey())) { // type change
                    String errorMessage = messageSource.getMessage(IMPORT_ORG_CHART_TYPE_CHANGE_ERR_MSG_CODE,
                            new String[]{type, Integer.toString(i + 1)},
                            Locale.getDefault());
                    throw new BadRequestException(errorMessage);
                }

                if (validationService.isNullOrBlank(parentName) && !orgChartDto.isRoot()) { // change to root node
                    orgChartDto.setRoot(true);
                    isUpdating = true;
                }

                if (!validationService.isNullOrBlank(renameTo)) { // perform rename
                    String renameDuplicated = !nameSeen.add(renameTo.toLowerCase()) ? renameTo : null;
                    if (renameDuplicated != null) { // rename column redundant
                        String errorMessage = messageSource.getMessage(IMPORT_UNIQUE_NAME_ERR_MSG_CODE,
                                new String[]{name, Integer.toString(i + 1)},
                                Locale.getDefault());

                        throw new BadRequestException(errorMessage);
                    }
                    orgChartDto.setName(renameTo);
                    isUpdating = true;

                    if (!orgChartDto.isRoot()) {
                        hierarchyGraph.computeIfAbsent(parentName.toLowerCase(), k -> new ArrayList<>())
                                .add(renameTo.toLowerCase());
                    }

                    // update children list
                    if (hierarchyGraph.get(name.toLowerCase()) != null) {
                        List<String> existingChildren = new ArrayList<>(hierarchyGraph.get(name.toLowerCase()));

                        if (!existingChildren.isEmpty()) {
                            if (hierarchyGraph.get(renameTo.toLowerCase()) != null) {
                                List<String> upcomingChildren = new ArrayList<>(hierarchyGraph.get(renameTo.toLowerCase()));

                                if (!upcomingChildren.isEmpty()) {
                                    existingChildren.addAll(upcomingChildren);
                                }
                            }

                            hierarchyGraph.computeIfAbsent(renameTo.toLowerCase(), k -> existingChildren);
                        }
                    }

                    hierarchyGraph.remove(name.toLowerCase());

                } else {
                    if (!orgChartDto.isRoot()) {
                        // to check any cycle within the chart
                        hierarchyGraph.computeIfAbsent(parentName.toLowerCase(), k -> new ArrayList<>())
                                .add(name.toLowerCase());
                    }
                }

                if (isUpdating) {
                    orgChartDto.setUpdatedBy(userUUID);
                    orgChartDto.setUpdatedAt(now);
                }

                orgChartDtoToBeAddedOrUpdated.add(orgChartDto);


            } else { // new record
                if ((toBeDeleted != null && toBeDeleted.equalsIgnoreCase(YES)) || !validationService.isNullOrBlank(renameTo)) { // perform deletion and creation at the same time
                    String errorMessage = messageSource.getMessage(IMPORT_ACTION_CONFLICT_ERR_MSG_CODE,
                            new String[]{Integer.toString(i + 1)},
                            Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                if (validationService.isNullOrBlank(parentName)) { //root Node
                    OrgChartDto orgChartDto = new OrgChartDto(
                            Objects.requireNonNull(OrgChartType.fromKey(type.toUpperCase()).orElse(null)),
                            name,
                            true,
                            false,
                            userUUID,
                            now,
                            userUUID,
                            now
                    );

                    orgChartDtoToBeAddedOrUpdated.add(orgChartDto);
                } else { // non root node
                    OrgChartDto orgChartDto = new OrgChartDto(
                            Objects.requireNonNull(OrgChartType.fromKey(type.toUpperCase()).orElse(null)),
                            name,
                            false,
                            false,
                            userUUID,
                            now,
                            userUUID,
                            now
                    );

                    orgChartDtoToBeAddedOrUpdated.add(orgChartDto);

                    // to check any cycle within the chart
                    hierarchyGraph.computeIfAbsent(parentName.toLowerCase(), k -> new ArrayList<>())
                            .add(name.toLowerCase());
                }

                // input as role and org chart at the same time
                if (type.equalsIgnoreCase(OrgChartType.P.getKey())) {
                    roleNameToBeAdded.add(name);
                }

            }
        }
        validateNoCycles(hierarchyGraph); // ensure save to add

        // delete record not in the sheet
        parentChildNodeService.deleteAllByParentIdInOrChildIdIn(nodeIdToBeDeleted);
        orgChartService.deleteAllByIdIn(nodeIdToBeDeleted, userUUID, now);

        Map<String, OrgChartDto> orgChartDtoUpdatedAndCreatedMap;
        if (!orgChartDtoToBeAddedOrUpdated.isEmpty()) {
            orgChartDtoUpdatedAndCreatedMap = orgChartService
                    .createAndUpdateAll(orgChartDtoToBeAddedOrUpdated.stream().toList())
                    .stream().collect(Collectors.toMap(
                            dto -> dto.getName().toLowerCase(),
                            Function.identity()
                    ));
        } else {
            orgChartDtoUpdatedAndCreatedMap = Collections.emptyMap();
        }

        if (!roleNameToBeAdded.isEmpty()) {
            List<RoleDto> roleDtoToBeAdded = roleNameToBeAdded.stream().map(name -> new RoleDto(
                            name.trim(),
                            null,
                            true,
                            false,
                            userUUID,
                            now,
                            userUUID,
                            now,
                            orgChartDtoUpdatedAndCreatedMap.get(name.toLowerCase())))
                    .toList();

            List<RoleDto> createdRole = roleService.createAll(roleDtoToBeAdded);

            AuthorityDto authorityDto = authorityService.findByName(AuthorityName.ROLE_USER);

            List<RoleAuthorityDto> roleAuthorityToBeCreated = createdRole.stream().map(role ->
                            new RoleAuthorityDto(
                                    new RoleAuthorityId(role.getId(), authorityDto.getId()),
                                    role,
                                    authorityDto,
                                    userUUID,
                                    now,
                                    userUUID,
                                    now
                            ))
                    .toList();

            roleAuthorityService.createAll(roleAuthorityToBeCreated);
        }

        if (!roleToBeRemoved.isEmpty()) {
            Set<Long> deletedRoleIds = roleService.findAndDeleteAllByOrgChartIdIn(roleToBeRemoved, userUUID, now)
                    .stream().map(RoleDto::getId).collect(Collectors.toSet());

            roleAuthorityService.deleteAllByRoleIdIn(deletedRoleIds);

            roleCompetencyService.deleteAllByRoleIdIn(deletedRoleIds);
        }

        if (!hierarchyGraph.isEmpty()) {
            List<ParentChildNodeDto> linkageToBeUpdatedOrCreated = hierarchyGraph.entrySet().stream().flatMap(map ->
                    {
                        if (orgChartDtoUpdatedAndCreatedMap.containsKey(map.getKey())) {
                            Long parentId = orgChartDtoUpdatedAndCreatedMap.get(map.getKey()).getId();

                            if (orgChartDtoUpdatedAndCreatedMap.keySet().containsAll(map.getValue())) {
                                Set<Long> childIds = map.getValue().stream()
                                        .map(id -> orgChartDtoUpdatedAndCreatedMap.get(id).getId())
                                        .collect(Collectors.toSet());

                                List<ParentChildNodeDto> toBeAddedOrUpdated = childIds.stream().map(childId -> new ParentChildNodeDto(
                                                RelationType.ORG_CHART,
                                                parentId,
                                                childId,
                                                userUUID,
                                                now,
                                                userUUID,
                                                now))
                                        .toList();

                                return toBeAddedOrUpdated.stream();
                            } else {
                                String errorMessage = messageSource.getMessage(IMPORT_CHILD_NODE_NULL_ERR_MSG_CODE,
                                        new String[]{map.getValue().toString()},
                                        Locale.getDefault());
                                throw new BadRequestException(errorMessage);
                            }
                        } else {
                            String errorMessage = messageSource.getMessage(IMPORT_PARENT_NODE_NULL_ERR_MSG_CODE,
                                    new String[]{map.getKey()},
                                    Locale.getDefault());
                            throw new BadRequestException(errorMessage);
                        }
                    }
            ).toList();

            parentChildNodeService.createALl(linkageToBeUpdatedOrCreated, RelationType.ORG_CHART);
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(IMPORT_OK, null, Locale.getDefault())
        ));

    }

    private void validateNoCycles(Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();

        for (String node : graph.keySet()) {
            if (detectCycle(node, graph, visited, recStack)) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_HIERARCHY_ERR_MSG_CODE, new String[]{node}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }
        }
    }

    private boolean detectCycle(String node, Map<String, List<String>> graph,
                                Set<String> visited, Set<String> recStack) {
        if (recStack.contains(node)) return true;
        if (visited.contains(node)) return false;

        visited.add(node);
        recStack.add(node);

        List<String> children = graph.getOrDefault(node, new ArrayList<>());
        for (String child : children) {
            if (detectCycle(child, graph, visited, recStack)) {
                return true;
            }
        }

        recStack.remove(node);
        return false;
    }

    private static <K, V> K getKeyFromValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (Objects.equals(entry.getValue(), value)) { // Use .equals() for object comparison
                return entry.getKey();
            }
        }
        return null; // Return null if the value is not found
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

    @GetMapping("/get-departments")
    public ResponseEntity<List<OrgChartDto>> getDepartmentList() {
        List<OrgChartDto> departments = orgChartService.getDepartments();
        return ResponseEntity.ok(departments);
    }
}
