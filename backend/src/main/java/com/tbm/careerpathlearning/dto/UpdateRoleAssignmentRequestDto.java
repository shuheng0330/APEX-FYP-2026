package com.tbm.careerpathlearning.dto;

import java.util.List;
import java.util.UUID;

public class UpdateRoleAssignmentRequestDto {
    private Long roleId;
    private List<UUID> staffIds;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public List<UUID> getStaffIds() {
        return staffIds;
    }

    public void setStaffIds(List<UUID> staffIds) {
        this.staffIds = staffIds;
    }
}
