package com.tbm.careerpathlearning.dto;


import com.tbm.careerpathlearning.model.RoleAuthorityId;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class RoleAuthorityMapDto {

    private Long roleId;
    private String roleName;
    private Map<Long, Boolean> authorityMap;

    public Map<Long, Boolean> getAuthorityMap() {
        return authorityMap;
    }

    public void setAuthorityMap(Map<Long, Boolean> authorityMap) {
        this.authorityMap = authorityMap;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
