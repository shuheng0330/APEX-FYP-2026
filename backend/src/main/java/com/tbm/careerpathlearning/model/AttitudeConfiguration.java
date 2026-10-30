package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.AttitudeConfigurationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.*;

@Getter @Setter @Entity @Table(name="attitude_configuration")
public class AttitudeConfiguration {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(length=255) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private AttitudeConfigurationStatus status=AttitudeConfigurationStatus.DRAFT;
    @Column(name="created_at",nullable=false,updatable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="updated_by",nullable=false) private UUID updatedBy;
    @Column(name="published_at") private OffsetDateTime publishedAt;
    @Column(name="published_by") private UUID publishedBy;
    @OneToMany(mappedBy="configuration",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("displayOrder ASC, id ASC") private List<AttitudeCriterion> criteria=new ArrayList<>();
    @OneToMany(mappedBy="configuration",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("point DESC") private List<AttitudeRatingDefinition> ratingDefinitions=new ArrayList<>();
    @OneToMany(mappedBy="configuration",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("id ASC") private List<AttitudeRoleFormatMapping> roleMappings=new ArrayList<>();
}
