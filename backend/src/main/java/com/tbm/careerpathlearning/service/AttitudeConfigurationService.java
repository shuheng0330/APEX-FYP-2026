package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.*;
import java.util.*;

public interface AttitudeConfigurationService {
    List<AttitudeConfigurationDto> list(UUID actor);
    AttitudeConfigurationDto get(Long id,UUID actor);
    AttitudeConfigurationDto current(UUID actor);
    AttitudeConfigurationOptionsDto options(UUID actor);
    AttitudeConfigurationDto create(AttitudeConfigurationRequest request,UUID actor);
    AttitudeConfigurationDto update(Long id,AttitudeConfigurationRequest request,UUID actor);
    AttitudeConfigurationDto copy(Long id,UUID actor);
    AttitudeConfigurationDto publish(Long id,UUID actor);
    AttitudePeriodConfigurationDto period(Long periodId,UUID actor);
    AttitudePeriodConfigurationDto bindInitially(Long periodId,Long configurationId,UUID actor);
}
