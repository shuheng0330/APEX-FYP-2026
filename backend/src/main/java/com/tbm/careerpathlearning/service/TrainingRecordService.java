package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrainingRecordDto;

import java.util.UUID;

/**
 * Staff training records (FR-18). Staff see their own; managers see their direct reports'; HR and the Super Admin see
 * anyone's (decision A, 29 Oct 2026).
 */
public interface TrainingRecordService {

    TrainingRecordDto record(UUID staffId, TrainingViewer viewer);
}
