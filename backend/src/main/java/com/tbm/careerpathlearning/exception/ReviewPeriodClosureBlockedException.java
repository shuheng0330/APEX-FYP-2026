package com.tbm.careerpathlearning.exception;

import com.tbm.careerpathlearning.dto.ReviewPeriodClosureDto;

public class ReviewPeriodClosureBlockedException extends RuntimeException {
    private final ReviewPeriodClosureDto readiness;
    public ReviewPeriodClosureBlockedException(ReviewPeriodClosureDto readiness) {
        super("Complete the outstanding requirements before closing this Review Period");
        this.readiness=readiness;
    }
    public ReviewPeriodClosureDto readiness() { return readiness; }
}
