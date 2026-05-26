package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.RoleCompetency;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.LearningMaterialRecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LearningMaterialRecommendationServiceImpl implements LearningMaterialRecommendationService {

    @Autowired
    private LearningMaterialRepository learningMaterialRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private EvaluationRatingsRepository evaluationRatingsRepository;

    @Autowired
    RoleCompetencyRepository roleCompetencyRepository;

    @Autowired
    private StaffLearningMaterialRepository staffLearningMaterialRepository;

    @Autowired
    AppMapper appMapper;

    @Override
    public List<LearningMaterialDto> recommendForUser(UUID staffId) {
        final int requiredLevelTemp = 10;

        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        Long roleId = staff.getRole().getId();

        Set<Long> completedMaterialIds = staffLearningMaterialRepository
                .findByStaff_IdAndIsCompletedTrue(staffId)
                .stream()
                .map(slm -> slm.getLearningMaterial().getMaterialId())
                .collect(Collectors.toSet());

        Map<Long, Integer> competencyGapMap = evaluationRatingsRepository.findLatestRatingsByStaffId(staffId).stream()
                .collect(Collectors.toMap(
                        r -> r.getCompetency().getId(),
                        r -> requiredLevelTemp - r.getRating()
                ));

        Map<Long, Integer> weightageMap = roleCompetencyRepository.findByRoleId(roleId).stream()
                .collect(Collectors.toMap(
                        rc -> rc.getCompetency().getId(),
                        RoleCompetency::getWeightage
                ));

        return learningMaterialRepository.findAll().stream()
                .map(appMapper::toDto)
                .filter(m -> !completedMaterialIds.contains(m.getMaterialId()))
                .map(m -> {
                    // 1. Calculate Raw Impact
                    double totalImpact = m.getCompetency().stream()
                            .mapToDouble(c -> {
                                int gap = competencyGapMap.getOrDefault(c.getId(), 0);
                                double weightage = weightageMap.getOrDefault(c.getId(), 0) / 100.0;

                                if (gap <= 0) return 0;

                                // STRATEGY CHANGE: Square the gap.
                                // This makes a gap of 8 much more "painful" than two gaps of 4.
                                // Formula: (Gap^2) * Weightage
                                return (gap * gap) * weightage;
                            })
                            .sum();

                    if (totalImpact == 0) return new ScoredMaterial(m, 0);

                    // 2. Efficiency Factor Implementation
                    // Convert stored seconds to minutes
                    double durationInMinutes = (m.getTotalDurationAllDoc() != null)
                            ? m.getTotalDurationAllDoc() / 60.0
                            : 5.0;

                    // Apply Logarithmic Scaling to balance impact vs time
                    double efficiencyFactor = 1 / Math.log10(durationInMinutes + 10);

                    // 3. Final Multi-Objective Score
                    double finalScore = totalImpact * (1 + 0.3 * efficiencyFactor);

                    return new ScoredMaterial(m, finalScore);
                })
                .filter(s -> s.score > 0)
                .sorted((s1, s2) -> Double.compare(s2.score, s1.score))
                .map(s -> s.material)
                .toList();
    }

    private static class ScoredMaterial {
        LearningMaterialDto material;
        double score; // Changed from int to double

        ScoredMaterial(LearningMaterialDto material, double score) {
            this.material = material;
            this.score = score;
        }
    }
}
