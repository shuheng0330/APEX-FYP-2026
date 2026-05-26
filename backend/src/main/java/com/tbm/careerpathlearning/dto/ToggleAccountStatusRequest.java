package com.tbm.careerpathlearning.dto;

import java.util.UUID;

public class ToggleAccountStatusRequest {

    private UUID staffId;
    private boolean accountStatus;

    public UUID getStaffId() {
        return staffId;
    }

    public void setStaffId(UUID staffId) {
        this.staffId = staffId;
    }

    public boolean isAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(boolean accountStatus) {
        this.accountStatus = accountStatus;
    }
}