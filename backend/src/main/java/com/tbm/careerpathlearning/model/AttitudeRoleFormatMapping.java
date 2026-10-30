package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.AttitudeEvaluationFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name="attitude_role_format_mapping",uniqueConstraints=
    @UniqueConstraint(name="uq_attitude_configuration_role",columnNames={"configuration_id","role_id"}))
public class AttitudeRoleFormatMapping {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="configuration_id",nullable=false)
    private AttitudeConfiguration configuration;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="role_id",nullable=false)
    private Role role;
    @Enumerated(EnumType.STRING) @Column(name="evaluation_format",nullable=false,length=20)
    private AttitudeEvaluationFormat evaluationFormat;
}
