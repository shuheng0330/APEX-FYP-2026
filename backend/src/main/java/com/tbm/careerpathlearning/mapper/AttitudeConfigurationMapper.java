package com.tbm.careerpathlearning.mapper;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.model.*;
import org.mapstruct.*;

@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface AttitudeConfigurationMapper {
    AttitudeConfigurationDto toDto(AttitudeConfiguration configuration);
    AttitudeConfigurationDto.Criterion toDto(AttitudeCriterion criterion);
    AttitudeConfigurationRequest.Rating toDto(AttitudeRatingDefinition rating);
    @Mapping(target="roleId",source="role.id")
    @Mapping(target="roleName",source="role.name")
    AttitudeConfigurationDto.RoleMapping toDto(AttitudeRoleFormatMapping mapping);
}
