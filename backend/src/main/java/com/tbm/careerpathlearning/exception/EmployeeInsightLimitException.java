package com.tbm.careerpathlearning.exception;

import com.tbm.careerpathlearning.dto.EmployeeInsightDto.LimitError;

public class EmployeeInsightLimitException extends RuntimeException {
    private final LimitError detail;
    public EmployeeInsightLimitException(LimitError detail) { super(detail.message()); this.detail = detail; }
    public LimitError detail() { return detail; }
}
