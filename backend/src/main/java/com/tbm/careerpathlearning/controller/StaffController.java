package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.RelationType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/staff")
@CrossOrigin
public class StaffController {

    @Value("${file.upload-dir}")
    private String UPLOAD_DIR;

    @Autowired
    private StaffService staffService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private CareerPathwayService careerPathwayService;

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private RoleAuthorityService roleAuthorityService;

    @Autowired
    private StaffLoginAuditService staffLoginAuditService;

    @Autowired
    private CareerPathwayRoleService careerPathwayRoleService;

    @Autowired
    private StaffProfileService staffProfileService;

    @Autowired
    private StaffSelfDeclaredSkillService staffSelfDeclaredSkillService;

    @Autowired
    private StaffCertService staffCertService;

    @Autowired
    private ParentChildNodeService parentChildNodeService;

    @Autowired
    private OrgChartService orgChartService;

    private static final String PROFILES_DIR = "profiles";

    private static final String EMAIL_REGISTERED_ERR_TITLE_CODE = "email.registered.err.title";

    private static final String EMAIL_REGISTERED_ERR_MSG_CODE = "email.registered.err.msg";

    private static final String INVALID_REQUEST_BODY_ERR_MSG_CODE = "invalid.request.body.err.msg";

    private static final String UNABLE_ASSIGN_CAREER_PATHWAY_ERR_TITLE_CODE = "unable.assign.career.pathway.err.title";

    private static final String UNABLE_ASSIGN_CAREER_PATHWAY_ERR_MSG_CODE = "unable.assign.career.pathway.err.msg";

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE = "career.pathway.outside.err.msg";

    private static final String STAFF_EDIT_OPERATION = "Update Staff Account";

    private static final String STAFF_REMOVE_OPERATION = "Remove Staff Account";

    private static final String STAFF_QUERY_OPERATION = "Staff Query";

    private static final String REGISTER_OPERATION = "Registration";

    private static final String TOGGLE_ACCOUNT_STATUS_OPERATION = "Toggle Staff Account Status";

    private static final String ACCOUNT_REGISTER_OK = "staff.register.ok.msg";

    private static final String STAFF_EDIT_OK = "staff.edit.ok.msg";

    private static final String TOGGLE_ACCOUNT_STATUS_OK = "toggle.staff.account.status.ok.msg";

    private static final String STAFF_REMOVE_OK = "staff.delete.ok.msg";

    private static final String STAFF_EMAIL_NOTES = "staff.email.notes";

    private static final String STAFF_NAME_NOTES = "staff.name.notes";

    private static final String DEPT_NAME_NOTES = "staff.department.name.notes";

    private static final String ROLE_NAME_NOTES = "staff.role.name.notes";

    private static final String CAREER_PATHWAY_NAME_NOTES = "staff.career.pathway.name.notes";

    private static final String MANAGER_EMAIL_NOTES = "staff.manager.email.notes";

    private static final String ACCOUNT_STATUS_NOTES = "staff.account.status.notes";

    private static final String NEW_STAFF_EMAIL_NOTES = "staff.new.email.notes";

    private static final String TO_BE_DELETED_NOTES = "staff.deleted.notes";

    private static final String STAFF_EMAIL_COLUMN = "Staff Email";

    private static final String STAFF_NAME_COLUMN = "Staff Name";

    private static final String DEPT_NAME_COLUMN = "Department Name";

    private static final String ROLE_NAME_COLUMN = "Role Name";

    private static final String CAREER_PATHWAY_NAME_COLUMN = "Career Pathway Name";

    private static final String MANAGER_EMAIL_COLUMN = "Direct Manager Email";

    private static final String ACCOUNT_STATUS_COLUMN = "Account Status";

    private static final String NEW_STAFF_EMAIL_COLUMN = "New Staff Email";

    private static final String TO_BE_DELETED_COLUMN = "To Be Deleted";

    private static final String INTERNAL_SERVER_ERROR_EXP_ERR_TITLE_CODE = "internal.server.err.title";

    private static final String EXPORT_ERR_MSG_CODE = "export.data.err.msg";

    private static final String IMPORT_OPERATION = "Import Role Assignment Data";

    private static final String IMPORT_INVALID_DATA_ERR_MSG_CODE = "import.invalid.data.err.msg";

    private static final String IMPORT_FILE_TOO_LARGE_ERR_MSG_CODE = "import.file.too.large.err.msg";

    private static final String IMPORT_FILE_INVALID_ERR_MSG_CODE = "import.file.invalid.err.msg";

    private static final String IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE = "import.duplicate.entry.err.msg";

    private static final String IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE = "import.data.not.found.err.msg";

    private static final String IMPORT_ACTION_CONFLICT_ERR_MSG_CODE = "import.action.conflict.err.msg";

    private static final String IMPORT_CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE = "import.career.pathway.outside.err.msg";

    private static final String IMPORT_OK = "data.import.ok.msg";

    private static final String YES = "Yes";

    private static final String INACTIVE = "Inactive";

    private static final int MAX_FILE_SIZE_IN_MB = 5;

