package com.clinic.opendental.security;

import com.clinic.opendental.controller.AssistantController;
import com.clinic.opendental.controller.DashboardController;
import com.clinic.opendental.security.PermissionResolver.Requirement;
import com.clinic.opendental.service.Impl.AssistantService;
import com.clinic.opendental.service.Impl.DashboardService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may call what. */
class SecurityRulesTest {

    private final PermissionResolver rules = new PermissionResolver();

    private Requirement need(String method, String path) {
        return rules.required(method, path);
    }

    @Test
    void readsNeedReadAndChangesNeedWrite() {
        assertThat(need("GET", "/api/patients/database").permission()).isEqualTo(Permission.PATIENTS_READ);
        assertThat(need("POST", "/api/patients/database").permission()).isEqualTo(Permission.PATIENTS_WRITE);
        assertThat(need("GET", "/api/database/allergies").permission()).isEqualTo(Permission.CLINICAL_READ);
        assertThat(need("DELETE", "/api/database/allergies/501").permission()).isEqualTo(Permission.CLINICAL_WRITE);
        assertThat(need("PUT", "/api/database/claims/9").permission()).isEqualTo(Permission.BILLING_WRITE);
        assertThat(need("GET", "/api/appointments/database").permission()).isEqualTo(Permission.APPOINTMENTS_READ);
        assertThat(need("GET", "/api/carriers").permission()).isEqualTo(Permission.BILLING_READ);
    }

    @Test
    void adminAreasAndUnknownRoutesNeedAdmin() {
        assertThat(need("GET", "/api/users").permission()).isEqualTo(Permission.USERS_MANAGE);
        assertThat(need("GET", "/api/audit").permission()).isEqualTo(Permission.USERS_MANAGE);
        assertThat(need("POST", "/api/sync/force").permission()).isEqualTo(Permission.SYNC_MANAGE);
        assertThat(need("GET", "/api/database/subscriptions").permission()).isEqualTo(Permission.SYNC_MANAGE);
        assertThat(need("POST", "/api/queries").permission()).isEqualTo(Permission.SYSTEM_ADMIN);
        assertThat(need("GET", "/api/something-new").permission()).isEqualTo(Permission.SYSTEM_ADMIN);
        assertThat(need("GET", "/swagger-ui/index.html").permission()).isEqualTo(Permission.SYSTEM_ADMIN);
    }

    @Test
    void onlySignInAndOpenDentalWebhooksArePublic() {
        assertThat(need("POST", "/api/auth/login").kind()).isEqualTo(Requirement.Kind.PUBLIC);
        assertThat(need("POST", "/api/webhooks/opendental/patient").kind()).isEqualTo(Requirement.Kind.PUBLIC);
        assertThat(need("GET", "/api/auth/me").kind()).isEqualTo(Requirement.Kind.AUTHENTICATED);
        assertThat(need("GET", "/api/dashboard/summary").kind()).isEqualTo(Requirement.Kind.AUTHENTICATED);
        assertThat(need("GET", "/api/sync/force/status").kind()).isEqualTo(Requirement.Kind.AUTHENTICATED);
    }

    @Test
    void rolesMatchTheAgreedResponsibilities() {
        assertThat(Role.ADMIN.permissions()).containsExactlyInAnyOrder(Permission.values());
        assertThat(Role.FRONT_DESK.permissions()).contains(Permission.PATIENTS_WRITE, Permission.APPOINTMENTS_WRITE, Permission.CLINICAL_READ)
                .doesNotContain(Permission.CLINICAL_WRITE, Permission.BILLING_READ, Permission.USERS_MANAGE);
        assertThat(Role.DENTIST.permissions()).contains(Permission.CLINICAL_WRITE, Permission.BILLING_READ)
                .doesNotContain(Permission.BILLING_WRITE, Permission.SYNC_MANAGE);
        assertThat(Role.BILLING.permissions()).contains(Permission.BILLING_WRITE, Permission.PATIENTS_READ)
                .doesNotContain(Permission.PATIENTS_WRITE, Permission.CLINICAL_READ);
        assertThat(Role.READ_ONLY.permissions()).noneMatch(p -> p.name().endsWith("_WRITE") || p.name().endsWith("_MANAGE"));
    }

    @Test
    void passwordsMustBeStrongAndNotTheEmail() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> UserService.checkPassword("short1", "a@b.co"))
                .hasMessageContaining("12");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> UserService.checkPassword("onlylettersxx", "a@b.co"))
                .hasMessageContaining("letters and numbers");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> UserService.checkPassword("jane.doe12345", "jane.doe@clinic.com"))
                .hasMessageContaining("email");
        UserService.checkPassword("Correct-horse-42", "jane@clinic.com");
    }

    /** The real filter chain in front of real controllers. */
    @Nested
    @WebMvcTest(controllers = {DashboardController.class, AssistantController.class})
    @Import({SecurityConfig.class, TokenService.class, PermissionResolver.class})
    @TestPropertySource(properties = "auth.jwt-secret=test-secret-that-is-at-least-32-characters-long")
    class FilterChain {

        @Autowired MockMvc mvc;
        @Autowired TokenService tokens;
        @MockBean AppUserRepository users;
        @MockBean AuditService audit;
        @MockBean DashboardService dashboard;
        @MockBean AssistantService assistant;

        private Cookie session(Role role, boolean mustChangePassword) {
            AppUser user = AppUser.builder().id(UUID.randomUUID()).email("x@clinic.com").fullName("X")
                    .role(role).active(true).mustChangePassword(mustChangePassword).passwordHash("h").build();
            return new Cookie(TokenService.COOKIE, tokens.issue(user));
        }

        @Test
        void withoutASessionEverythingButSignInIsRefused() throws Exception {
            mvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(get("/api/dashboard/summary").cookie(new Cookie(TokenService.COOKIE, "forged.token.value")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void aSignedInUserReachesWhatTheirRoleAllows() throws Exception {
            when(dashboard.summary(any(), anyBoolean())).thenReturn(Map.of("date", "2026-10-03"));
            when(assistant.configured()).thenReturn(true);

            mvc.perform(get("/api/dashboard/summary").cookie(session(Role.READ_ONLY, false))).andExpect(status().isOk());
            mvc.perform(get("/api/assistant/status").cookie(session(Role.FRONT_DESK, false))).andExpect(status().isOk());
        }

        @Test
        void aRoleWithoutThePermissionIsRefused() throws Exception {
            mvc.perform(post("/api/sync/force").cookie(session(Role.FRONT_DESK, false)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Your role (Front desk) doesn't allow this."));
        }

        @Test
        void aRequiredPasswordChangeBlocksEverythingElse() throws Exception {
            mvc.perform(get("/api/dashboard/summary").cookie(session(Role.ADMIN, true)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        }
    }
}
