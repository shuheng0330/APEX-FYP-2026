package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CompetencyDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Competency;
import com.tbm.careerpathlearning.repository.CompetencyRepository;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetencyServiceImplTest {

    @Mock
    private CompetencyRepository competencyRepository;

    @Mock
    private AppMapper appMapper;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ValidationService validationService;

    @InjectMocks
    private CompetencyServiceImpl competencyService;

    private Competency mockCompetency;
    private CompetencyDto mockCompetencyDto;
    private UUID userUUID;
    private Long compId = 1L;

    @BeforeEach
    void setUp() {
        userUUID = UUID.randomUUID();

        mockCompetency = new Competency();
        mockCompetency.setId(compId);
        mockCompetency.setName("Java Programming");
        mockCompetency.setDeleted(false);

        mockCompetencyDto = new CompetencyDto();
        mockCompetencyDto.setId(compId);
        mockCompetencyDto.setName("Java Programming");
        mockCompetencyDto.setDeleted(false);
        mockCompetencyDto.setCreatedBy(userUUID);
        mockCompetencyDto.setCreatedAt(OffsetDateTime.now());
        mockCompetencyDto.setUpdatedBy(userUUID);
        mockCompetencyDto.setUpdatedAt(OffsetDateTime.now());

        // Lenient stubbing for MessageSource to prevent unnecessary stubbing errors
        lenient().when(messageSource.getMessage(any(), any(), any(Locale.class))).thenReturn("Mock Error Message");
    }

    // --- Find Tests ---

    @Test
    void findAllByIsDeletedIsFalse_ShouldReturnList() {
        when(competencyRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency));
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        List<CompetencyDto> result = competencyService.findAllByIsDeletedIsFalse();
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void findAllById_ShouldReturnDto_WhenFound() {
        when(competencyRepository.findById(compId)).thenReturn(Optional.of(mockCompetency));
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        CompetencyDto result = competencyService.findAllById(compId);
        assertNotNull(result);
        assertEquals("Java Programming", result.getName());
    }

    @Test
    void findAllById_ShouldThrowException_WhenNotFound() {
        when(competencyRepository.findById(compId)).thenReturn(Optional.empty());
        assertThrows(DataAccessException.class, () -> competencyService.findAllById(compId));
    }

    @Test
    void findAllByIsDeletedIsFalseAndIdIn_ShouldReturnList_WhenAllFound() {
        Set<Long> ids = Set.of(compId);
        when(competencyRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockCompetency));
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        List<CompetencyDto> result = competencyService.findAllByIsDeletedIsFalseAndIdIn(ids);
        assertEquals(1, result.size());
    }

    @Test
    void findAllByIsDeletedIsFalseAndIdIn_ShouldThrowException_WhenSizeMismatch() {
        Set<Long> ids = Set.of(compId, 2L); // Requesting 2 IDs
        when(competencyRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockCompetency)); // Returning only 1
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        assertThrows(DataAccessException.class, () -> competencyService.findAllByIsDeletedIsFalseAndIdIn(ids));
    }

    // --- Create Tests ---

    @Test
    void create_ShouldSave_WhenValid() {
        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(competencyRepository.findByNameIgnoreCaseAndIsDeletedIsFalse("Java Programming")).thenReturn(Optional.empty());
        when(appMapper.toEntity(mockCompetencyDto)).thenReturn(mockCompetency);
        when(competencyRepository.save(mockCompetency)).thenReturn(mockCompetency);
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        CompetencyDto result = competencyService.create(mockCompetencyDto);
        assertNotNull(result);
        verify(competencyRepository).save(any());
    }

    @Test
    void create_ShouldThrowException_WhenNameRedundant() {
        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(competencyRepository.findByNameIgnoreCaseAndIsDeletedIsFalse("Java Programming")).thenReturn(Optional.of(mockCompetency));

        // Mapped to DTO inside checkRedundancyByName
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        assertThrows(DataAccessException.class, () -> competencyService.create(mockCompetencyDto));
    }

    @Test
    void create_ShouldThrowException_WhenValidationFails() {
        mockCompetencyDto.setName(null);
        when(validationService.isNullOrBlank(null)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> competencyService.create(mockCompetencyDto));
    }

    // --- Create All (Bulk) Tests ---

    @Test
    void createAll_ShouldSaveList_WhenValid() {
        List<CompetencyDto> dtos = List.of(mockCompetencyDto);
        // Mock existing checks
        when(competencyRepository.findAllByNameInIgnoreCaseAndIsDeletedIsFalse(anySet())).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        when(appMapper.toEntity(any(CompetencyDto.class))).thenReturn(mockCompetency);
        when(competencyRepository.saveAll(anyList())).thenReturn(List.of(mockCompetency));
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        List<CompetencyDto> result = competencyService.createAll(dtos);
        assertFalse(result.isEmpty());
    }

    @Test
    void createAll_ShouldThrowException_WhenListEmpty() {
        assertThrows(BadRequestException.class, () -> competencyService.createAll(Collections.emptyList()));
    }

    @Test
    void createAll_ShouldThrowException_WhenInputDuplicates() {
        List<CompetencyDto> dtos = List.of(mockCompetencyDto, mockCompetencyDto);
        assertThrows(BadRequestException.class, () -> competencyService.createAll(dtos));
    }

    @Test
    void createAll_ShouldThrowException_WhenDbRedundant() {
        List<CompetencyDto> dtos = List.of(mockCompetencyDto);

        // DB returns existing record
        when(competencyRepository.findAllByNameInIgnoreCaseAndIsDeletedIsFalse(anySet())).thenReturn(List.of(mockCompetency));
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        assertThrows(BadRequestException.class, () -> competencyService.createAll(dtos));
    }

    @Test
    void createAll_ShouldThrowException_WhenInputContainsDuplicates() {
        CompetencyDto d1 = new CompetencyDto(); d1.setName("Java");
        CompetencyDto d2 = new CompetencyDto(); d2.setName("Java");

        assertThrows(BadRequestException.class, () -> competencyService.createAll(List.of(d1, d2)));
    }

    @Test
    void createAll_ShouldThrowException_WhenItemIsInvalid() {
        CompetencyDto validDto = new CompetencyDto(); validDto.setName("Java");
        CompetencyDto invalidDto = new CompetencyDto(); invalidDto.setName("");

        // Mock validation
        when(competencyRepository.findAllByNameInIgnoreCaseAndIsDeletedIsFalse(anySet())).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank("Java")).thenReturn(false);
        when(validationService.isNullOrBlank("")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> competencyService.createAll(List.of(validDto, invalidDto)));
    }

    // --- Create and Update All Tests ---

    @Test
    void createAndUpdateAll_ShouldHandleMixedOperations() {
        // 1. Update existing "Java" (ID 1)
        CompetencyDto updateDto = new CompetencyDto(); updateDto.setId(compId); updateDto.setName("Java Advanced");
        // 2. Create new "Python"
        CompetencyDto createDto = new CompetencyDto(); createDto.setName("Python");

        List<CompetencyDto> input = List.of(updateDto, createDto);

        // --- MOCK DB Existing State ---
        when(competencyRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(mockCompetency)); // Returns entity ID 1

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        // --- MOCK Save Operation ---
        Competency savedEntity1 = new Competency();
        Competency savedEntity2 = new Competency();
        when(appMapper.toEntity(any(CompetencyDto.class))).thenReturn(savedEntity1, savedEntity2);
        when(competencyRepository.saveAll(anyList())).thenReturn(List.of(savedEntity1, savedEntity2));

        CompetencyDto resultDto = new CompetencyDto(); resultDto.setName("Result");

        // --- SINGLE SMART STUB ---
        // This handles BOTH the initial DB fetch (for the Map) AND the final result mapping
        when(appMapper.toDto(any(Competency.class))).thenAnswer(inv -> {
            Competency arg = inv.getArgument(0);
            // If it's the specific mock entity we set up for the DB find...
            if (arg == mockCompetency || (arg.getId() != null && arg.getId().equals(compId))) {
                return mockCompetencyDto; // Return the DTO with ID 1 so the Map key logic works
            }
            return resultDto; // Return generic DTO for the newly saved entities
        });

        List<CompetencyDto> result = competencyService.createAndUpdateAll(input);
        assertEquals(2, result.size());
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenDuplicateInInput() {
        List<CompetencyDto> dtos = List.of(mockCompetencyDto, mockCompetencyDto);
        assertThrows(BadRequestException.class, () -> competencyService.createAndUpdateAll(dtos));
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenUpdatingNameToExistingOtherRecord() {
        // Scenario: Try to rename ID 1 to "Python", but ID 2 is already "Python"
        CompetencyDto updateDto = new CompetencyDto();
        updateDto.setId(1L);
        updateDto.setName("Python");

        // DB State: ID 1="Java", ID 2="Python"
        Competency c1 = new Competency(); c1.setId(1L); c1.setName("Java");
        Competency c2 = new Competency(); c2.setId(2L); c2.setName("Python");

        CompetencyDto d1 = new CompetencyDto(); d1.setId(1L); d1.setName("Java");
        CompetencyDto d2 = new CompetencyDto(); d2.setId(2L); d2.setName("Python");

        when(competencyRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(c1, c2));
        when(appMapper.toDto(c1)).thenReturn(d1);
        when(appMapper.toDto(c2)).thenReturn(d2);

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> competencyService.createAndUpdateAll(List.of(updateDto)));
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenIdNotFoundInDb() {
        // Input has ID 99, but DB is empty
        CompetencyDto notFoundDto = new CompetencyDto();
        notFoundDto.setId(99L);
        notFoundDto.setName("Ghost Competency");

        when(competencyRepository.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(DataAccessException.class, () -> competencyService.createAndUpdateAll(List.of(notFoundDto)));
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenRenamingToExistingName() {
        // DB has: ID 1="Java", ID 2="Python"
        Competency c1 = new Competency(); c1.setId(1L); c1.setName("Java");
        Competency c2 = new Competency(); c2.setId(2L); c2.setName("Python");

        CompetencyDto d1 = new CompetencyDto(); d1.setId(1L); d1.setName("Java");
        CompetencyDto d2 = new CompetencyDto(); d2.setId(2L); d2.setName("Python");

        when(competencyRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(c1, c2));
        when(appMapper.toDto(c1)).thenReturn(d1);
        when(appMapper.toDto(c2)).thenReturn(d2);

        // Input: Try to rename ID 1 ("Java") to "Python" (which belongs to ID 2)
        CompetencyDto updateDto = new CompetencyDto();
        updateDto.setId(1L);
        updateDto.setName("Python");

        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> competencyService.createAndUpdateAll(List.of(updateDto)));
    }

    @Test
    void createAndUpdateAll_ShouldAllowNameReuse_WhenDeletingAndCreatingSameName() {
        // DB has: ID 1="Java"
        Competency c1 = new Competency(); c1.setId(1L); c1.setName("Java");
        CompetencyDto d1 = new CompetencyDto(); d1.setId(1L); d1.setName("Java");

        when(competencyRepository.findAllByIsDeletedIsFalse()).thenReturn(List.of(c1));
        when(appMapper.toDto(c1)).thenReturn(d1);

        // Input 1: Delete ID 1 ("Java")
        CompetencyDto deleteDto = new CompetencyDto();
        deleteDto.setId(1L);
        deleteDto.setName("Java");
        deleteDto.setDeleted(true);

        // Input 2: Create NEW "Java"
        CompetencyDto createDto = new CompetencyDto();
        createDto.setName("Java"); // Same name!
        createDto.setDeleted(false);

        when(validationService.isNullOrBlank(any())).thenReturn(false);
        when(appMapper.toEntity(any(CompetencyDto.class))).thenReturn(c1);
        when(competencyRepository.saveAll(anyList())).thenReturn(List.of(c1, c1));

        List<CompetencyDto> result = competencyService.createAndUpdateAll(List.of(deleteDto, createDto));
        assertEquals(2, result.size());
    }

    @Test
    void createAndUpdateAll_ShouldThrowException_WhenDataInvalid() {
        CompetencyDto invalidDto = new CompetencyDto();
        invalidDto.setName(""); // Invalid

        when(competencyRepository.findAllByIsDeletedIsFalse()).thenReturn(Collections.emptyList());
        when(validationService.isNullOrBlank("")).thenReturn(true); // Fail validation

        assertThrows(BadRequestException.class, () -> competencyService.createAndUpdateAll(List.of(invalidDto)));
    }

    // --- Update Tests ---

    @Test
    void update_ShouldSave_WhenValid() {
        CompetencyDto updateInput = new CompetencyDto();
        updateInput.setName("Updated Name");

        when(competencyRepository.findById(compId)).thenReturn(Optional.of(mockCompetency));
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        // Check redundancy (Same ID allowed)
        when(competencyRepository.findByNameIgnoreCaseAndIsDeletedIsFalse("Updated Name")).thenReturn(Optional.empty());

        when(appMapper.toEntity(any(CompetencyDto.class))).thenReturn(mockCompetency);
        when(competencyRepository.save(any())).thenReturn(mockCompetency);

        CompetencyDto result = competencyService.update(compId, updateInput);
        assertNotNull(result);
    }

    @Test
    void update_ShouldThrowException_WhenRedundantWithOtherId() {
        CompetencyDto updateInput = new CompetencyDto();
        updateInput.setName("Existing Name");

        when(competencyRepository.findById(compId)).thenReturn(Optional.of(mockCompetency));
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        // DB returns a DIFFERENT record (ID 99) with that name
        Competency other = new Competency(); other.setId(99L);
        CompetencyDto otherDto = new CompetencyDto(); otherDto.setId(99L);

        when(competencyRepository.findByNameIgnoreCaseAndIsDeletedIsFalse("Existing Name")).thenReturn(Optional.of(other));
        when(appMapper.toDto(other)).thenReturn(otherDto);

        assertThrows(DataAccessException.class, () -> competencyService.update(compId, updateInput));
    }

    @Test
    void update_ShouldThrowException_WhenDescriptionTooLong() {
        CompetencyDto dto = new CompetencyDto();
        dto.setName("Java");
        dto.setDescription("a".repeat(1001)); // > 1000 chars

        when(competencyRepository.findById(1L)).thenReturn(Optional.of(mockCompetency));
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        // Ensure name passes
        when(validationService.isNullOrBlank(any())).thenReturn(false);

        assertThrows(BadRequestException.class, () -> competencyService.update(1L, dto));
    }

    // --- Delete Tests ---

    @Test
    void delete_ShouldSoftDelete() {
        when(competencyRepository.findById(compId)).thenReturn(Optional.of(mockCompetency));
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        when(appMapper.toEntity(any(CompetencyDto.class))).thenAnswer(inv -> {
            CompetencyDto dto = inv.getArgument(0);
            Competency c = new Competency();
            c.setId(dto.getId());
            c.setDeleted(dto.isDeleted());
            return c;
        });

        competencyService.delete(compId, userUUID);

        verify(competencyRepository).save(argThat(Competency::isDeleted));
    }

    @Test
    void deleteAllByIdIn_ShouldSoftDeleteAll() {
        Set<Long> ids = Set.of(compId);
        when(competencyRepository.findAllByIsDeletedIsFalseAndIdIn(ids)).thenReturn(List.of(mockCompetency));
        when(appMapper.toDto(mockCompetency)).thenReturn(mockCompetencyDto);

        when(appMapper.toEntity(any(CompetencyDto.class))).thenAnswer(inv -> {
            CompetencyDto dto = inv.getArgument(0);
            Competency c = new Competency();
            c.setDeleted(dto.isDeleted());
            return c;
        });

        competencyService.deleteAllByIdIn(ids, userUUID);

        verify(competencyRepository).saveAll(argThat(list -> {
            List<Competency> entities = (List<Competency>) list;
            return !entities.isEmpty() && entities.get(0).isDeleted();
        }));
    }
}