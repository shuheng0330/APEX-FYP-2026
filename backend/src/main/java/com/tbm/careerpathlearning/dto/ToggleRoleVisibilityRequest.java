package com.tbm.careerpathlearning.dto;

public class ToggleRoleVisibilityRequest {

    private Long roleId;
    private boolean visibility;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public boolean getVisibility() {
        return visibility;
    }

    public void setVisibility(boolean visibility) {
        this.visibility = visibility;
    }
}