package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.AuthorityName;
import jakarta.persistence.*;

@Entity
@Table(name = "authority")
public class Authority {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AuthorityName name;

    @Column(name = "description_key", length = 255, nullable = false)
    private String descriptionKey;

    @Column(name = "label_key", length = 255, nullable = false)
    private String labelKey;

    public Authority() {
    }

    public Authority(AuthorityName name, String descriptionKey) {
        this.name = name;
        this.descriptionKey = descriptionKey;
    }

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

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public void setDescriptionKey(String description) {
        this.descriptionKey = description;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public void setLabelKey(String labelKey) {
        this.labelKey = labelKey;
    }
}