package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AuthorityName;

public class TranslatedAuthorityDto {

    private Long id;
    private AuthorityName name;
    private String description;
    private String label;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AuthorityName getName() {
        return name;
    }

    public void setName(AuthorityName name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
