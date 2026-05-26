package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.AuthorityDto;
import com.tbm.careerpathlearning.dto.TranslatedAuthorityDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.exception.DataAccessException;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Authority;
import com.tbm.careerpathlearning.repository.AuthorityRepository;
import com.tbm.careerpathlearning.service.AuthorityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthorityServiceImpl implements AuthorityService {

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private AppMapper appMapper;

    private static final String RESULT_NOT_FOUND_ERR_MSG_CODE = "database.result.not.found.err.msg";

    @Override
    public List<AuthorityDto> findAll() {
        return authorityRepository.findAll().stream().map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public List<TranslatedAuthorityDto> getAllTranslatedAuthorities() {
        List<AuthorityDto> authorityDtoList = this.findAll();
        List<TranslatedAuthorityDto> translatedAuthorityDtoList = new ArrayList<>();

        for (AuthorityDto authorityDto : authorityDtoList) {
            Long id = authorityDto.getId();
            String descriptionKey = authorityDto.getDescriptionKey();
            String labelKey = authorityDto.getLabelKey();

            String description = messageSource.getMessage(descriptionKey, null, Locale.getDefault());
            String label = messageSource.getMessage(labelKey, null, Locale.getDefault());

            TranslatedAuthorityDto translatedAuthorityDto = new TranslatedAuthorityDto();
            translatedAuthorityDto.setId(id);
            translatedAuthorityDto.setName(authorityDto.getName());
            translatedAuthorityDto.setDescription(description);
            translatedAuthorityDto.setLabel(label);

            translatedAuthorityDtoList.add(translatedAuthorityDto);
        }

        return translatedAuthorityDtoList;
    }

    @Override
    public AuthorityDto findById(Long id) {
        Authority authority = authorityRepository.findById(id).orElseThrow(() ->
                new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault())));
        return this.appMapper.toDto(authority);
    }

    @Override
    public List<AuthorityDto> findAllByIdIn(Set<Long> ids) {
        return authorityRepository.findAllById(ids).stream().map(this.appMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public AuthorityDto findByName(AuthorityName authorityName) {
        Optional<AuthorityDto> authorityDto = authorityRepository.findByName(authorityName).map(this.appMapper::toDto);

        if (authorityDto.isEmpty()) {
            throw new DataAccessException(messageSource.getMessage(RESULT_NOT_FOUND_ERR_MSG_CODE, null, Locale.getDefault()));
        }

        return authorityDto.get();
    }

    @Override
    public List<AuthorityDto> findAllByNameIn(Set<AuthorityName> authorityNames) {
        return authorityRepository.findAllByNameIn(authorityNames).stream().map(appMapper::toDto).collect(Collectors.toList());
    }

}
