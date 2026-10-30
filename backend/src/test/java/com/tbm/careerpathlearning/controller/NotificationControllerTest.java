package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.NotificationListDto;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.TokenService;
import com.tbm.careerpathlearning.service.ValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The bell works on the signed-in person's own notifications only (NFR-016). */
@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(NotificationControllerTest.MethodSecurity.class)
class NotificationControllerTest {

    /** The web slice does not load SecurityConfig, which switches on @PreAuthorize in the app; switch it on here too. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private NotificationService notificationService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private ValidationService validationService;

    private final UUID me = UUID.randomUUID();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("The bell lists the signed-in person's notifications")
    void mine() throws Exception {
        NotificationListDto dto = new NotificationListDto();
        dto.setUnread(2);
        dto.setItems(List.of());
        when(notificationService.mine(me)).thenReturn(dto);

        mockMvc.perform(as(get("/api/notifications"), "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2));
    }

    @Test
    @DisplayName("Marking as read acts for the signed-in person")
    void markRead() throws Exception {
        mockMvc.perform(as(post("/api/notifications/5/read"), "ROLE_USER")).andExpect(status().isNoContent());
        mockMvc.perform(as(post("/api/notifications/read-all"), "ROLE_USER")).andExpect(status().isNoContent());

        verify(notificationService).markRead(5L, me);
        verify(notificationService).markAllRead(me);
    }

    @Test
    @DisplayName("Without a user role the bell is refused")
    void refusedWithoutUserRole() throws Exception {
        mockMvc.perform(as(get("/api/notifications"))).andExpect(status().isForbidden());

        verifyNoInteractions(notificationService);
    }

    /** Signs the request in with the given permissions, the way the JWT filter does. */
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String... authorities) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(me.toString(), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return request.principal(auth);
    }
}
