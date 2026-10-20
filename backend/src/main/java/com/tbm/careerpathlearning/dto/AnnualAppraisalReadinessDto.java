package com.tbm.careerpathlearning.dto;

import java.util.List;

public record AnnualAppraisalReadinessDto(boolean ready, List<String> blockers, AnnualAppraisalSnapshotDto performance) {}