    private static final String ACCEPTED_IMPORT_FILE_TYPE = ".xlsx";

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @GetMapping
    public ResponseEntity<?> getAllStaffs() {
        return ResponseEntity.ok(this.staffService.findAllByIsDeletedIsFalse());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStaffById(@PathVariable UUID id) {
        return ResponseEntity.ok(this.staffService.findById(id));
    }

    @GetMapping("/direct-down-line/{id}")
    public ResponseEntity<?> getDirectDownLineByManagerId(@PathVariable UUID id) {
        return ResponseEntity.ok(staffService.findAllByIsDeletedIsFalseAndManagerId(id));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_COMPETENCY.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_ROLE.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_PROPOSE_ROLE_COMPETENCIES.getAuthorityName()
            )
            """)
    @GetMapping("/by-authority-name")
    public ResponseEntity<?> getStaffByAuthority(@RequestParam List<String> authorityNames) {
        if (authorityNames.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{STAFF_QUERY_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        Set<AuthorityName> authorities = authorityNames.stream().map(authorityName ->
                        AuthorityName.fromAuthority(authorityName).orElseThrow(() -> {
                            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE,
                                    new String[]{STAFF_QUERY_OPERATION}, Locale.getDefault());
                            return new BadRequestException(errorMessage);
                        }))
                .collect(Collectors.toSet());

        List<Long> authorityIds = authorityService.findAllByNameIn(authorities).stream()
                .map(AuthorityDto::getId).toList();

        if (authorityIds.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE,
                    new String[]{STAFF_QUERY_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        Set<Long> roleIds = roleAuthorityService.getAll().stream()
                .filter(roleAuthority -> authorityIds.contains(roleAuthority.getId().getAuthorityId()))
                .map(roleAuthority -> roleAuthority.getId().getRoleId())
                .collect(Collectors.toSet());

        List<StaffDto> staffDtoList = staffService.findAllByRoleIdIn(roleIds);

        return ResponseEntity.ok(staffDtoList);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_VIEW_STAFF.getAuthorityName(),
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @GetMapping("/overview")
    public ResponseEntity<?> getStaffOverview(Authentication authentication) {

        List<StaffOverviewDto> result = new ArrayList<>();
        if (authentication.getAuthorities().contains(new SimpleGrantedAuthority(AuthorityName.CAN_MANAGE_STAFF.getAuthorityName())) ||
                authentication.getAuthorities().contains(new SimpleGrantedAuthority(AuthorityName.CAN_VIEW_STAFF.getAuthorityName()))) {
            // get access to see all staff profiles

            result = staffService.findAllByIsDeletedIsFalse().stream().map(dto ->
                            new StaffOverviewDto(
                                    dto.getId(),
                                    dto.getName(),
                                    dto.getEmail(),
                                    dto.getRole() == null ? null : dto.getRole().getOrgChart().getId(),
                                    dto.getRole() == null ? null : dto.getRole().getOrgChart().getName(),
                                    dto.getRole() != null && dto.getRole().getOrgChart().isDeleted(),
                                    dto.getRole() == null ? null : dto.getRole().getId(),
                                    dto.getRole() == null ? null : dto.getRole().getName(),
                                    dto.getRole() != null && dto.getRole().isDeleted(),
                                    dto.getCareerPathway() == null ? null : dto.getCareerPathway().getId(),
                                    dto.getCareerPathway() == null ? null : dto.getCareerPathway().getName(),
                                    dto.getCareerPathway() != null && dto.getCareerPathway().isDeleted(),
                                    dto.getManager() == null ? null : dto.getManager().getId(),
                                    dto.getManager() == null ? null : dto.getManager().getName(),
                                    dto.getManager() == null ? null : dto.getManager().getEmail(),
                                    dto.getManager() != null && dto.getManager().isDeleted(),
                                    dto.getAccountStatus() == StaffAccountStatus.ACTIVE
                            ))
                    .toList();
        } else if (authentication.getAuthorities().contains(new SimpleGrantedAuthority(AuthorityName.ROLE_USER.getAuthorityName()))) {
            // limited access to view direct report & all descendant department staffs
            String userId = authentication.getName();
            UUID userUUID = UUID.fromString(userId);

            List<StaffDto> allStaffs = staffService.findAllByIsDeletedIsFalse();
            StaffDto user = Objects.requireNonNull(allStaffs.stream().filter(dto ->
                    Objects.equals(dto.getId(), userUUID)).findFirst().orElse(null));
            Long userOrgChartId = user.getRole().getOrgChart().getId();

            List<ParentChildNodeDto> relations = parentChildNodeService.findAllByRelationType(RelationType.ORG_CHART);
            Set<Long> allDescendantIds = findAllDescendants(userOrgChartId, relations);

            Set<StaffDto> allDescendantsFromChildrenDept = allStaffs.stream().filter(dto ->
                            dto.getRole() != null && allDescendantIds.contains(dto.getRole().getOrgChart().getId()))
                    .collect(Collectors.toSet());

            Map<UUID, List<StaffDto>> staffByManagerMap = allStaffs.stream()
                    .filter(s -> s.getManager() != null)
                    .collect(Collectors.groupingBy(s -> s.getManager().getId()));

            Set<StaffDto> allDescendantsWithinSameDepartment = getAllSubordinates(userUUID, staffByManagerMap);

            Set<StaffDto> visibleStaff = new HashSet<>(allDescendantsFromChildrenDept);
            visibleStaff.addAll(allDescendantsWithinSameDepartment);
            visibleStaff.add(user);

            result = visibleStaff.stream().map(dto ->
                            new StaffOverviewDto(
                                    dto.getId(),
                                    dto.getName(),
                                    dto.getEmail(),
                                    dto.getRole() == null ? null : dto.getRole().getOrgChart().getId(),
                                    dto.getRole() == null ? null : dto.getRole().getOrgChart().getName(),
                                    dto.getRole() != null && dto.getRole().getOrgChart().isDeleted(),
                                    dto.getRole() == null ? null : dto.getRole().getId(),
                                    dto.getRole() == null ? null : dto.getRole().getName(),
                                    dto.getRole() != null && dto.getRole().isDeleted(),
                                    dto.getCareerPathway() == null ? null : dto.getCareerPathway().getId(),
                                    dto.getCareerPathway() == null ? null : dto.getCareerPathway().getName(),
                                    dto.getCareerPathway() != null && dto.getCareerPathway().isDeleted(),
                                    dto.getManager() == null ? null : dto.getManager().getId(),
                                    dto.getManager() == null ? null : dto.getManager().getName(),
                                    dto.getManager() == null ? null : dto.getManager().getEmail(),
                                    dto.getManager() != null && dto.getManager().isDeleted(),
                                    dto.getAccountStatus() == StaffAccountStatus.ACTIVE
                            ))
                    .toList();

        }

        return ResponseEntity.ok(result);
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).ROLE_USER.getAuthorityName()
            )
            """)
    @GetMapping("/all-descendants")
    public ResponseEntity<?> getAllDescendants(Authentication authentication) {
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        List<StaffDto> allStaffs = staffService.findAllByIsDeletedIsFalse();
        StaffDto user = Objects.requireNonNull(allStaffs.stream().filter(dto ->
                Objects.equals(dto.getId(), userUUID)).findFirst().orElse(null));
        Long userOrgChartId = user.getRole().getOrgChart().getId();

        List<ParentChildNodeDto> relations = parentChildNodeService.findAllByRelationType(RelationType.ORG_CHART);
        Set<Long> allDescendantIds = findAllDescendants(userOrgChartId, relations);

        Set<StaffDto> allDescendantsFromChildrenDept = allStaffs.stream().filter(dto ->
                        dto.getRole() != null && allDescendantIds.contains(dto.getRole().getOrgChart().getId()))
                .collect(Collectors.toSet());

        Map<UUID, List<StaffDto>> staffByManagerMap = allStaffs.stream()
                .filter(s -> s.getManager() != null)
                .collect(Collectors.groupingBy(s -> s.getManager().getId()));

        Set<StaffDto> allDescendantsWithinSameDepartment = getAllSubordinates(userUUID, staffByManagerMap);

        Set<StaffDto> visibleStaff = new HashSet<>(allDescendantsFromChildrenDept);
        visibleStaff.addAll(allDescendantsWithinSameDepartment);
        visibleStaff.add(user);

        return ResponseEntity.ok(visibleStaff);
    }


