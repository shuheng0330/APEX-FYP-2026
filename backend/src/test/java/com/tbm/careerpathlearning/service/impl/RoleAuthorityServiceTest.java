package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleAuthority;
import com.tbm.careerpathlearning.model.RoleAuthorityId;
import com.tbm.careerpathlearning.repository.RoleAuthorityRepository;
import com.tbm.careerpathlearning.service.AuthorityService;
import com.tbm.careerpathlearning.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleAuthorityServiceTest {

    @Mock
    private RoleAuthorityRepository roleAuthorityRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private AuthorityService authorityService;

    @Mock
    private AppMapper appMapper;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private RoleAuthorityServiceImpl roleAuthorityService;

    // Test Data
    private RoleAuthorityId id;
    private RoleAuthority roleAuthorityEntity;
    private RoleAuthorityDto roleAuthorityDto;
    private RoleDto roleDto;
    private AuthorityDto authorityDto;

    private final Long ROLE_ID = 100L;
    private final Long AUTH_ID = 5L;

    @BeforeEach
    void setUp() {
        id = new RoleAuthorityId(ROLE_ID, AUTH_ID);

        // Role DTO
        roleDto = new RoleDto();
        roleDto.setId(ROLE_ID);
        roleDto.setName("Manager");

        // Authority DTO
        authorityDto = new AuthorityDto();
        authorityDto.setId(AUTH_ID);
        authorityDto.setName(com.tbm.careerpathlearning.enums.AuthorityName.values()[0]);

        // RoleAuthority Entity
        roleAuthorityEntity = new RoleAuthority();
        roleAuthorityEntity.setId(id);

        // RoleAuthority DTO
        roleAuthorityDto = new RoleAuthorityDto();
        roleAuthorityDto.setId(id);
        roleAuthorityDto.setRole(roleDto);
        roleAuthorityDto.setAuthority(authorityDto);
    }

    // --- getAll Tests ---

    @Test
    void getAll_ShouldReturnList() {
        when(roleAuthorityRepository.findAll()).thenReturn(List.of(roleAuthorityEntity));
        when(appMapper.toDto(roleAuthorityEntity)).thenReturn(roleAuthorityDto);

        List<RoleAuthorityDto> result = roleAuthorityService.getAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(id, result.get(0).getId());
        verify(roleAuthorityRepository).findAll();
    }

    // --- findById Tests ---

    @Test
    void findById_WhenFound_ShouldReturnOptional() {
        when(roleAuthorityRepository.findById(id)).thenReturn(Optional.of(roleAuthorityEntity));
        when(appMapper.toDto(roleAuthorityEntity)).thenReturn(roleAuthorityDto);

        Optional<RoleAuthorityDto> result = roleAuthorityService.findById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }

    @Test
    void findById_WhenNotFound_ShouldReturnEmpty() {
        when(roleAuthorityRepository.findById(id)).thenReturn(Optional.empty());

        Optional<RoleAuthorityDto> result = roleAuthorityService.findById(id);

        assertFalse(result.isPresent());
    }

    // --- create Tests ---

    @Test
    void create_WhenValid_ShouldReturnDto() {
        // Arrange
        when(roleService.getAllById(ROLE_ID)).thenReturn(roleDto);
        when(authorityService.findById(AUTH_ID)).thenReturn(authorityDto);
        // findById calls repository internally to check redundancy
        when(roleAuthorityRepository.findById(id)).thenReturn(Optional.empty());
        when(appMapper.toEntity(roleAuthorityDto)).thenReturn(roleAuthorityEntity);
        when(roleAuthorityRepository.save(roleAuthorityEntity)).thenReturn(roleAuthorityEntity);
        when(appMapper.toDto(roleAuthorityEntity)).thenReturn(roleAuthorityDto);

        // Act
        RoleAuthorityDto result = roleAuthorityService.create(roleAuthorityDto);

        // Assert
        assertNotNull(result);
        assertEquals(id, result.getId());
        verify(roleAuthorityRepository).save(roleAuthorityEntity);
    }

    @Test
    void create_WhenNullInput_ShouldThrowBadRequest() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Invalid Data");

        assertThrows(BadRequestException.class, () -> roleAuthorityService.create(null));
    }

    @Test
    void create_WhenRedundant_ShouldThrowException() {
        // Arrange
        when(roleService.getAllById(ROLE_ID)).thenReturn(roleDto);
        when(authorityService.findById(AUTH_ID)).thenReturn(authorityDto);
        // Simulate existing record
        when(roleAuthorityRepository.findById(id)).thenReturn(Optional.of(roleAuthorityEntity));
        when(appMapper.toDto(roleAuthorityEntity)).thenReturn(roleAuthorityDto); // Called by findById wrapper
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Redundant Error");

        // Act & Assert
        assertThrows(DataAccessException.class, () -> roleAuthorityService.create(roleAuthorityDto));
    }

    // --- createAll Tests ---

    @Test
    void createAll_WhenValid_ShouldReturnList() {
        // Arrange
        List<RoleAuthorityDto> dtoList = List.of(roleAuthorityDto);
        List<RoleAuthority> entityList = List.of(roleAuthorityEntity);

        // Mock existence checks
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(Set.of(ROLE_ID))).thenReturn(List.of(roleDto));
        when(authorityService.findAllByIdIn(Set.of(AUTH_ID))).thenReturn(List.of(authorityDto));
        when(roleAuthorityRepository.findAllById(anySet())).thenReturn(Collections.emptyList()); // No existing records

        // Mock mapping and saving
        when(appMapper.toEntity(roleAuthorityDto)).thenReturn(roleAuthorityEntity);
        when(roleAuthorityRepository.saveAll(anyList())).thenReturn(entityList);
        when(appMapper.toDto(roleAuthorityEntity)).thenReturn(roleAuthorityDto);

        // Act
        List<RoleAuthorityDto> result = roleAuthorityService.createAll(dtoList);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(roleAuthorityRepository).saveAll(anyList());
    }

    @Test
    void createAll_WhenRoleMissing_ShouldThrowException() {
        List<RoleAuthorityDto> dtoList = List.of(roleAuthorityDto);

        // Mock role NOT found
        when(roleService.getAllByIsDeletedIsFalseAndIdIn(anySet())).thenReturn(Collections.emptyList());
        when(authorityService.findAllByIdIn(anySet())).thenReturn(List.of(authorityDto));
        when(roleAuthorityRepository.findAllById(anySet())).thenReturn(Collections.emptyList());
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Not Found");

        assertThrows(DataAccessException.class, () -> roleAuthorityService.createAll(dtoList));
    }

    // --- getRoleAuthorityMapByRoleId Tests ---

    @Test
    void getRoleAuthorityMapByRoleId_ShouldReturnMap() {
        // Arrange
        // Authority 5 is assigned (TRUE), Authority 6 is unassigned (FALSE)
        AuthorityDto auth2 = new AuthorityDto(); auth2.setId(6L);

        when(authorityService.findAll()).thenReturn(List.of(authorityDto, auth2));
        when(roleAuthorityRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(roleAuthorityEntity));
        when(appMapper.toDto(roleAuthorityEntity)).thenReturn(roleAuthorityDto);

        // Act
        Map<Long, Boolean> result = roleAuthorityService.getRoleAuthorityMapByRoleId(ROLE_ID);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.get(AUTH_ID)); // Assigned
        assertFalse(result.get(6L)); // Not assigned
    }

    // --- Delete Tests ---

    @Test
    void deleteById_ShouldCallRepository() {
        roleAuthorityService.deleteById(id);
        verify(roleAuthorityRepository).deleteById(id);
    }

    @Test
    void deleteAllByIdIn_ShouldCallRepository() {
        Set<RoleAuthorityId> ids = Set.of(id);
        roleAuthorityService.deleteAllByIdIn(ids);
        verify(roleAuthorityRepository).deleteAllById(ids);
    }

    @Test
    void deleteAllByRoleIdIn_ShouldCallRepository() {
        Set<Long> roleIds = Set.of(ROLE_ID);
        roleAuthorityService.deleteAllByRoleIdIn(roleIds);
        verify(roleAuthorityRepository).deleteAllByRole_IdIn(roleIds);
    }

    @Test
    void deleteByRoleIdAndAuthorityIdIn_ShouldCallRepository() {
        Set<Long> authIds = Set.of(AUTH_ID);
        roleAuthorityService.deleteByRoleIdAndAuthorityIdIn(ROLE_ID, authIds);
        verify(roleAuthorityRepository).deleteAllByAuthority_IdInAndRole_Id(authIds, ROLE_ID);
    }
}