package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainingAuditEntryDto;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAuditLog;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingAuditLogRepository;
import com.tbm.careerpathlearning.service.TrainingAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TrainingAuditServiceImpl implements TrainingAuditService {

    @Autowired private TrainingAuditLogRepository trainingAuditLogRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private Clock clock;

    @Override
    @Transactional
    public void record(UUID actorId, TrainingAuditAction action, TrainingAuditTarget target, String targetId,
                       String reason, String detail) {
        TrainingAuditLog entry = new TrainingAuditLog();
        entry.setAction(action.name());
        entry.setTargetType(target.name());
        entry.setTargetId(targetId);
        entry.setActorId(actorId);
        entry.setReason(reason);
        entry.setDetail(detail);
        entry.setCreatedAt(OffsetDateTime.now(clock));
        trainingAuditLogRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingAuditEntryDto> history(TrainingAuditTarget target, String targetId) {
        Map<UUID, String> names = new HashMap<>();
        return trainingAuditLogRepository.findAllByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(target.name(), targetId)
                .stream()
                .map(e -> {
                    TrainingAuditEntryDto dto = new TrainingAuditEntryDto();
                    dto.setId(e.getId());
                    dto.setAction(e.getAction());
                    dto.setActorName(e.getActorId() == null ? null : names.computeIfAbsent(e.getActorId(),
                            id -> staffRepository.findById(id).map(Staff::getName).orElse("Unknown user")));
                    dto.setReason(e.getReason());
                    dto.setDetail(e.getDetail());
                    dto.setCreatedAt(e.getCreatedAt());
                    return dto;
                })
                .toList();
    }
}
