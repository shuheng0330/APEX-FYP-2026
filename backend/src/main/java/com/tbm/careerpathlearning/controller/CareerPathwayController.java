package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
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
@RequestMapping("/api/career-pathway")
public class CareerPathwayController {

    private static final Logger logger = LoggerFactory.getLogger(CareerPathwayController.class);

    @Autowired
    private CareerPathwayService careerPathwayService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private OrgChartService orgChartService;

    @Autowired
    private TrackService trackService;

    @Autowired
    private CareerPathwayTrackService careerPathwayTrackService;

    @Autowired
    private CareerPathwayRoleService careerPathwayRoleService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private StaffService staffService;

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String CAREER_PATHWAY_MULTIPLE_ROOT_ERR_MSG_CODE = "career.pathway.multiple.root.msg";

    private static final String IMPORT_CAREER_PATHWAY_MULTIPLE_ROOT_ERR_MSG_CODE = "import.career.pathway.multiple.root.msg";

    private static final String CAREER_PATHWAY_CIRCULAR_ERR_MSG_CODE = "career.pathway.circular.root.msg";

    private static final String IMPORT_CAREER_PATHWAY_CIRCULAR_ERR_MSG_CODE = "import.career.pathway.circular.root.msg";

    private static final String CAREER_PATHWAY_DISCONNECTED_ERR_MSG_CODE = "career.pathway.disconnected.root.msg";

    private static final String IMPORT_CAREER_PATHWAY_DISCONNECTED_ERR_MSG_CODE = "import.career.pathway.disconnected.root.msg";

    private static final String CAREER_PATHWAY_CREATION = "Create Career Pathway";

    private static final String CAREER_PATHWAY_UPDATE = "Update Career Pathway";

    private static final String CAREER_PATHWAY_DELETE = "Remove Career Pathway";

    private static final String CAREER_PATHWAY_CREATION_OK = "career.pathway.creation.ok.msg";

    private static final String CAREER_PATHWAY_UPDATE_OK = "career.pathway.update.ok.msg";

    private static final String CAREER_PATHWAY_DELETE_OK = "career.pathway.delete.ok.msg";

    private static final String DEPT_NAME_COLUMN = "Department Name";

    private static final String CAREER_PATHWAY_NAME_COLUMN = "Career Pathway Name";

    private static final String CAREER_PATHWAY_DESC_COLUMN = "Career Pathway Description";

    private static final String TAGS_COLUMN = "Tags";

    private static final String ROOT_ROLE_COLUMN = "Root Role Name";

    private static final String PARENT_CHILD_ROLE_COLUMN = "Parent Role Name > Child Role Name";

    private static final String NEW_CAREER_PATHWAY_NAME_COLUMN = "New Career Pathway Name";

    private static final String TO_BE_DELETED_COLUMN = "To Be Deleted";

    private static final String DEPT_NAME_NOTES = "career.pathway.definition.department.name.notes";

    private static final String CAREER_PATHWAY_NAME_NOTES = "career.pathway.definition.name.notes";

    private static final String CAREER_PATHWAY_DESC_NOTES = "career.pathway.definition.desc.notes";

    private static final String TAGS_NOTES = "career.pathway.definition.tags.notes";

    private static final String ROOT_ROLE_NOTES = "career.pathway.definition.root.name.notes";

    private static final String PARENT_CHILD_ROLE_NOTES = "career.pathway.definition.parent.child.name.notes";

    private static final String NEW_CAREER_PATHWAY_NAME_NOTES = "career.pathway.definition.new.name.notes";

    private static final String TO_BE_DELETED_NOTES = "career.pathway.definition.deleted.notes";

    private static final String INTERNAL_SERVER_ERROR_EXP_ERR_TITLE_CODE = "internal.server.err.title";

    private static final String EXPORT_ERR_MSG_CODE = "export.data.err.msg";

    private static final String IMPORT_OPERATION = "Import Role Assignment Data";

