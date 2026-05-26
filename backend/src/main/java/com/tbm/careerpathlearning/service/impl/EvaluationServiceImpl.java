package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.EvaluationDTO;
import com.tbm.careerpathlearning.dto.RatingDTO;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.EvaluationService;
import com.tbm.careerpathlearning.service.StaffService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvaluationServiceImpl implements EvaluationService {

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private EvaluationCycleRepository evaluationCycleRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private RoleCompetencyRepository roleCompetencyRepository;

    @Autowired
    private StaffService staffService;

    @Autowired
    private MessageSource messageSource;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String EVALUATION_RATING_EMPTY = "evaluation.rating.empty.err.msg";

    private static final String ROLE_COMPETENCY_NOT_CONFIGURE = "role.competency.not.configured.err.msg";

    @Override
    @Transactional
    public EvaluationDTO createEvaluation(EvaluationDTO dto) {
        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new DataAccessException(
                        "Invalid Staff",
                        messageSource.getMessage(
                                RESULT_NOT_FOUND_ERR_MSG_CODE,
                                null,
                                Locale.getDefault()
                        )
                ));

        // Map role competencies for weightage
        Map<Long, Integer> compWeightMap = roleCompetencyRepository.findByRoleId(staff.getRole().getId()).stream()
                .collect(Collectors.toMap(rc -> rc.getCompetency().getId(), RoleCompetency::getWeightage));

        if (compWeightMap.isEmpty()) {
            throw new BadRequestException(
                    "Invalid Configuration",
                    messageSource.getMessage(
                            ROLE_COMPETENCY_NOT_CONFIGURE,
                            null,
                            Locale.getDefault()
                    )
            );
        }

        if (dto.getRatings() == null || dto.getRatings().isEmpty()) {
            throw new BadRequestException(
                    "Invalid Evaluation",
                    messageSource.getMessage(
                            EVALUATION_RATING_EMPTY,
                            null,
                            Locale.getDefault()
                    )
            );
        }

        EvaluationCycle activeCycle = evaluationCycleRepository.findActiveCycle()
               .orElseThrow(() -> new BadRequestException(
                       "No Active Cycle",
                       "There is no active evaluation cycle at the moment."
               ));


        Evaluation evaluation = new Evaluation();
        evaluation.setStaff(staff);
        evaluation.setEvaluationCycle(activeCycle);
        evaluation.setComment(dto.getComment());
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluation.setCreatedBy(dto.getCreatedBy());

        List<EvaluationRatings> ratings = dto.getRatings().stream().map(r -> {
            Competency competency = competencyRepository.findById(r.getCompId())
                    .orElseThrow(() -> new RuntimeException("Competency not found"));

            EvaluationRatings er = new EvaluationRatings();
            er.setEvaluation(evaluation);
            er.setCompetency(competency);
            er.setRating(r.getRating());
            er.setCreatedAt(LocalDate.now());
            return er;
        }).toList();

        // Compute overall score
        double totalWeightedScore = ratings.stream()
                .mapToInt(r -> r.getRating() * compWeightMap.getOrDefault(r.getCompetency().getId(), 0))
                .sum();
        double totalWeightage = ratings.stream()
                .mapToInt(r -> compWeightMap.getOrDefault(r.getCompetency().getId(), 0))
                .sum();
        double overallScore = (totalWeightage > 0) ? (totalWeightedScore * 100) / (totalWeightage * 10) : 0;

        overallScore = Math.round(overallScore * 100.0) / 100.0;
        evaluation.setOverallScore(overallScore);

        evaluation.getRatings().addAll(ratings);
        Evaluation saved = evaluationRepository.save(evaluation);

        // Map to DTO
        return mapToDTO(saved);
    }

    @Override
    public List<EvaluationDTO> getAllEvaluations() {

        return evaluationRepository.findAllWithRatings()
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Override
    public List<EvaluationDTO> getAllEvaluationsByStaffId(UUID staffId) {
        return evaluationRepository.findAllByStaffWithRatings(staffId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Override
    public List<EvaluationDTO> getAllDownLineEvaluation(UUID staffId) {
        Set<UUID> staffIds = staffService.getAllDownlineStaffIds(staffId);

        return evaluationRepository
                .findAllByStaffIdIn(staffIds)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    private EvaluationDTO mapToDTO(Evaluation evaluation) {

        EvaluationDTO dto = new EvaluationDTO();
        dto.setEvaluationId(evaluation.getEvaluationId());
        dto.setStaffId(evaluation.getStaff().getId());
        dto.setStaffName(evaluation.getStaff().getName());
        dto.setComment(evaluation.getComment());
        dto.setOverallScore(evaluation.getOverallScore());
        dto.setCreatedAt(evaluation.getCreatedAt());
        if (evaluation.getEvaluationCycle() != null) {
            dto.setEvaluationCycleEndDate(evaluation.getEvaluationCycle().getEndDate().toString());
        }

        List<RatingDTO> ratingDTOs = evaluation.getRatings().stream()
                .map(r -> {
                    RatingDTO ratingDTO = new RatingDTO();
                    ratingDTO.setCompId(r.getCompetency().getId());
                    ratingDTO.setCompetencyName(r.getCompetency().getName());
                    ratingDTO.setRating(r.getRating());
                    return ratingDTO;
                })
                .toList();

        dto.setRatings(ratingDTOs);
        return dto;
    }

    private Evaluation mapToEntity(EvaluationDTO dto) {
        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new EntityNotFoundException("Staff not found with ID: " + dto.getStaffId()));

        Evaluation evaluation = new Evaluation();
        evaluation.setStaff(staff);
        evaluation.setComment(dto.getComment());
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluation.setOverallScore(0.0);
        return evaluation;
    }

}

