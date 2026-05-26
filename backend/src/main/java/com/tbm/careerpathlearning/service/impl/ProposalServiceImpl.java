package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.ProposalDto;
import com.tbm.careerpathlearning.enums.ProposalStatus;
import com.tbm.careerpathlearning.enums.ProposalType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Proposal;
import com.tbm.careerpathlearning.repository.ProposalRepository;
import com.tbm.careerpathlearning.service.ProposalService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProposalServiceImpl implements ProposalService {

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private MessageSource messageSource;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    private static final String INVALID_DATA_ERR_MSG_CODE = "invalid.data.err.msg";

    private static final String CREATE_OPERATION = "Create Proposal";

    @Override
    public List<ProposalDto> getAll() {
        return proposalRepository.findAll().stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public ProposalDto getProposalById(Long id) {
        return proposalRepository.findById(id).map(appMapper::toDto)
                .orElseThrow(() ->
                        new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));
    }

    @Override
    public List<ProposalDto> getAllProposalsByIdIn(Set<Long> ids) {
        return proposalRepository.findAllById(ids).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<ProposalDto> getAllByType(ProposalType type) {
        return proposalRepository.findAllByType(type).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProposalDto createProposal(ProposalDto proposalDto) {
        if (proposalDto.getType() == null) {
            String errorMessage = messageSource.getMessage(INVALID_DATA_ERR_MSG_CODE,
                    new String[]{CREATE_OPERATION}, Locale.getDefault());

            throw new BadRequestException(errorMessage);
        }

        if (proposalDto.getStatus() == null) {
            proposalDto.setStatus(ProposalStatus.ONGOING);
        }

        if (proposalDto.getCreatedAt() == null) {
            proposalDto.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8)));
        }

        if (proposalDto.getUpdatedAt() == null) {
            proposalDto.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8)));
        }

        return appMapper.toDto(proposalRepository.save(this.appMapper.toEntity(proposalDto)));
    }

    @Override
    @Transactional
    public ProposalDto updateProposalStatusById(Long proposalId, ProposalStatus proposalStatus, UUID userId) {
        ProposalDto proposalDtoToBeUpdated = this.getProposalById(proposalId);

        proposalDtoToBeUpdated.setStatus(proposalStatus);
        proposalDtoToBeUpdated.setUpdatedBy(userId);
        proposalDtoToBeUpdated.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8)));

        return appMapper.toDto(proposalRepository.save(appMapper.toEntity(proposalDtoToBeUpdated)));
    }

    @Override
    @Transactional
    public List<ProposalDto> updateProposalStatusByIdIn(Set<Long> proposalId, ProposalStatus proposalStatus, UUID userId) {
        List<Proposal> proposalDtoToBeUpdated = this.getAllProposalsByIdIn(proposalId).stream()
                .map(proposalDto -> {
                    proposalDto.setStatus(proposalStatus);
                    proposalDto.setUpdatedBy(userId);
                    proposalDto.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(8)));
                    return appMapper.toEntity(proposalDto);
                }).toList();

        return proposalRepository.saveAll(proposalDtoToBeUpdated).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

}
