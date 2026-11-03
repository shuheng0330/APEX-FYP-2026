package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.EmployeeLevelDto;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AnnualKpiReviewPeriodMapper;
import com.tbm.careerpathlearning.repository.EmployeeLevelRepository;
import com.tbm.careerpathlearning.service.EmployeeLevelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmployeeLevelServiceImpl implements EmployeeLevelService {
    private final EmployeeLevelRepository levels;
    private final AnnualKpiReviewPeriodMapper mapper;
    public EmployeeLevelServiceImpl(EmployeeLevelRepository levels, AnnualKpiReviewPeriodMapper mapper) {
        this.levels = levels; this.mapper = mapper;
    }
    @Override public List<EmployeeLevelDto> options() {
        return levels.findAllByOrderByDisplayOrderAsc().stream().map(mapper::toDto).toList();
    }
    @Override public Long resolveCode(String code) {
        return levels.findByCodeIgnoreCase(code.trim()).orElseThrow(() ->
                new BadRequestException("Unknown Employee Level code: " + code)).getId();
    }
}