    private static final String IMPORT_INVALID_DATA_ERR_MSG_CODE = "import.invalid.data.err.msg";

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
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @GetMapping()
    @Transactional
    public ResponseEntity<?> getAll(Authentication authentication) {
        return ResponseEntity.ok(careerPathwayService.getAllByIsDeletedIsFalse());
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
            )
            """)
    @GetMapping("/overview")
    @Transactional
    public ResponseEntity<?> overview(Authentication authentication) {

        List<CareerPathwayDto> existingRecords = careerPathwayService.getAllByIsDeletedIsFalse();
        List<CareerPathwayTrackDto> existingTracks = careerPathwayTrackService.findAll();
        List<CareerPathwayRoleDto> relations = careerPathwayRoleService.getAll();
        Set<RoleDto> selectedRoles = relations.stream().map(CareerPathwayRoleDto::getChildRole).collect(Collectors.toSet());
        selectedRoles.addAll(relations.stream().map(CareerPathwayRoleDto::getParentRole).collect(Collectors.toSet()));
        selectedRoles.addAll(existingRecords.stream().map(CareerPathwayDto::getRootRole).collect(Collectors.toSet()));

        Map<Long, CareerPathwayNodeDto> graphMap = selectedRoles.stream().collect(Collectors.toMap(
                RoleDto::getId,
                dto ->
                        new CareerPathwayNodeDto(
                                dto.getId(),
                                dto.getName(),
                                new CareerPathwayNodeDataDto(dto.isDeleted()),
                                new ArrayList<>()
                        )));

        List<CareerPathwayOverviewDto> result = existingRecords.stream().map(dto -> {
                    List<TrackDto> assignedTrack = existingTracks.stream().filter(assignment ->
                                    Objects.equals(assignment.getId().getCareerPathwayId(), dto.getId()))
                            .map(CareerPathwayTrackDto::getTrack).toList();

                    List<CareerPathwayRoleDto> assignedRelations = relations.stream().filter(rel ->
                                    Objects.equals(rel.getId().getCareerPathwayId(), dto.getId()))
                            .toList();
                    relations.removeAll(assignedRelations);

                    Map<Long, Set<Long>> childrenMap = new HashMap<>(assignedRelations.size());
                    assignedRelations.forEach(rel ->
                            childrenMap.computeIfAbsent(rel.getId().getParentId(), k -> new HashSet<>()).add(rel.getId().getChildId())
                    );

                    CareerPathwayNodeDto graph = null;
                    if (childrenMap.isEmpty()) {
                        CareerPathwayNodeDto single = graphMap.get(dto.getRootRole().getId());
                        if (single != null) {
                            graph = new CareerPathwayNodeDto(single.getId(), single.getName(), single.getData(), List.of());
                        }
                    } else {
                        Set<Long> allParents = childrenMap.keySet();
                        Set<Long> allChildren = childrenMap.values().stream()
                                .flatMap(Collection::stream)
                                .collect(Collectors.toSet());

                        Long rootId = allParents.stream()
                                .filter(p -> !allChildren.contains(p))
                                .findFirst()
                                .orElse(null);

                        if (rootId != null) {
                            graph = buildGraph(rootId, childrenMap, graphMap);
                        }
                    }

                    return new CareerPathwayOverviewDto(
                            dto.getOrgChart().getId(),
                            dto.getOrgChart().getName(),
                            dto.getOrgChart().isDeleted(),
                            dto.getId(),
                            dto.getName(),
                            dto.getDescription(),
                            assignedTrack,
                            graph
                    );
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    private CareerPathwayNodeDto buildGraph(Long id,
                                            Map<Long, Set<Long>> childrenMap,
                                            Map<Long, CareerPathwayNodeDto> graphMap) {
        CareerPathwayNodeDto node = new CareerPathwayNodeDto();
        CareerPathwayNodeDto ref = graphMap.get(id);
        if (ref == null) return null;

        node.setId(ref.getId());
        node.setName(ref.getName());
        node.setData(ref.getData());

        List<CareerPathwayNodeDto> children = childrenMap.getOrDefault(id, Collections.emptySet())
                .stream()
                .map(childId -> buildGraph(childId, childrenMap, graphMap))
                .filter(Objects::nonNull)
                .toList();

        node.setChildren(children);
        return node;
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
            )
            """)
    @PostMapping()
    @Transactional
    public ResponseEntity<?> createCareerPathway(@RequestBody CreateCareerPathwayRequestDto req,
                                                 Authentication authentication) {
        if (req == null || req.getOrgChartId() == null || validationService.isNullOrBlank(req.getCareerPathwayName())
                || req.getRootRoleId() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{CAREER_PATHWAY_CREATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        validateCareerPathwayGraph(req.getRootRoleId(), req.getParentChildRoleId(), null);

        // -- basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<Long> roleIds = new HashSet<>();
        roleIds.add(req.getRootRoleId());
        req.getParentChildRoleId().forEach((parentId, childIds) -> {
            roleIds.addAll(childIds);
            roleIds.add(parentId);
        });

        OrgChartDto selectedOrgChart = orgChartService.getByById(req.getOrgChartId());
        Map<Long, RoleDto> selectedRoleMap = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .collect(Collectors.toMap(RoleDto::getId, Function.identity()));

        RoleDto rootRole = Objects.requireNonNull(selectedRoleMap.get(req.getRootRoleId()));

        CareerPathwayDto careerPathwayToBeCreated = new CareerPathwayDto(
                req.getCareerPathwayName().trim(),
                req.getDescription() == null ? null : req.getDescription().trim(),
                selectedOrgChart,
                rootRole,
                false,
                userUUID,
                now,
                userUUID,
                now);

        CareerPathwayDto createdCareerPathwayDto = careerPathwayService.create(careerPathwayToBeCreated);

        if (req.getTrack() != null && !req.getTrack().isEmpty()) {
            List<TrackDto> trackToBeCreated = req.getTrack().stream().map(track -> new TrackDto(
                            track,
                            false,
                            userUUID,
                            now,
                            userUUID,
                            now))
                    .toList();

            List<TrackDto> createdTrackDto = trackService.createAll(trackToBeCreated);

            List<CareerPathwayTrackDto> careerPathwayTrackToBeCreated = createdTrackDto.stream().map(dto ->
                            new CareerPathwayTrackDto(
                                    new CareerPathwayTrackId(createdCareerPathwayDto.getId(), dto.getId()),
                                    createdCareerPathwayDto,
                                    dto,
                                    userUUID,
                                    now,
                                    userUUID,
                                    now))
                    .toList();

            careerPathwayTrackService.createAll(careerPathwayTrackToBeCreated);
        }

        List<CareerPathwayRoleDto> nodesToBeCreated = req.getParentChildRoleId().entrySet().stream().flatMap(map -> {
                    Set<Long> childIds = new HashSet<>(map.getValue());

                    List<CareerPathwayRoleDto> childNodes = childIds.stream().map(childId ->
                                    new CareerPathwayRoleDto(
                                            new CareerPathwayRoleId(createdCareerPathwayDto.getId(), map.getKey(), childId),
                                            createdCareerPathwayDto,
                                            selectedRoleMap.get(map.getKey()),
                                            selectedRoleMap.get(childId),
                                            false,
                                            userUUID,
                                            now,
                                            userUUID,
                                            now
                                    ))
                            .toList();

                    return childNodes.stream();
                })
                .toList();
        if (!nodesToBeCreated.isEmpty()) {
            careerPathwayRoleService.createAll(nodesToBeCreated);
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(CAREER_PATHWAY_CREATION_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
            )
            """)
    @PutMapping("/edit")
    @Transactional
    public ResponseEntity<?> update(@RequestBody CreateCareerPathwayRequestDto req,
                                    Authentication authentication) {
        if (req == null || req.getRootRoleId() == null || req.getCareerPathwayId() == null || req.getOrgChartId() == null || validationService.isNullOrBlank(req.getCareerPathwayName())
                || req.getRootRoleId() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{CAREER_PATHWAY_UPDATE}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        if (req.getParentChildRoleId() == null) {
            req.setParentChildRoleId(Collections.emptyMap());
        }

        validateCareerPathwayGraph(req.getRootRoleId(), req.getParentChildRoleId(), null);

        // -- basic info --
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        CareerPathwayDto selectedRecord = careerPathwayService.getById(req.getCareerPathwayId());

        Set<Long> roleIds = new HashSet<>();
        roleIds.add(req.getRootRoleId());
        req.getParentChildRoleId().forEach((parentId, childIds) -> {
            roleIds.addAll(childIds);
            roleIds.add(parentId);
        });

        Map<Long, RoleDto> selectedRoleMap = roleService.getAllByIsDeletedIsFalseAndIdIn(roleIds).stream()
                .collect(Collectors.toMap(RoleDto::getId, Function.identity()));

        RoleDto rootRole = Objects.requireNonNull(selectedRoleMap.get(req.getRootRoleId()));

        selectedRecord.setName(req.getCareerPathwayName().trim());
        selectedRecord.setDescription(req.getDescription() == null ? null : req.getDescription().trim());
        selectedRecord.setRootRole(rootRole);
        selectedRecord.setUpdatedBy(userUUID);
        selectedRecord.setUpdatedAt(now);

        CareerPathwayDto updatedRecord = careerPathwayService.update(req.getCareerPathwayId(), selectedRecord);

        List<TrackDto> selectedTrackDto;
        if (req.getTrack() != null && !req.getTrack().isEmpty()) {
            selectedTrackDto = req.getTrack().stream().map(track -> new TrackDto(
                            track,
                            false,
                            userUUID,
                            now,
                            userUUID,
                            now))
                    .toList();
        } else {
            selectedTrackDto = Collections.emptyList();
        }

        Map<Long, TrackDto> selectedTrackMap = trackService.createAll(selectedTrackDto).stream()
                .collect(Collectors.toMap(TrackDto::getId, Function.identity()));

        Set<Long> assignedTrackIds = careerPathwayTrackService.findAllByCareerPathwayId(req.getCareerPathwayId())
                .stream().map(dto -> dto.getId().getTrackId()).collect(Collectors.toSet());

        Set<Long> trackToAdd = new HashSet<>(selectedTrackMap.keySet());
        trackToAdd.removeAll(assignedTrackIds);

        Set<Long> trackToRemove = new HashSet<>(assignedTrackIds);
        trackToRemove.removeAll(selectedTrackMap.keySet());

        if (!trackToRemove.isEmpty()) {
            careerPathwayTrackService.deleteAllById(
                    trackToRemove.stream().map(id ->
                                    new CareerPathwayTrackId(req.getCareerPathwayId(), id))
                            .collect(Collectors.toSet()));

            removeTrackIfNotLongerUsed(trackToRemove, userUUID, now);
        }

        if (!trackToAdd.isEmpty()) {
            List<CareerPathwayTrackDto> careerPathwayTrackToBeCreated = trackToAdd.stream().map(id ->
                            new CareerPathwayTrackDto(
                                    new CareerPathwayTrackId(req.getCareerPathwayId(), id),
                                    updatedRecord,
                                    selectedTrackMap.get(id),
                                    userUUID,
                                    now,
                                    userUUID,
                                    now))
                    .toList();

            careerPathwayTrackService.createAll(careerPathwayTrackToBeCreated);
        }

        List<CareerPathwayRoleDto> selectedNodes;

        if (!req.getParentChildRoleId().isEmpty()) {
            selectedNodes = req.getParentChildRoleId().entrySet().stream().flatMap(map -> {
                        Set<Long> childIds = new HashSet<>(map.getValue());

                        List<CareerPathwayRoleDto> childNodes = childIds.stream().map(childId ->
                                        new CareerPathwayRoleDto(
                                                new CareerPathwayRoleId(req.getCareerPathwayId(), map.getKey(), childId),
                                                updatedRecord,
                                                selectedRoleMap.get(map.getKey()),
                                                selectedRoleMap.get(childId),
                                                false,
                                                userUUID,
                                                now,
                                                userUUID,
                                                now
                                        ))
                                .toList();

                        return childNodes.stream();
                    })
                    .toList();
        } else {
            selectedNodes = Collections.emptyList();
        }

        Map<CareerPathwayRoleId, CareerPathwayRoleDto> selectedNodeMap = selectedNodes.stream().collect(Collectors.toMap(CareerPathwayRoleDto::getId, Function.identity()));

        Set<CareerPathwayRoleId> assignedNodeIds = careerPathwayRoleService.getAllByCareerPathwayId(req.getCareerPathwayId())
                .stream().map(CareerPathwayRoleDto::getId).collect(Collectors.toSet());

        Set<CareerPathwayRoleId> nodeToAdd = new HashSet<>(selectedNodeMap.keySet());
        nodeToAdd.removeAll(assignedNodeIds);

        Set<CareerPathwayRoleId> nodeToRemove = new HashSet<>(assignedNodeIds);
        nodeToRemove.removeAll(selectedNodeMap.keySet());

        if (!nodeToRemove.isEmpty()) {
            careerPathwayRoleService.deleteAllByIdIn(nodeToRemove);
        }

        if (!nodeToAdd.isEmpty()) {
            List<CareerPathwayRoleDto> toBeAdded = nodeToAdd.stream().map(selectedNodeMap::get).toList();

            careerPathwayRoleService.createAll(toBeAdded);
        }

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(CAREER_PATHWAY_UPDATE_OK, null, Locale.getDefault())
        ));
    }

    private void removeTrackIfNotLongerUsed(Set<Long> toRemove, UUID userUUID, OffsetDateTime now) {
        Set<Long> stillInUsed = careerPathwayTrackService.findAllByTrackIdIn(toRemove)
                .stream().map(dto -> dto.getId().getTrackId()).collect(Collectors.toSet());
        Set<Long> toDelete = new HashSet<>(toRemove);
        toDelete.removeAll(stillInUsed);

        trackService.deleteAllByIdIn(toDelete, userUUID, now);
    }

    @PreAuthorize("""
            hasAnyAuthority(
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

        // remove selected record
        careerPathwayService.delete(selectedCareerPathwayId, userUUID, now);

        // remove tracks
        Set<Long> deletedTrackIds = careerPathwayTrackService.findAndDeleteByCareerPathwayId(selectedCareerPathwayId)
                .stream().map(dto -> dto.getId().getTrackId()).collect(Collectors.toSet());
        removeTrackIfNotLongerUsed(deletedTrackIds, userUUID, now);

        // remove all nodes
        careerPathwayRoleService.deleteByCareerPathwayId(selectedCareerPathwayId);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(CAREER_PATHWAY_DELETE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
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

        // remove selected record
        careerPathwayService.deleteAll(selectedCareerPathwayIds, userUUID, now);

        // remove tracks
        Set<Long> deletedTrackIds = careerPathwayTrackService.findAndDeleteByCareerPathwayIdIn(selectedCareerPathwayIds)
                .stream().map(dto -> dto.getId().getTrackId()).collect(Collectors.toSet());
        removeTrackIfNotLongerUsed(deletedTrackIds, userUUID, now);

        // remove all nodes
        careerPathwayRoleService.deleteAllByCareerPathwayIdIn(selectedCareerPathwayIds);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(CAREER_PATHWAY_DELETE_OK, null, Locale.getDefault())
        ));
    }

    private void validateCareerPathwayGraph(Long rootRoleId, Map<Long, Set<Long>> parentChildRoleId, String rowNumber) {
        // Build adjacency list and indegree map
        Map<Long, Set<Long>> adj = new HashMap<>();
        Map<Long, Integer> indegree = new HashMap<>();

        indegree.putIfAbsent(rootRoleId, 0);

        // Build graph
        parentChildRoleId.forEach((parent, children) -> {
            adj.putIfAbsent(parent, new HashSet<>());
            for (Long child : children) {
                adj.get(parent).add(child);
                indegree.put(child, indegree.getOrDefault(child, 0) + 1);
                indegree.putIfAbsent(parent, indegree.getOrDefault(parent, 0));
            }
        });

        // --- Check 1: Ensure single root ---
        // Root must be the only node with indegree == 0
        long rootsCount = indegree.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .count();

        if (rootsCount != 1 || !indegree.containsKey(rootRoleId) || indegree.get(rootRoleId) != 0) {
            if (validationService.isNullOrBlank(rowNumber)) {
                String errorMessage = messageSource.getMessage(CAREER_PATHWAY_MULTIPLE_ROOT_ERR_MSG_CODE, null, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }
            String errorMessage = messageSource.getMessage(IMPORT_CAREER_PATHWAY_MULTIPLE_ROOT_ERR_MSG_CODE,
                    new String[]{rowNumber}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // --- Check 2: Detect cycles (DFS) ---
        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();

        if (hasCycleDFS(rootRoleId, adj, visited, recursionStack)) {
            if (validationService.isNullOrBlank(rowNumber)) {
                String errorMessage = messageSource.getMessage(CAREER_PATHWAY_CIRCULAR_ERR_MSG_CODE, null, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }
            String errorMessage = messageSource.getMessage(IMPORT_CAREER_PATHWAY_CIRCULAR_ERR_MSG_CODE,
                    new String[]{rowNumber}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        // --- Check 3: Ensure all nodes reachable from root ---
        if (visited.size() != indegree.size()) {
            if (validationService.isNullOrBlank(rowNumber)) {
                String errorMessage = messageSource.getMessage(CAREER_PATHWAY_DISCONNECTED_ERR_MSG_CODE, null, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }
            String errorMessage = messageSource.getMessage(IMPORT_CAREER_PATHWAY_DISCONNECTED_ERR_MSG_CODE,
                    new String[]{rowNumber}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }
    }

    private boolean hasCycleDFS(Long node, Map<Long, Set<Long>> adj,
                                Set<Long> visited, Set<Long> stack) {
        if (stack.contains(node)) return true; // cycle detected
        if (visited.contains(node)) return false;

        visited.add(node);
        stack.add(node);

        for (Long neighbor : adj.getOrDefault(node, Collections.emptySet())) {
            if (hasCycleDFS(neighbor, adj, visited, stack)) return true;
        }

        stack.remove(node);
        return false;
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @GetMapping("/my")
    @Transactional
    public ResponseEntity<?> my(@RequestParam UUID staffId, Authentication authentication) {
        StaffDto selectedStaff = staffService.findById(staffId);
        CareerPathwayDto myCareerPathway = selectedStaff.getCareerPathway();
        Long currentRoleId = selectedStaff.getRole() == null ? null : selectedStaff.getRole().getId();

        CareerPathwayOverviewDto result;
        if (myCareerPathway != null && currentRoleId != null) {
            List<CareerPathwayRoleDto> assignedRelations = careerPathwayRoleService.getAllByCareerPathwayId(myCareerPathway.getId());
            Set<RoleDto> selectedRoles = assignedRelations.stream().map(CareerPathwayRoleDto::getChildRole).collect(Collectors.toSet());
            selectedRoles.addAll(assignedRelations.stream().map(CareerPathwayRoleDto::getParentRole).collect(Collectors.toSet()));
            selectedRoles.add(myCareerPathway.getRootRole());

            List<CareerPathwayTrackDto> existingTracks = careerPathwayTrackService.findAll();

            Map<Long, CareerPathwayNodeDto> graphMap = selectedRoles.stream().collect(Collectors.toMap(
                    RoleDto::getId,
                    dto ->
                            new CareerPathwayNodeDto(
                                    dto.getId(),
                                    dto.getName(),
                                    new CareerPathwayNodeDataDto(0, dto.isDeleted()),
                                    new ArrayList<>()
                            )));

            List<TrackDto> assignedTrack = existingTracks.stream().filter(assignment ->
                            Objects.equals(assignment.getId().getCareerPathwayId(), myCareerPathway.getId()))
                    .map(CareerPathwayTrackDto::getTrack).toList();

            Map<Long, Set<Long>> childrenMap = new HashMap<>(assignedRelations.size());
            assignedRelations.forEach(rel ->
                    childrenMap.computeIfAbsent(rel.getId().getParentId(), k -> new HashSet<>()).add(rel.getId().getChildId())
            );

            CareerPathwayNodeDto graph = null;
            if (childrenMap.isEmpty()) {
                CareerPathwayNodeDto single = graphMap.get(myCareerPathway.getRootRole().getId());
                if (single != null) {
                    CareerPathwayNodeDataDto nodeData = new CareerPathwayNodeDataDto(0, single.getData().isDeleted());
                    graph = new CareerPathwayNodeDto(single.getId(), single.getName(), nodeData, List.of());
                }
            } else {
                Set<Long> allParents = childrenMap.keySet();
                Set<Long> allChildren = childrenMap.values().stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());

                Long rootId = allParents.stream()
                        .filter(p -> !allChildren.contains(p))
                        .findFirst()
                        .orElse(null);

                Set<Long> visitedRoles = determineVisitedRoles(currentRoleId, assignedRelations);

                if (rootId != null) {
                    graph = buildVisualisedGraph(rootId, childrenMap, graphMap, currentRoleId, visitedRoles);
                }
            }

            result = new CareerPathwayOverviewDto(
                    myCareerPathway.getOrgChart().getId(),
                    myCareerPathway.getOrgChart().getName(),
                    myCareerPathway.getOrgChart().isDeleted(),
                    myCareerPathway.getId(),
                    myCareerPathway.getName(),
                    myCareerPathway.getDescription(),
                    assignedTrack,
                    graph,
                    selectedStaff.getRole() == null ? null : selectedStaff.getRole().getName()
            );
        } else {
            result = null;
        }

        return ResponseEntity.ok(result);
    }

    private Set<Long> determineVisitedRoles(Long currentRoleId, List<CareerPathwayRoleDto> relations) {
        Set<Long> visited = new HashSet<>();
        for (CareerPathwayRoleDto rel : relations) {
            if (rel.getChildRole().getId().equals(currentRoleId)) {
                visited.add(rel.getParentRole().getId());
            }
        }
        return visited;
    }

    private CareerPathwayNodeDto buildVisualisedGraph(
            Long id,
            Map<Long, Set<Long>> childrenMap,
            Map<Long, CareerPathwayNodeDto> graphMap,
            Long currentRoleId,
            Set<Long> visitedRoles
    ) {
        CareerPathwayNodeDto ref = graphMap.get(id);
        if (ref == null) return null;

        CareerPathwayNodeDto node = new CareerPathwayNodeDto();
        node.setId(ref.getId());
        node.setName(ref.getName());

        CareerPathwayNodeDataDto data = new CareerPathwayNodeDataDto();

        if (id.equals(currentRoleId)) {
            data.setHasVisited(0); // current role
        } else if (visitedRoles.contains(id)) {
            data.setHasVisited(1); // visited
        } else {
            data.setHasVisited(-1); // not yet visited
        }

        node.setData(data);

        List<CareerPathwayNodeDto> children = childrenMap.getOrDefault(id, Collections.emptySet())
                .stream()
                .map(childId -> buildVisualisedGraph(childId, childrenMap, graphMap, currentRoleId, visitedRoles))
                .filter(Objects::nonNull)
                .toList();

        node.setChildren(children);
        return node;
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_CAREER_PATHWAY.getAuthorityName()
            )
            """)
    @GetMapping("/export")
    public ResponseEntity<?> exportCareerPathwayOverviewData(Authentication authentication) {
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

            Cell cellCareerPathwayName = header.createCell(1);
            cellCareerPathwayName.setCellValue(CAREER_PATHWAY_NAME_COLUMN);
            createCellComment(drawing, cellCareerPathwayName, messageSource.getMessage(CAREER_PATHWAY_NAME_NOTES, null, Locale.getDefault()));

            Cell cellDesc = header.createCell(2);
            cellDesc.setCellValue(CAREER_PATHWAY_DESC_COLUMN);
            createCellComment(drawing, cellDesc, messageSource.getMessage(CAREER_PATHWAY_DESC_NOTES, null, Locale.getDefault()));

            Cell cellTags = header.createCell(3);
            cellTags.setCellValue(TAGS_COLUMN);
            createCellComment(drawing, cellTags, messageSource.getMessage(TAGS_NOTES, null, Locale.getDefault()));

            Cell cellRootRole = header.createCell(4);
            cellRootRole.setCellValue(ROOT_ROLE_COLUMN);
            createCellComment(drawing, cellRootRole, messageSource.getMessage(ROOT_ROLE_NOTES, null, Locale.getDefault()));

            Cell cellParentChildRole = header.createCell(5);
            cellParentChildRole.setCellValue(PARENT_CHILD_ROLE_COLUMN);
            createCellComment(drawing, cellParentChildRole, messageSource.getMessage(PARENT_CHILD_ROLE_NOTES, null, Locale.getDefault()));

            Cell cellNewName = header.createCell(6);
            cellNewName.setCellValue(NEW_CAREER_PATHWAY_NAME_COLUMN);
            createCellComment(drawing, cellNewName, messageSource.getMessage(NEW_CAREER_PATHWAY_NAME_NOTES, null, Locale.getDefault()));

            Cell cellDeleted = header.createCell(7);
            cellDeleted.setCellValue(TO_BE_DELETED_COLUMN);
            createCellComment(drawing, cellDeleted, messageSource.getMessage(TO_BE_DELETED_NOTES, null, Locale.getDefault()));

            rowIndex++;

            List<CareerPathwayDto> allCareerPathway = careerPathwayService.getAllByIsDeletedIsFalse();
            List<CareerPathwayRoleDto> allAssignedRoleNode = careerPathwayRoleService.getAll();
            List<CareerPathwayTrackDto> allAssignedTracks = careerPathwayTrackService.findAll();

            for (CareerPathwayDto dto : allCareerPathway) {
                String tags = allAssignedTracks.stream().filter(tag ->
                                Objects.equals(tag.getId().getCareerPathwayId(), dto.getId()))
                        .map(tag -> tag.getTrack().getTrack())
                        .collect(Collectors.joining("; "));

                List<String> assignedRole = allAssignedRoleNode.stream().filter(role ->
                                Objects.equals(role.getId().getCareerPathwayId(), dto.getId()))
                        .map(role -> String.format("%s > %s", role.getParentRole().getName(), role.getChildRole().getName()))
                        .toList();

                String nodes = "";
                if (!assignedRole.isEmpty()) {
                    nodes = String.join("; ", assignedRole);
                }

                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(dto.getOrgChart().getName());
                row.createCell(1).setCellValue(dto.getName());
                row.createCell(2).setCellValue(dto.getDescription());
                row.createCell(3).setCellValue(tags);
                row.createCell(4).setCellValue(dto.getRootRole().getName());
                row.createCell(5).setCellValue(nodes);

                rowIndex++;
            }

            workbook.write(out);
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Career_Pathway_Overview_Data.xlsx");
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
        Map<String, OrgChartDto> allDepartmentMap = allDepartment.stream().collect(Collectors.toMap(
                dto -> dto.getName().toLowerCase().trim(),
                Function.identity()
        ));
        List<RoleDto> allRoles = roleService.getAllByDeletedIsFalse();
        Map<String, Set<String>> alldepartmentRoleNameMap = allDepartment.stream().collect(Collectors.toMap(
                dto -> dto.getName().trim().toLowerCase(),
                dto -> allRoles.stream().filter(role ->
                                Objects.equals(role.getOrgChart().getId(), dto.getId()))
                        .map(role -> role.getName().toLowerCase().trim())
                        .collect(Collectors.toSet())));

        Map<Long, RoleDto> allRoleIdMap = allRoles.stream().collect(Collectors.toMap(
                RoleDto::getId,
                Function.identity()
        ));

        List<CareerPathwayDto> allCareerPathway = careerPathwayService.getAllByIsDeletedIsFalse();
        Map<String, CareerPathwayDto> allCareerPathwayNameMap = allCareerPathway.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getName().trim().toLowerCase(),
                        Function.identity()
                ));
        Map<String, Set<String>> allDepartmentCareerPathwayMap = allDepartment.stream().collect(Collectors.toMap(
                dto -> dto.getName().trim().toLowerCase(),
                dto -> allCareerPathway.stream().filter(careerPathway ->
                                Objects.equals(careerPathway.getOrgChart().getId(), dto.getId()))
                        .map(careerPathway -> careerPathway.getName().toLowerCase().trim())
                        .collect(Collectors.toSet())));

        List<CareerPathwayTrackDto> allAssignedTag = careerPathwayTrackService.findAll();

        List<CareerPathwayRoleDto> allAssignedRole = careerPathwayRoleService.getAll();
        Map<CareerPathwayRoleId, CareerPathwayRoleDto> allAssignedRoleMap = allAssignedRole.stream()
                .collect(Collectors.toMap(
                        CareerPathwayRoleDto::getId,
                        Function.identity()
                ));

        Sheet sheet = workbook.getSheetAt(0);

        // -- check headers --
        Row header = sheet.getRow(0);
        if (!getCellValueAsString(header.getCell(0)).equalsIgnoreCase(DEPT_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(1)).equalsIgnoreCase(CAREER_PATHWAY_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(2)).equalsIgnoreCase(CAREER_PATHWAY_DESC_COLUMN)
                || !getCellValueAsString(header.getCell(3)).equalsIgnoreCase(TAGS_COLUMN)
                || !getCellValueAsString(header.getCell(4)).equalsIgnoreCase(ROOT_ROLE_COLUMN)
                || !getCellValueAsString(header.getCell(5)).equalsIgnoreCase(PARENT_CHILD_ROLE_COLUMN)
                || !getCellValueAsString(header.getCell(6)).equalsIgnoreCase(NEW_CAREER_PATHWAY_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(7)).equalsIgnoreCase(TO_BE_DELETED_COLUMN)
        ) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        List<CareerPathwayDto> careerPathwayDtoToBeUpdated = new ArrayList<>();
        Set<String> tagsToBeCreated = new HashSet<>();
        Map<String, Set<String>> careerPathwayTagToBeCreated = new HashMap<>();
        Set<CareerPathwayTrackId> careerPathwayTagIdsToBeDeleted = new HashSet<>();

        Map<String, Map<Long, Set<Long>>> careerPathwayRoleToBeCreated = new HashMap<>();
        Set<CareerPathwayRoleId> careerPathwayRoleIdsToBeDeleted = new HashSet<>();

        Map<String, Set<String>> duplicatedRow = new HashMap<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header (row 0)
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(0) == null || getCellValueAsString(row.getCell(0)) == null
                    || getCellValueAsString(row.getCell(0)).isBlank()) continue;

            // -- data --
            String departmentName = getCellValueAsString(row.getCell(0));
            String careerPathwayName = getCellValueAsString(row.getCell(1));
            String careerPathwayDesc = getCellValueAsString(row.getCell(2));
            String tags = getCellValueAsString(row.getCell(3));
            String rootRoleName = getCellValueAsString(row.getCell(4));
            String parentChildRoleName = getCellValueAsString(row.getCell(5));
            String newCareerPathwayName = getCellValueAsString(row.getCell(6));
            String toBeDeleted = getCellValueAsString(row.getCell(7));

            if (duplicatedRow.get(departmentName.toLowerCase().trim()) != null) {
                if (duplicatedRow.get(departmentName.toLowerCase().trim()).contains(careerPathwayName.toLowerCase().trim())) {
                    String errorMessage = messageSource.getMessage(IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE, new String[]{Integer.toString(i + 1)}, Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                Set<String> assignedCareerPathwayNameSet = duplicatedRow.get(departmentName.toLowerCase().trim());
                assignedCareerPathwayNameSet.add(careerPathwayName.toLowerCase().trim());
                duplicatedRow.replace(departmentName.toLowerCase().trim(), assignedCareerPathwayNameSet);
            } else {
                Set<String> assignedCareerPathwayNameSet = new HashSet<>();
                assignedCareerPathwayNameSet.add(careerPathwayName.toLowerCase().trim());
                duplicatedRow.put(departmentName.toLowerCase().trim(), assignedCareerPathwayNameSet);
            }

            if (validationService.isNullOrBlank(departmentName) || !alldepartmentRoleNameMap.containsKey(departmentName.toLowerCase().trim())) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{DEPT_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (validationService.isNullOrBlank(careerPathwayName) || careerPathwayName.length() > 255) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{CAREER_PATHWAY_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(careerPathwayDesc) && careerPathwayDesc.trim().length() > 1000) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{CAREER_PATHWAY_DESC_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (validationService.isNullOrBlank(rootRoleName) ||
                    !alldepartmentRoleNameMap.get(departmentName.toLowerCase().trim()).contains(rootRoleName.toLowerCase().trim())) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{ROOT_ROLE_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }
            RoleDto rootRole = Objects.requireNonNull(allRoles.stream().filter(role ->
                            role.getName().equalsIgnoreCase(rootRoleName.trim()))
                    .findAny().orElse(null));

            if (toBeDeleted != null && toBeDeleted.equalsIgnoreCase(YES) &&
                    !validationService.isNullOrBlank(newCareerPathwayName)) { // perform deletion and update at the same time
                String errorMessage = messageSource.getMessage(IMPORT_ACTION_CONFLICT_ERR_MSG_CODE,
                        new String[]{Integer.toString(i + 1)},
                        Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            Set<String> inputtedTags;
            if (!validationService.isNullOrBlank(tags)) {
                int finalI1 = i;
                inputtedTags = Arrays.stream(tags.split(";"))
                        .map(String::trim)
                        .filter(tag -> !validationService.isNullOrBlank(tag))
                        .peek(tag -> {
                            if (tag.length() > 100) {
                                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE, new String[]{TAGS_COLUMN, Integer.toString(finalI1 + 1)}, Locale.getDefault());
                                throw new BadRequestException(errorMessage);
                            }
                        })
                        .collect(Collectors.toSet());
            } else {
                inputtedTags = Collections.emptySet();
            }

            Map<Long, Set<Long>> inputtedParentChildRole = new HashMap<>();
            Set<CareerPathwayRoleId> inputtedParentChildRoleIds = new HashSet<>();
            if (!validationService.isNullOrBlank(parentChildRoleName)) {
                int finalI = i;
                Arrays.stream(parentChildRoleName.split(";")).forEach(parentChildNode -> {
                    parentChildNode = parentChildNode.trim();

                    if (!validationService.isNullOrBlank(parentChildNode)) {
                        String[] parts = parentChildNode.split(">");
                        if (parts.length != 2) {
                            String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                                    new String[]{PARENT_CHILD_ROLE_COLUMN, Integer.toString(finalI + 1)}, Locale.getDefault());
                            throw new BadRequestException(errorMessage);
                        }

                        String parentName = parts[0].trim();
                        String childName = parts[1].trim();
                        Set<String> roleNameWithinDepartment = alldepartmentRoleNameMap.get(departmentName.toLowerCase().trim());

                        if (!roleNameWithinDepartment.contains(parentName.toLowerCase())) {
                            String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{parentName}, Locale.getDefault());
                            throw new BadRequestException(errorMessage);
                        }

                        if (!roleNameWithinDepartment.contains(childName.toLowerCase())) {
                            String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{childName}, Locale.getDefault());
                            throw new BadRequestException(errorMessage);
                        }

                        RoleDto parentRole = Objects.requireNonNull(allRoles.stream().filter(role ->
                                        role.getName().equalsIgnoreCase(parentName.trim()))
                                .findAny().orElse(null));
                        RoleDto childRole = Objects.requireNonNull(allRoles.stream().filter(role ->
                                        role.getName().equalsIgnoreCase(childName.trim()))
                                .findAny().orElse(null));

                        inputtedParentChildRoleIds.add(new CareerPathwayRoleId(
                                null,
                                parentRole.getId(),
                                childRole.getId()
                        ));

                        inputtedParentChildRole
                                .computeIfAbsent(parentRole.getId(), k -> new HashSet<>())
                                .add(childRole.getId());
                    }
                });

                this.validateCareerPathwayGraph(
                        rootRole.getId(),
                        inputtedParentChildRole, Integer.toString(i + 1));
            }

            CareerPathwayDto careerPathwayDto;
            Map<String, CareerPathwayTrackDto> assignedTagMap = new HashMap<>();
            Set<String> assignedTags = new HashSet<>();
            Set<CareerPathwayRoleId> assignedParentChildRole = new HashSet<>();

            if (allDepartmentCareerPathwayMap.get(departmentName.toLowerCase().trim())
                    .contains(careerPathwayName.toLowerCase().trim())) { // update existing record
                careerPathwayDto = Objects.requireNonNull(
                        allCareerPathwayNameMap.get(careerPathwayName.toLowerCase().trim()));
                careerPathwayDto.setUpdatedBy(userUUID);
                careerPathwayDto.setUpdatedAt(now);

                assignedTagMap.putAll(allAssignedTag.stream().filter(dto ->
                                Objects.equals(dto.getId().getCareerPathwayId(), careerPathwayDto.getId()))
                        .collect(Collectors.toMap(
                                dto -> dto.getTrack().getTrack().toLowerCase().trim(),
                                Function.identity()
                        )));
                assignedTags.addAll(assignedTagMap.keySet());

                assignedParentChildRole.addAll(
                        allAssignedRoleMap.keySet().stream()
                                .filter(id ->
                                        Objects.equals(id.getCareerPathwayId(), careerPathwayDto.getId()))
                                .collect(Collectors.toSet())
                );

                inputtedParentChildRoleIds.forEach(id ->
                        id.setCareerPathwayId(careerPathwayDto.getId())
                );

                if (!validationService.isNullOrBlank(toBeDeleted) && toBeDeleted.equalsIgnoreCase(YES)) {
                    careerPathwayDto.setDeleted(true);
                    careerPathwayDtoToBeUpdated.add(careerPathwayDto);

                    careerPathwayTagIdsToBeDeleted.addAll(assignedTagMap.values().stream()
                            .map(CareerPathwayTrackDto::getId).collect(Collectors.toSet()));
                    careerPathwayRoleIdsToBeDeleted.addAll(assignedParentChildRole);
                    continue;
                }

                // rename & reassignment
                if (!validationService.isNullOrBlank(newCareerPathwayName)) {
                    Set<String> careerPathwayNameWithinDepartment = allDepartmentCareerPathwayMap.get(departmentName.toLowerCase().trim());
                    if (careerPathwayNameWithinDepartment.contains(newCareerPathwayName.toLowerCase().trim())
                            && !careerPathwayName.equalsIgnoreCase(newCareerPathwayName)) {
                        String errorMessage = messageSource.getMessage(IMPORT_UNIQUE_NAME_ERR_MSG_CODE,
                                new String[]{newCareerPathwayName, Integer.toString(i + 1)}, Locale.getDefault());
                        throw new BadRequestException(errorMessage);
                    }

                    careerPathwayDto.setName(newCareerPathwayName.trim());
                    careerPathwayNameWithinDepartment.remove(careerPathwayName.toLowerCase().trim());
                    careerPathwayNameWithinDepartment.add(newCareerPathwayName.toLowerCase().trim());
                    allDepartmentCareerPathwayMap.replace(departmentName.toLowerCase().trim(), careerPathwayNameWithinDepartment);
                }

                careerPathwayDto.setDescription(validationService.isNullOrBlank(careerPathwayDesc) ? null : careerPathwayDesc.trim());
                careerPathwayDto.setRootRole(rootRole);
            } else { // create new record
                if (!validationService.isNullOrBlank(newCareerPathwayName) ||
                        (!validationService.isNullOrBlank(toBeDeleted) && toBeDeleted.equalsIgnoreCase(YES))) {
                    String errorMessage = messageSource.getMessage(IMPORT_ACTION_CONFLICT_ERR_MSG_CODE,
                            new String[]{Integer.toString(i + 1)},
                            Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                if (allCareerPathwayNameMap.containsKey(careerPathwayName.trim().toLowerCase())) {
                    String errorMessage = messageSource.getMessage(IMPORT_UNIQUE_NAME_ERR_MSG_CODE,
                            new String[]{careerPathwayName, Integer.toString(i + 1)}, Locale.getDefault());
                    throw new BadRequestException(errorMessage);
                }

                careerPathwayDto = new CareerPathwayDto(
                        careerPathwayName.trim(),
                        validationService.isNullOrBlank(careerPathwayDesc) ? null : careerPathwayDesc.trim(),
                        allDepartmentMap.get(departmentName.toLowerCase().trim()),
                        rootRole,
                        false,
                        userUUID,
                        now,
                        userUUID,
                        now
                );

                allCareerPathwayNameMap.put(careerPathwayName.toLowerCase().trim(), careerPathwayDto);
            }

            Set<String> lowerCaseInputtedTags = inputtedTags.stream()
                    .map(tag -> tag.toLowerCase().trim())
                    .collect(Collectors.toSet());
            Set<String> tagToAdd = new HashSet<>(lowerCaseInputtedTags);
            tagToAdd.removeAll(assignedTags);

            Set<String> tagToRemoved = new HashSet<>(assignedTags);
            tagToRemoved.removeAll(lowerCaseInputtedTags);

            if (!tagToRemoved.isEmpty()) { // remove assigned tags
                Set<CareerPathwayTrackId> idToRemove = tagToRemoved.stream().map(tagToRemove ->
                                Objects.requireNonNull(assignedTagMap.get(tagToRemove.toLowerCase().trim()).getId()))
                        .collect(Collectors.toSet());

                careerPathwayTagIdsToBeDeleted.addAll(idToRemove);
            }

            if (!tagToAdd.isEmpty()) { // add existing or newly created tags
                Set<String> toAddBeforeToLowerCase = inputtedTags.stream().filter(tag ->
                                tagToAdd.contains(tag.toLowerCase().trim()))
                        .collect(Collectors.toSet());
                tagsToBeCreated.addAll(toAddBeforeToLowerCase);

                careerPathwayTagToBeCreated.put(careerPathwayDto.getName().toLowerCase().trim(), toAddBeforeToLowerCase);
            }

            Set<CareerPathwayRoleId> normalizedAssigned = assignedParentChildRole.stream()
                    .map(id -> new CareerPathwayRoleId(
                            id.getCareerPathwayId(),
                            id.getParentId(),
                            id.getChildId()
                    ))
                    .collect(Collectors.toSet());

            Set<CareerPathwayRoleId> normalizedInputted = inputtedParentChildRoleIds.stream()
                    .map(id -> new CareerPathwayRoleId(
                            id.getCareerPathwayId(),
                            id.getParentId(),
                            id.getChildId()
                    ))
                    .collect(Collectors.toSet());

            Set<CareerPathwayRoleId> nodeToRemove = new HashSet<>(normalizedAssigned);
            nodeToRemove.removeAll(normalizedInputted);
            careerPathwayRoleIdsToBeDeleted.addAll(nodeToRemove);

            Set<CareerPathwayRoleId> nodeToAdd = new HashSet<>(normalizedInputted);
            nodeToAdd.removeAll(normalizedAssigned);

            if (!nodeToAdd.isEmpty()) {
                Map<Long, Set<Long>> parentRoleToAdd = new HashMap<>();
                nodeToAdd.forEach(node -> {
                            parentRoleToAdd
                                    .computeIfAbsent(node.getParentId(), k -> new HashSet<>())
                                    .add(node.getChildId());
                        }
                );
                careerPathwayRoleToBeCreated.put(careerPathwayName.toLowerCase().trim(), parentRoleToAdd);
            }

            careerPathwayDtoToBeUpdated.add(careerPathwayDto);
        }

        List<CareerPathwayDto> createdCareerPathway;
        if (!careerPathwayDtoToBeUpdated.isEmpty()) {
            createdCareerPathway = careerPathwayService.createAndUpdateAll(careerPathwayDtoToBeUpdated);
        } else {
            createdCareerPathway = Collections.emptyList();
        }

        if (!tagsToBeCreated.isEmpty()) {
            List<TrackDto> tagToBeCreated = tagsToBeCreated.stream().map(tag ->
                            new TrackDto(
                                    tag.trim(),
                                    false,
                                    userUUID,
                                    now,
                                    userUUID,
                                    now))
                    .toList();

            Map<String, TrackDto> createdTagMap = trackService.createAll(tagToBeCreated).stream()
                    .collect(Collectors.toMap(
                            TrackDto::getTrack,
                            Function.identity()
                    ));

            List<CareerPathwayTrackDto> careerPathwayTrackDtoToBeCreated = careerPathwayTagToBeCreated.entrySet().stream().flatMap(entrySet -> {
                CareerPathwayDto careerPathwayDto = Objects.requireNonNull(createdCareerPathway.stream().filter(careerPathway ->
                                careerPathway.getName().equalsIgnoreCase(entrySet.getKey()))
                        .findAny().orElse(null));

                List<TrackDto> trackDtoList = entrySet.getValue().stream().map(createdTagMap::get).toList();
                List<CareerPathwayTrackDto> toAdd = trackDtoList.stream().map(trackDto ->
                                new CareerPathwayTrackDto(
                                        new CareerPathwayTrackId(careerPathwayDto.getId(), trackDto.getId()),
                                        careerPathwayDto,
                                        trackDto,
                                        userUUID,
                                        now,
                                        userUUID,
                                        now
                                ))
                        .toList();
                return toAdd.stream();
            }).toList();

            careerPathwayTrackService.createAll(careerPathwayTrackDtoToBeCreated);
        }

        if (!careerPathwayTagIdsToBeDeleted.isEmpty()) {
            careerPathwayTrackService.deleteAllById(careerPathwayTagIdsToBeDeleted);

            Set<Long> tagIdToCheck = careerPathwayTagIdsToBeDeleted.stream().map(CareerPathwayTrackId::getTrackId).collect(Collectors.toSet());
            Set<Long> stillInUsed = careerPathwayTrackService.findAllByTrackIdIn(tagIdToCheck)
                    .stream().map(dto -> dto.getId().getTrackId()).collect(Collectors.toSet());

            Set<Long> toRemove = new HashSet<>(tagIdToCheck);
            toRemove.removeAll(stillInUsed);

            trackService.deleteAllByIdIn(toRemove, userUUID, now);
        }

        if (!careerPathwayRoleToBeCreated.isEmpty()) {
            List<CareerPathwayRoleDto> careerPathwayRoleToCreate = careerPathwayRoleToBeCreated.entrySet().stream().flatMap(entrySet -> {
                        CareerPathwayDto careerPathwayDto = Objects.requireNonNull(createdCareerPathway.stream().filter(careerPathway ->
                                        careerPathway.getName().equalsIgnoreCase(entrySet.getKey()))
                                .findAny().orElse(null));

                        List<CareerPathwayRoleDto> toAdd = entrySet.getValue().entrySet().stream().flatMap(parentChild -> {
                            RoleDto parentRole = Objects.requireNonNull(allRoleIdMap.get(parentChild.getKey()));
                            List<CareerPathwayRoleDto> parentChildToAdd = parentChild.getValue().stream().map(child ->
                                            new CareerPathwayRoleDto(
                                                    new CareerPathwayRoleId(careerPathwayDto.getId(), parentChild.getKey(), child),
                                                    careerPathwayDto,
                                                    parentRole,
                                                    Objects.requireNonNull(allRoleIdMap.get(child)),
                                                    false,
                                                    userUUID,
                                                    now,
                                                    userUUID,
                                                    now
                                            ))
                                    .toList();
                            return parentChildToAdd.stream();
                        }).toList();

                        return toAdd.stream();
                    })
                    .toList();

            careerPathwayRoleService.createAll(careerPathwayRoleToCreate);
        }

        if (!careerPathwayRoleIdsToBeDeleted.isEmpty()) {
            careerPathwayRoleService.deleteAllByIdIn(careerPathwayRoleIdsToBeDeleted);
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