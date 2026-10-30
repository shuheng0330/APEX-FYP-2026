package com.tbm.careerpathlearning.enums;

public enum KpiVersionStatus {
    REQUESTED, DRAFT, PENDING_APPROVAL, RETURNED, PENDING_MD_APPROVAL,
    PENDING_HOD_CONFIRMATION, EFFECTIVE, SUPERSEDED, CANCELLED;
    public boolean unfinished() { return this != EFFECTIVE && this != SUPERSEDED && this != CANCELLED; }
}
