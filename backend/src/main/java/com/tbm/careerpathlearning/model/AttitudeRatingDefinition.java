package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Getter @Setter @Entity @Table(name="attitude_rating_definition")
@IdClass(AttitudeRatingDefinition.Key.class)
public class AttitudeRatingDefinition {
    @Id @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="configuration_id",nullable=false)
    private AttitudeConfiguration configuration;
    @Id @Column(nullable=false) private Integer point;
    @Column(length=255) private String label;
    @Column(columnDefinition="TEXT") private String description;
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Key implements Serializable {
        private Long configuration;
        private Integer point;
    }
}
