package com.tbm.careerpathlearning.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Published choices and applicable Role coverage, without exposing the setup editor. */
public record ReviewPeriodAttitudeConfigurationOption(
        Long id, String name, OffsetDateTime publishedAt, List<Long> coveredRoleIds) {}
