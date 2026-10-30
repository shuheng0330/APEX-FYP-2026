package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.KpiLevel;

public record KpiCentralResultOptionDto(Long planVersionId,int versionNumber,KpiLevel level,
        Long departmentId,String departmentName,boolean currentVersion) {}