    public Set<StaffDto> getAllSubordinates(UUID targetManagerId, Map<UUID, List<StaffDto>> staffByManagerMap) {
        Set<StaffDto> subordinates = new HashSet<>();

        List<StaffDto> directReports = staffByManagerMap.get(targetManagerId);

        if (directReports != null) {
            for (StaffDto report : directReports) {
                subordinates.add(report);

                // find the reports of this report
                subordinates.addAll(getAllSubordinates(report.getId(), staffByManagerMap));
            }
        }

        return subordinates;
    }

    private Set<Long> findAllDescendants(Long parentId, List<ParentChildNodeDto> relations) {
        Set<Long> descendants = new HashSet<>();

        List<Long> directChildren = relations.stream()
                .filter(r -> Objects.equals(r.getParentId(), parentId))
                .map(ParentChildNodeDto::getChildId)
                .toList();

        for (Long childId : directChildren) {
            descendants.add(childId);
            descendants.addAll(findAllDescendants(childId, relations));
        }

        return descendants;
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @GetMapping("/add-staff")
    public ResponseEntity<?> getDataBeforeAddStaff(Authentication authentication) {
        List<OrgChartRoleDto> orgChartRoleDtoList = new ArrayList<>();

        List<RoleDto> roleDtoList = roleService.getAllByDeletedIsFalse();

        roleDtoList.forEach(roleDto -> {
            OrgChartRoleDto orgChartRoleDto = new OrgChartRoleDto();
            orgChartRoleDto.setOrgChartId(roleDto.getOrgChart().getId());
            orgChartRoleDto.setOrgChartName(roleDto.getOrgChart().getName());
            orgChartRoleDto.setRoleId(roleDto.getId());
            orgChartRoleDto.setRoleName(roleDto.getName());

            orgChartRoleDtoList.add(orgChartRoleDto);
        });

        List<OrgChartCareerPathwayDto> orgChartCareerPathwayDtoList = new ArrayList<>();
        List<CareerPathwayRoleDto> allRelations = careerPathwayRoleService.getAll();

        List<CareerPathwayDto> careerPathwayDtoList = careerPathwayService.getAllByIsDeletedIsFalse();

        careerPathwayDtoList.forEach(careerPathwayDto -> {
            List<CareerPathwayRoleDto> relations = allRelations.stream().filter(dto ->
                            dto.getCareerPathway().getId().equals(careerPathwayDto.getId()))
                    .toList();
            Set<Long> childrenIds = relations.stream().map(dto -> dto.getId().getChildId())
                    .collect(Collectors.toSet());
            childrenIds.addAll(relations.stream().map(dto -> dto.getId().getParentId())
                    .collect(Collectors.toSet()));
            childrenIds.add(careerPathwayDto.getRootRole().getId());

            OrgChartCareerPathwayDto orgChartCareerPathwayDto = new OrgChartCareerPathwayDto(
                    careerPathwayDto.getOrgChart().getId(),
                    careerPathwayDto.getOrgChart().getName(),
                    careerPathwayDto.getId(),
                    careerPathwayDto.getName(),
                    childrenIds
            );

            orgChartCareerPathwayDtoList.add(orgChartCareerPathwayDto);
        });

        return ResponseEntity.ok(Map.of(
                "roles", orgChartRoleDtoList,
                "careerPathways", orgChartCareerPathwayDtoList
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> registerAccount(@RequestBody RegisterAccountRequest req,
                                             Authentication authentication) throws Exception {
        if (validationService.isNullOrBlank(req.getEmail())) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{REGISTER_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String email = req.getEmail().trim().toLowerCase();

        Optional<StaffDto> existedEmail = this.staffService.findByIsDeletedIsFalseAndEmail(email);

        if (existedEmail.isPresent()) {
            String errorTitle = messageSource.getMessage(EMAIL_REGISTERED_ERR_TITLE_CODE, null, Locale.getDefault());
            String errorMessage = messageSource.getMessage(EMAIL_REGISTERED_ERR_MSG_CODE, null, Locale.getDefault());

            throw new BadRequestException(errorTitle, errorMessage);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8));
        UUID userId = UUID.randomUUID();

        String adminId = authentication.getName();
        UUID adminUUID = UUID.fromString(adminId);

        StaffDto newStaff = new StaffDto();
        newStaff.setId(userId);
        newStaff.setEmail(email);

        if (!validationService.isNullOrBlank(req.getName())) {
            newStaff.setName(req.getName());
        }

        if (req.getRoleId() != null) {
            newStaff.setRole(roleService.getAllById(req.getRoleId()));
        }

        if (req.getCareerPathwayId() != null) {
            if (req.getRoleId() == null) {
                String errorTitle = messageSource.getMessage(UNABLE_ASSIGN_CAREER_PATHWAY_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(UNABLE_ASSIGN_CAREER_PATHWAY_ERR_MSG_CODE, null, Locale.getDefault());

                throw new BadRequestException(errorTitle, errorMessage);
            }

            CareerPathwayDto selectedCareerPathwayDto = careerPathwayService.getById(req.getCareerPathwayId());
            List<CareerPathwayRoleDto> roleWithinCareerPathway = careerPathwayRoleService.getAllByCareerPathwayId(req.getCareerPathwayId());
            Set<Long> roleIdsWithinCareerPathway = roleWithinCareerPathway.stream()
                    .map(dto -> dto.getId().getParentId()).collect(Collectors.toSet());
            roleIdsWithinCareerPathway.addAll(roleWithinCareerPathway.stream()
                    .map(dto -> dto.getId().getChildId()).collect(Collectors.toSet()));
            roleIdsWithinCareerPathway.add(selectedCareerPathwayDto.getRootRole().getId());

            if (!roleIdsWithinCareerPathway.contains(req.getRoleId())) {
                String errorMessage = messageSource.getMessage(CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE, null, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            newStaff.setCareerPathway(selectedCareerPathwayDto);
        }

        if (req.getManagerId() != null) {
            StaffDto managerDto = this.staffService.findById(req.getManagerId());

            newStaff.setManager(managerDto);
        }

        newStaff.setFirstLogin(true);
        newStaff.setCreatedAt(now);
        newStaff.setCreatedBy(adminUUID);
        newStaff.setUpdatedAt(now);
        newStaff.setUpdatedBy(adminUUID);
        newStaff.setAccountStatus(StaffAccountStatus.ACTIVE);

        StaffDto createdStaff = staffService.create(newStaff);

        StaffProfileDto newProfile = new StaffProfileDto(
                createdStaff.getId(),
                createdStaff,
                null,
                null,
                null,
                adminUUID,
                now,
                adminUUID,
                now
        );
        staffProfileService.create(newProfile);

        emailService.sendAccountRegisteredEmail(email, Locale.getDefault());

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(ACCOUNT_REGISTER_OK, null, Locale.getDefault()
                )));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @PutMapping("/edit-staff")
    @Transactional
    public ResponseEntity<?> updateStaff(@RequestBody EditStaffRequestDto requestDto, Authentication authentication) throws Exception {
        if (requestDto == null || requestDto.getStaffId() == null || validationService.isNullOrBlank(requestDto.getEmail())) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{STAFF_EDIT_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        UUID staffId = requestDto.getStaffId();

        StaffDto staffDto = staffService.findById(staffId);

        String email = requestDto.getEmail().toLowerCase().trim();

        if (!email.equalsIgnoreCase(staffDto.getEmail())) {
            staffDto.setFirstLogin(true);
            emailService.sendAccountRegisteredEmail(email, Locale.getDefault());
        }
        staffDto.setEmail(email);

        if (!validationService.isNullOrBlank(requestDto.getName())) {
            staffDto.setName(requestDto.getName());
        } else {
            staffDto.setName(null);
        }

        if (requestDto.getRoleId() != null) {
            staffDto.setRole(roleService.getAllById(requestDto.getRoleId()));
        } else {
            staffDto.setRole(null);
        }

        if (requestDto.getCareerPathwayId() != null) {
            if (requestDto.getRoleId() == null) {
                String errorTitle = messageSource.getMessage(UNABLE_ASSIGN_CAREER_PATHWAY_ERR_TITLE_CODE, null, Locale.getDefault());
                String errorMessage = messageSource.getMessage(UNABLE_ASSIGN_CAREER_PATHWAY_ERR_MSG_CODE, null, Locale.getDefault());

                throw new BadRequestException(errorTitle, errorMessage);
            }

            CareerPathwayDto selectedCareerPathwayDto = careerPathwayService.getById(requestDto.getCareerPathwayId());
            List<CareerPathwayRoleDto> roleWithinCareerPathway = careerPathwayRoleService.getAllByCareerPathwayId(requestDto.getCareerPathwayId());
            Set<Long> roleIdsWithinCareerPathway = roleWithinCareerPathway.stream()
                    .map(dto -> dto.getId().getParentId()).collect(Collectors.toSet());
            roleIdsWithinCareerPathway.addAll(roleWithinCareerPathway.stream()
                    .map(dto -> dto.getId().getChildId()).collect(Collectors.toSet()));
            roleIdsWithinCareerPathway.add(selectedCareerPathwayDto.getRootRole().getId());

            if (!roleIdsWithinCareerPathway.contains(requestDto.getRoleId())) {
                String errorMessage = messageSource.getMessage(CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE, null, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            staffDto.setCareerPathway(selectedCareerPathwayDto);
        } else {
            staffDto.setCareerPathway(null);
        }

        if (requestDto.getManagerId() != null) {
            StaffDto managerDto = this.staffService.findById(requestDto.getManagerId());

            staffDto.setManager(managerDto);
        } else {
            staffDto.setManager(null);
        }

        if (requestDto.isAccountStatus()) {
            staffDto.setAccountStatus(StaffAccountStatus.ACTIVE);
        } else {
            staffDto.setAccountStatus(StaffAccountStatus.INACTIVE);
        }

        staffDto.setUpdatedBy(userUUID);
        staffDto.setUpdatedAt(OffsetDateTime.now());

        staffService.update(staffId, staffDto);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(STAFF_EDIT_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @PutMapping("/toggle-account-status")
    @Transactional
    public ResponseEntity<?> toggleAccountStatus(@RequestBody ToggleAccountStatusRequest request, Authentication authentication) {
        if (request == null || request.getStaffId() == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{TOGGLE_ACCOUNT_STATUS_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        UUID staffId = request.getStaffId();
        boolean accountStatus = request.isAccountStatus();

        StaffDto staffDto = staffService.findById(staffId);
        if (staffDto == null) {
            String errorMessage = messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault());
            throw new DataAccessException(errorMessage);
        }
        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);
        staffDto.setAccountStatus(accountStatus ? StaffAccountStatus.ACTIVE : StaffAccountStatus.INACTIVE);

        if (accountStatus) {
            Optional<StaffLoginAuditDto> staffLoginAuditDto = staffLoginAuditService.findStaffLoginAuditById(staffId);
            if (staffLoginAuditDto.isPresent()) {
                staffLoginAuditDto.get().setLoginFailedAttempts(0);
                staffLoginAuditDto.get().setForgotPasswordAttempts(0);

                staffLoginAuditService.updateStaffLoginAudit(staffId, staffLoginAuditDto.get());
            }
        }

        staffDto.setUpdatedBy(userUUID);
        staffDto.setUpdatedAt(OffsetDateTime.now());

        staffService.update(staffId, staffDto);
        return ResponseEntity.ok(Map.of("message", messageSource.getMessage(TOGGLE_ACCOUNT_STATUS_OK, null, Locale.getDefault())));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @DeleteMapping("/delete")
    @Transactional
    public ResponseEntity<?> delete(
            @RequestParam UUID staffId, Authentication authentication) throws IOException {
        if (staffId == null) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{STAFF_REMOVE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        //delete profile
        staffProfileService.delete(staffId);

        //delete self declared skill
        staffSelfDeclaredSkillService.deleteAllByStaffId(staffId);

        //delete cert
        staffCertService.deleteAllByStaffId(staffId);

        //delete profile directory
        deleteProfileDirectory(staffId);

        //delete staff
        staffService.delete(staffId, userUUID);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(STAFF_REMOVE_OK, null, Locale.getDefault())
        ));
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @DeleteMapping("/bulk-delete")
    @Transactional
    public ResponseEntity<?> bulkDeleteRole(
            @RequestParam List<UUID> staffIds, Authentication authentication) throws IOException {
        if (staffIds == null || staffIds.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_REQUEST_BODY_ERR_MSG_CODE, new String[]{STAFF_REMOVE_OPERATION}, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        String userId = authentication.getName();
        UUID userUUID = UUID.fromString(userId);

        Set<UUID> staffIdsToRemove = new HashSet<>(staffIds);

        //delete profile
        staffProfileService.deleteAllById(staffIdsToRemove);

        //delete self declared skill
        staffSelfDeclaredSkillService.deleteAllByStaffIdIn(staffIdsToRemove);

        //delete cert
        staffCertService.deleteAllByStaffIdIn(staffIdsToRemove);

        //delete profile directory
        for (UUID staffId : staffIdsToRemove) {
            deleteProfileDirectory(staffId);
        }

        //delete staffs
        staffService.deleteAllById(staffIdsToRemove, userUUID);

        return ResponseEntity.ok(Map.of(
                "message", messageSource.getMessage(STAFF_REMOVE_OK, null, Locale.getDefault())
        ));
    }

    private void deleteProfileDirectory(UUID staffId) throws IOException {
        Path profileDir = Paths.get(UPLOAD_DIR, PROFILES_DIR, staffId.toString());
        if (Files.exists(profileDir)) {
            var paths = Files.walk(profileDir);
            paths.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @PreAuthorize("""
            hasAnyAuthority(
                T(com.tbm.careerpathlearning.enums.AuthorityName).CAN_MANAGE_STAFF.getAuthorityName()
            )
            """)
    @GetMapping("/export")
    public ResponseEntity<?> exportStaffAccountData(Authentication authentication) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sheet 1");
            XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
            int rowIndex = 0;

            // -- header
            Row header = sheet.createRow(rowIndex);

            Cell cellDesc = header.createCell(0);
            cellDesc.setCellValue(STAFF_EMAIL_COLUMN);
            createCellComment(drawing, cellDesc, messageSource.getMessage(STAFF_EMAIL_NOTES, null, Locale.getDefault()));

            Cell cellName = header.createCell(1);
            cellName.setCellValue(STAFF_NAME_COLUMN);
            createCellComment(drawing, cellName, messageSource.getMessage(STAFF_NAME_NOTES, null, Locale.getDefault()));

            Cell cellDeptName = header.createCell(2);
            cellDeptName.setCellValue(DEPT_NAME_COLUMN);
            createCellComment(drawing, cellDeptName, messageSource.getMessage(DEPT_NAME_NOTES, null, Locale.getDefault()));

            Cell cellRoleName = header.createCell(3);
            cellRoleName.setCellValue(ROLE_NAME_COLUMN);
            createCellComment(drawing, cellRoleName, messageSource.getMessage(ROLE_NAME_NOTES, null, Locale.getDefault()));

            Cell cellCareerPathwayName = header.createCell(4);
            cellCareerPathwayName.setCellValue(CAREER_PATHWAY_NAME_COLUMN);
            createCellComment(drawing, cellCareerPathwayName, messageSource.getMessage(CAREER_PATHWAY_NAME_NOTES, null, Locale.getDefault()));

            Cell cellManagerEmail = header.createCell(5);
            cellManagerEmail.setCellValue(MANAGER_EMAIL_COLUMN);
            createCellComment(drawing, cellManagerEmail, messageSource.getMessage(MANAGER_EMAIL_NOTES, null, Locale.getDefault()));

            Cell cellAccountStatus = header.createCell(6);
            cellAccountStatus.setCellValue(ACCOUNT_STATUS_COLUMN);
            createCellComment(drawing, cellAccountStatus, messageSource.getMessage(ACCOUNT_STATUS_NOTES, null, Locale.getDefault()));

            Cell cellNewEmail = header.createCell(7);
            cellNewEmail.setCellValue(NEW_STAFF_EMAIL_COLUMN);
            createCellComment(drawing, cellNewEmail, messageSource.getMessage(NEW_STAFF_EMAIL_NOTES, null, Locale.getDefault()));

            Cell cellDeleted = header.createCell(8);
            cellDeleted.setCellValue(TO_BE_DELETED_COLUMN);
            createCellComment(drawing, cellDeleted, messageSource.getMessage(TO_BE_DELETED_NOTES, null, Locale.getDefault()));

            rowIndex++;

            List<StaffDto> allStaff = staffService.findAllByIsDeletedIsFalse();

            for (StaffDto dto : allStaff) {
                Row row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue(dto.getEmail());
                row.createCell(1).setCellValue(dto.getName());
                row.createCell(2).setCellValue(dto.getRole() == null ? null : dto.getRole().getOrgChart().getName());
                row.createCell(3).setCellValue(dto.getRole() == null ? null : dto.getRole().getName());
                row.createCell(4).setCellValue(dto.getCareerPathway() == null ? null : dto.getCareerPathway().getName());
                row.createCell(5).setCellValue(dto.getManager() == null ? null : dto.getManager().getEmail());
                row.createCell(6).setCellValue(Objects.equals(dto.getAccountStatus(), StaffAccountStatus.ACTIVE) ? null : INACTIVE);

                rowIndex++;
            }

            workbook.write(out);
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray());


            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Exported_Staff_Account_Data.xlsx");
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
        Map<String, Set<String>> allDepartmentRoleNameMap = allDepartment.stream().collect(Collectors.toMap(
                dto -> dto.getName().trim().toLowerCase(),
                dto -> allRoles.stream().filter(role ->
                                Objects.equals(role.getOrgChart().getId(), dto.getId()))
                        .map(role -> role.getName().toLowerCase().trim())
                        .collect(Collectors.toSet())));

        List<CareerPathwayDto> allCareerPathway = careerPathwayService.getAllByIsDeletedIsFalse();

        List<CareerPathwayRoleDto> allRelations = careerPathwayRoleService.getAll();
        Map<String, Map<String, Set<String>>> allCareerPathwayRoleNameMap = allDepartment.stream().collect(Collectors.toMap(
                dto -> dto.getName().trim().toLowerCase(),
                dto ->
                        allCareerPathway.stream().filter(careerPathway ->
                                        Objects.equals(dto.getId(), careerPathway.getOrgChart().getId()))
                                .collect(Collectors.toMap(
                                        careerPathway -> careerPathway.getName().toLowerCase().trim(),
                                        careerPathway -> {
                                            List<CareerPathwayRoleDto> assignedNode = allRelations.stream().filter(rel ->
                                                            Objects.equals(rel.getId().getCareerPathwayId(), careerPathway.getId()))
                                                    .toList();

                                            Set<String> assignedRoleName = assignedNode.stream().map(rel ->
                                                    rel.getParentRole().getName().toLowerCase().trim()).collect(Collectors.toSet());
                                            assignedRoleName.addAll(assignedNode.stream().map(rel ->
                                                    rel.getChildRole().getName().toLowerCase().trim()).collect(Collectors.toSet()));
                                            assignedRoleName.add(careerPathway.getRootRole().getName().toLowerCase().trim());

                                            return assignedRoleName;
                                        }
                                ))
        ));

        Map<String, StaffDto> allEmailStaffMap = staffService.findAllByIsDeletedIsFalse().stream()
                .collect(Collectors.toMap(
                        dto -> dto.getEmail().trim().toLowerCase(),
                        Function.identity(),
                        (first, second) -> first
                ));

        Sheet sheet = workbook.getSheetAt(0);

        // -- check headers --
        Row header = sheet.getRow(0);
        if (!getCellValueAsString(header.getCell(0)).equalsIgnoreCase(STAFF_EMAIL_COLUMN)
                || !getCellValueAsString(header.getCell(1)).equalsIgnoreCase(STAFF_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(2)).equalsIgnoreCase(DEPT_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(3)).equalsIgnoreCase(ROLE_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(4)).equalsIgnoreCase(CAREER_PATHWAY_NAME_COLUMN)
                || !getCellValueAsString(header.getCell(5)).equalsIgnoreCase(MANAGER_EMAIL_COLUMN)
                || !getCellValueAsString(header.getCell(6)).equalsIgnoreCase(ACCOUNT_STATUS_COLUMN)
                || !getCellValueAsString(header.getCell(7)).equalsIgnoreCase(NEW_STAFF_EMAIL_COLUMN)
                || !getCellValueAsString(header.getCell(8)).equalsIgnoreCase(TO_BE_DELETED_COLUMN)
        ) {
            String errorMessage = messageSource.getMessage(IMPORT_FILE_INVALID_ERR_MSG_CODE, null, Locale.getDefault());
            throw new BadRequestException(errorMessage);
        }

        List<StaffDto> staffDtoToBeUpdated = new ArrayList<>();
        Set<UUID> newRegisteredStaffs = new HashSet<>();
        Set<String> emailUpdatedStaffs = new HashSet<>();
        Set<UUID> deletedStaffs = new HashSet<>();
        Set<String> duplicatedRow = new HashSet<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // skip header (row 0)
            Row row = sheet.getRow(i);
            if (row == null || row.getCell(0) == null || getCellValueAsString(row.getCell(0)) == null
                    || getCellValueAsString(row.getCell(0)).isBlank()) continue;

            // -- data --
            String staffEmail = getCellValueAsString(row.getCell(0));
            String staffName = getCellValueAsString(row.getCell(1));
            String departmentName = getCellValueAsString(row.getCell(2));
            String roleName = getCellValueAsString(row.getCell(3));
            String careerPathwayName = getCellValueAsString(row.getCell(4));
            String managerEmail = getCellValueAsString(row.getCell(5));
            String accountStatus = getCellValueAsString(row.getCell(6));
            String newEmail = getCellValueAsString(row.getCell(7));
            String toBeDeleted = getCellValueAsString(row.getCell(8));

            if (validationService.isNullOrBlank(staffEmail) || staffEmail.length() > 255) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{STAFF_EMAIL_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }
            staffEmail = staffEmail.trim().toLowerCase();

            if (!validationService.isNullOrBlank(staffName) && staffName.length() > 255) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{STAFF_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(departmentName) && !allDepartmentRoleNameMap.containsKey(departmentName.trim().toLowerCase())) {
                String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{departmentName}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(roleName)
                    && validationService.isNullOrBlank(departmentName)) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE, new String[]{DEPT_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(roleName)
                    && !allDepartmentRoleNameMap.get(departmentName.trim().toLowerCase()).contains(roleName.trim().toLowerCase())) {
                String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{roleName}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(careerPathwayName)
                    && validationService.isNullOrBlank(departmentName)) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE, new String[]{DEPT_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(careerPathwayName)
                    && validationService.isNullOrBlank(roleName)) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE, new String[]{ROLE_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(careerPathwayName)
                    && !allCareerPathwayRoleNameMap.get(departmentName.trim().toLowerCase()).containsKey(careerPathwayName.trim().toLowerCase())) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{CAREER_PATHWAY_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(managerEmail)
                    && !allEmailStaffMap.containsKey(managerEmail.trim().toLowerCase())) {
                String errorMessage = messageSource.getMessage(IMPORT_DATA_NOT_FOUND_ERR_MSG_CODE, new String[]{managerEmail}, Locale.getDefault());
                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(newEmail) && newEmail.length() > 255) {
                String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                        new String[]{NEW_STAFF_EMAIL_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (!validationService.isNullOrBlank(toBeDeleted) && toBeDeleted.equalsIgnoreCase(YES) &&
                    (!validationService.isNullOrBlank(newEmail))) { // perform deletion and update at the same time
                String errorMessage = messageSource.getMessage(IMPORT_ACTION_CONFLICT_ERR_MSG_CODE,
                        new String[]{Integer.toString(i + 1)},
                        Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }

            if (duplicatedRow.contains(staffEmail)) {
                String errorMessage = messageSource.getMessage(IMPORT_DUPLICATE_ENTRY_ERR_MSG_CODE, new String[]{Integer.toString(i + 1)}, Locale.getDefault());

                throw new BadRequestException(errorMessage);
            }
            duplicatedRow.add(staffEmail);

            RoleDto assignedRole;
            if (!validationService.isNullOrBlank(roleName)) {
                assignedRole = Objects.requireNonNull(allRoles.stream().filter(role ->
                                role.getName().equalsIgnoreCase(roleName)
                                        && role.getOrgChart().getName().equalsIgnoreCase(departmentName))
                        .findFirst().orElse(null));
            } else {
                assignedRole = null;
            }

            CareerPathwayDto assignedCareerPathway;
            if (!validationService.isNullOrBlank(careerPathwayName)) {
                if (assignedRole == null) {
                    String errorMessage = messageSource.getMessage(IMPORT_CAREER_PATHWAY_OUTSIDE_ERR_MSG_CODE,
                            new String[]{Integer.toString(i + 1)}, Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                if (!allCareerPathwayRoleNameMap.get(departmentName.toLowerCase().trim())
                        .get(careerPathwayName.toLowerCase().trim())
                        .contains(assignedRole.getName().toLowerCase().trim())) {
                    String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                            new String[]{CAREER_PATHWAY_NAME_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                assignedCareerPathway = Objects.requireNonNull(allCareerPathway.stream().filter(careerPathway ->
                                careerPathway.getName().equalsIgnoreCase(careerPathwayName)
                                        && careerPathway.getOrgChart().getName().equalsIgnoreCase(departmentName))
                        .findFirst().orElse(null));
            } else {
                assignedCareerPathway = null;
            }

            StaffDto assignedManager;
            if (!validationService.isNullOrBlank(managerEmail)) {
                assignedManager = Objects.requireNonNull(
                        allEmailStaffMap.values().stream().filter(staff ->
                                        staff.getEmail().equalsIgnoreCase(managerEmail.trim()))
                                .findFirst().orElse(null)
                );
            } else {
                assignedManager = null;
            }

            StaffDto selectedStaff;
            if (allEmailStaffMap.containsKey(staffEmail)) { // update existing email
                selectedStaff = Objects.requireNonNull(allEmailStaffMap.get(staffEmail));

                if (!validationService.isNullOrBlank(toBeDeleted) && toBeDeleted.equalsIgnoreCase(YES)) {
                    selectedStaff.setDeleted(true);

                    allEmailStaffMap.remove(staffEmail);
                    staffDtoToBeUpdated.add(selectedStaff);
                    deletedStaffs.add(selectedStaff.getId());
                    continue;
                }

                if (!validationService.isNullOrBlank(newEmail)) {
                    if (allEmailStaffMap.containsKey(newEmail.trim().toLowerCase())) {
                        String errorMessage = messageSource.getMessage(IMPORT_INVALID_DATA_ERR_MSG_CODE,
                                new String[]{NEW_STAFF_EMAIL_COLUMN, Integer.toString(i + 1)}, Locale.getDefault());

                        throw new BadRequestException(errorMessage);
                    }

                    selectedStaff.setEmail(newEmail.trim().toLowerCase());
                    selectedStaff.setFirstLogin(true);
                    emailUpdatedStaffs.add(selectedStaff.getEmail());

                    allEmailStaffMap.remove(staffEmail);
                    allEmailStaffMap.put(newEmail.trim().toLowerCase(), selectedStaff);
                }

                selectedStaff.setName(staffName.trim());
                selectedStaff.setRole(assignedRole);
                selectedStaff.setCareerPathway(assignedCareerPathway);
                selectedStaff.setManager(assignedManager);

                if (!validationService.isNullOrBlank(accountStatus) && accountStatus.equalsIgnoreCase(INACTIVE)) {
                    selectedStaff.setAccountStatus(StaffAccountStatus.INACTIVE);
                }
            } else { // register new account
                if ((!validationService.isNullOrBlank(toBeDeleted) && toBeDeleted.equalsIgnoreCase(YES)) ||
                        !validationService.isNullOrBlank(newEmail)) { // perform deletion and update at the same time
                    String errorMessage = messageSource.getMessage(IMPORT_ACTION_CONFLICT_ERR_MSG_CODE,
                            new String[]{Integer.toString(i + 1)},
                            Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                selectedStaff = new StaffDto(
                        UUID.randomUUID(),
                        staffName.trim(),
                        staffEmail,
                        assignedRole,
                        assignedCareerPathway,
                        assignedManager,
                        true,
                        !validationService.isNullOrBlank(accountStatus) && accountStatus.equalsIgnoreCase(INACTIVE) ?
                                StaffAccountStatus.INACTIVE : StaffAccountStatus.ACTIVE,
                        false,
                        userUUID,
                        now,
                        userUUID,
                        now
                );

                newRegisteredStaffs.add(selectedStaff.getId());
            }

            staffDtoToBeUpdated.add(selectedStaff);
        }

        Map<UUID, StaffDto> updatedStaffEmailMap;
        if (!staffDtoToBeUpdated.isEmpty()) {
            updatedStaffEmailMap = staffService.updateAll(staffDtoToBeUpdated).stream().collect(Collectors.toMap(
                    StaffDto::getId,
                    Function.identity()
            ));
        } else {
            updatedStaffEmailMap = Collections.emptyMap();
        }

        if (!emailUpdatedStaffs.isEmpty()) {
            emailUpdatedStaffs.forEach(email -> {
                emailService.sendAccountRegisteredEmail(email, Locale.getDefault());
            });
        }

        if (!newRegisteredStaffs.isEmpty()) {
            List<StaffProfileDto> newProfiles = new ArrayList<>();

            newRegisteredStaffs.forEach(id -> {
                StaffDto createdStaff = Objects.requireNonNull(
                        updatedStaffEmailMap.get(id)
                );
                emailService.sendAccountRegisteredEmail(createdStaff.getEmail(), Locale.getDefault());

                StaffProfileDto newProfile = new StaffProfileDto(
                        createdStaff.getId(),
                        createdStaff,
                        null,
                        null,
                        null,
                        userUUID,
                        now,
                        userUUID,
                        now
                );
                newProfiles.add(newProfile);
            });

            staffProfileService.createAll(newProfiles);
        }

        if (!deletedStaffs.isEmpty()) {

            deletedStaffs.forEach(id -> {
                try {
                    deleteProfileDirectory(id);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            staffProfileService.deleteAllById(deletedStaffs);

            staffSelfDeclaredSkillService.deleteAllByStaffIdIn(deletedStaffs);

            staffCertService.deleteAllByStaffIdIn(deletedStaffs);

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