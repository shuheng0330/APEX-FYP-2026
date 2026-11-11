package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttitudeAssignmentFeatureFlagTest {
    @Configuration(proxyBeanMethods=false) @EnableMethodSecurity @Import(AttitudeAssignmentService.class)
    static class Config {}

    ApplicationContextRunner context(AttitudeAssignmentServiceTest f) {
        return new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withBean(com.tbm.careerpathlearning.repository.AnnualKpiReviewPeriodRepository.class,()->f.periods)
                .withBean(com.tbm.careerpathlearning.repository.ReviewPeriodRoleConfigurationRepository.class,()->f.roles)
                .withBean(com.tbm.careerpathlearning.repository.ReviewPeriodParticipantRepository.class,()->f.participants)
                .withBean(com.tbm.careerpathlearning.repository.AttitudeAssessmentRepository.class,()->f.assessments)
                .withBean(com.tbm.careerpathlearning.repository.AnnualAppraisalRepository.class,()->f.appraisals)
                .withBean(com.tbm.careerpathlearning.repository.EmployeeInsightScopeRepository.class,()->f.scopes)
                .withBean(com.tbm.careerpathlearning.repository.AttitudeConfigurationAssignmentActivityRepository.class,()->f.audit)
                .withBean(com.tbm.careerpathlearning.repository.StaffRepository.class,()->f.staff)
                .withBean(AttitudeConfigurationService.class,()->f.configurations)
                .withBean(AttitudeConfigurationBinding.class,()->new AttitudeConfigurationBinding(f.editions))
                .withBean("annualKpiReviewClock",java.time.Clock.class,java.time.Clock::systemUTC);
    }
    @Test void missingOrFalsePropertyDisablesActualSpringServiceButPreservesMethodSecurity() {
        for(var properties:List.of(new String[0],new String[]{"attitude.configuration.reassignment-enabled=false"})) {
            var f=new AttitudeAssignmentServiceTest();f.setup();
            context(f).withPropertyValues(properties).run(ctx->{
                assertThat(ctx).hasNotFailed();var service=ctx.getBean(AttitudeAssignmentService.class);
                try {
                    login(f,"ROLE_USER");
                    assertThatThrownBy(()->service.state(5L,f.actor)).isInstanceOf(AccessDeniedException.class);
                    assertThatThrownBy(()->service.update(5L,f.request(),f.actor)).isInstanceOf(AccessDeniedException.class);
                    login(f,"CAN_MANAGE_ATTITUDE_CONFIGURATION");
                    assertThatThrownBy(()->service.state(5L,f.actor)).isInstanceOf(BadRequestException.class).hasMessageContaining("currently unavailable");
                    assertThatThrownBy(()->service.update(5L,f.request(),f.actor)).isInstanceOf(BadRequestException.class).hasMessageContaining("currently unavailable");
                    verifyNoInteractions(f.periods,f.audit,f.assessments,f.configurations);
                } finally {SecurityContextHolder.clearContext();}
            });
        }
    }
    @Test void onlyExplicitTestConfigurationEnablesPreservedImplementation() {
        var f=new AttitudeAssignmentServiceTest();f.setup();
        context(f).withPropertyValues("attitude.configuration.reassignment-enabled=true").run(ctx->{
            assertThat(ctx).hasNotFailed();
            try {
                login(f,"CAN_MANAGE_ATTITUDE_CONFIGURATION");
                assertThat(ctx.getBean(AttitudeAssignmentService.class).state(5L,f.actor).canUpdate()).isTrue();
                assertThat(ctx.getBean(AttitudeAssignmentService.class).update(5L,f.request(),f.actor).period().getConfiguration().getId()).isEqualTo(2);
            } finally {SecurityContextHolder.clearContext();}
        });
    }
    private void login(AttitudeAssignmentServiceTest f,String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(f.actor,null,List.of(new SimpleGrantedAuthority(role))));
    }
}
