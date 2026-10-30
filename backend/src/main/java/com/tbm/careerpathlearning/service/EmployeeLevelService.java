package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.EmployeeLevelDto;
import java.util.List;

public interface EmployeeLevelService {
    List<EmployeeLevelDto> options();
    Long resolveCode(String code);
}
