package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.AuthorityDto;
import com.tbm.careerpathlearning.dto.TranslatedAuthorityDto;
import com.tbm.careerpathlearning.enums.AuthorityName;

import java.util.List;
import java.util.Set;

public interface AuthorityService {
    List<AuthorityDto> findAll();

    List<TranslatedAuthorityDto> getAllTranslatedAuthorities();

    AuthorityDto findById(Long id);

    List<AuthorityDto> findAllByIdIn(Set<Long> ids);

    AuthorityDto findByName(AuthorityName authorityName);

    List<AuthorityDto> findAllByNameIn(Set<AuthorityName> authorityNames);
}
