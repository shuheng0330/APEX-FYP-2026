package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.ParentChildNodeDto;
import com.tbm.careerpathlearning.enums.RelationType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.ParentChildNode;
import com.tbm.careerpathlearning.repository.ParentChildNodeRepository;
import com.tbm.careerpathlearning.service.ParentChildNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ParentChildNodeServiceImpl implements ParentChildNodeService {

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private ParentChildNodeRepository parentChildNodeRepository;

    @Autowired
    private MessageSource messageSource;

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String CREATE_OPERATION = "Associating nodes";

    @Override
    public List<ParentChildNodeDto> findAllByRelationType(RelationType relationType) {
        return parentChildNodeRepository.findAllByRelationType(relationType).stream().map(appMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAllByParentIdInOrChildIdIn(Set<Long> nodeIds) {
        parentChildNodeRepository.deleteAllByParentIdInOrChildIdIn(nodeIds, nodeIds);
    }

    @Override
    @Transactional
    public List<ParentChildNodeDto> createALl(List<ParentChildNodeDto> dtos, RelationType relationType) {
        if (dtos.isEmpty()) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        Map<Map<Long, Long>, ParentChildNodeDto> existingParentChildMap = this.findAllByRelationType(relationType).stream()
                .collect(Collectors.toMap(
                        dto -> {
                            Map<Long, Long> parentChildId = new HashMap<>();
                            parentChildId.put(dto.getParentId(), dto.getChildId());

                            return parentChildId;
                        },
                        Function.identity()
                ));

        Map<Map<Long, Long>, ParentChildNodeDto> incomingParentChildMap = dtos.stream()
                .collect(Collectors.toMap(
                        dto -> {
                            Map<Long, Long> parentChildId = new HashMap<>();
                            parentChildId.put(dto.getParentId(), dto.getChildId());

                            return parentChildId;
                        },
                        Function.identity()
                ));

        Set<Map<Long, Long>> toAdd = new HashSet<>(incomingParentChildMap.keySet());
        toAdd.removeAll(existingParentChildMap.keySet());

        Set<Map<Long, Long>> toRemove = new HashSet<>(existingParentChildMap.keySet());
        toRemove.removeAll(incomingParentChildMap.keySet());

        Set<Map<Long, Long>> union = new HashSet<>(existingParentChildMap.keySet());
        union.retainAll(incomingParentChildMap.keySet());

        List<ParentChildNodeDto> result = new ArrayList<>();

        if (!toAdd.isEmpty()) {
            List<ParentChildNode> entityToBeAdded = toAdd.stream().map(parentChildId -> {
                ParentChildNodeDto dto = incomingParentChildMap.get(parentChildId);

                if (dto.getParentId().equals(dto.getChildId()) || dto.getRelationType() == null || !Objects.equals(dto.getRelationType(), relationType)
                        || dto.getCreatedAt() == null || dto.getUpdatedAt() == null || dto.getCreatedBy() == null || dto.getUpdatedBy() == null) {
                    String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE, new String[]{CREATE_OPERATION}, Locale.getDefault());

                    throw new BadRequestException(errorMessage);
                }

                return appMapper.toEntity(dto);
            }).toList();

            result.addAll(parentChildNodeRepository.saveAll(entityToBeAdded).stream().map(appMapper::toDto).toList());
        }

        if (!toRemove.isEmpty()) {
            List<ParentChildNode> entityToBeDeleted = toRemove.stream().map(parentChildId ->
                            appMapper.toEntity(existingParentChildMap.get(parentChildId)))
                    .toList();

            parentChildNodeRepository.deleteAll(entityToBeDeleted);
        }

        if (!union.isEmpty()) {
            List<ParentChildNodeDto> unionNodes = union.stream().map(existingParentChildMap::get)
                    .toList();

            result.addAll(unionNodes);
        }

        return result;
    }

}
