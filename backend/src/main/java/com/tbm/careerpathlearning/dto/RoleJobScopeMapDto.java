package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleJobScopeMapDto {

    private Long roleId;

    private List<JobScopeDto> assignedJobScopes;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public List<JobScopeDto> getAssignedJobScopes() {
        return assignedJobScopes;
    }

    public void setAssignedJobScopes(List<JobScopeDto> assignedJobScopes) {
        this.assignedJobScopes = assignedJobScopes;
    }
}
