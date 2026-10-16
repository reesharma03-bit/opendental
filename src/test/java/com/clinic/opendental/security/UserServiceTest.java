package com.clinic.opendental.security;

import com.clinic.opendental.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Signing in, lock-out and the last-Admin rule. */
class UserServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AppUserRepository users;
    private AuditService audit;
    private UserService service;
    private AppUser jane;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        audit = mock(AuditService.class);
        service = new UserService(users, mock(com.clinic.opendental.repository.ClinicRepository.class), encoder, audit, 3, 15);
        jane = AppUser.builder().id(UUID.randomUUID()).email("jane@clinic.com").fullName("Jane")
                .role(Role.FRONT_DESK).active(true).passwordHash(encoder.encode("Correct-horse-42")).build();
        when(users.findByEmail("jane@clinic.com")).thenReturn(Optional.of(jane));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aDentistSignsUpWithAHashedPasswordAndTheDentistRole() {
        AppUser created = service.signUpDentist("  Dr Mehta ", " dr.mehta@yourclinic.com ", "Smile-Clinic-2026", null);

        assertThat(created.getRole()).isEqualTo(Role.DENTIST);
        assertThat(created.getEmail()).isEqualTo("dr.mehta@yourclinic.com");
        assertThat(created.getFullName()).isEqualTo("Dr Mehta");
        assertThat(created.getClinicId()).isNull(); // they register (own) their clinics next
        assertThat(created.isActive()).isTrue();
        assertThat(created.getPasswordHash()).doesNotContain("Smile-Clinic-2026").startsWith("$2");
        assertThat(encoder.matches("Smile-Clinic-2026", created.getPasswordHash())).isTrue();
        verify(audit).record(eq("DENTIST_SIGNED_UP"), any(), any(), eq("dr.mehta@yourclinic.com"), any());
    }

    @Test
    void signUpRefusesATakenEmailAWeakPasswordAndAMissingName() {
        when(users.findByEmail("JANE@clinic.com")).thenReturn(Optional.of(jane)); // the lookup ignores case
        assertThatThrownBy(() -> service.signUpDentist("Jane Again", "JANE@clinic.com", "Smile-Clinic-2026", null))
                .isInstanceOf(ApiException.class).hasMessageContaining("already exists");
        assertThatThrownBy(() -> service.signUpDentist("Dr New", "new@clinic.com", "short1", null)).hasMessageContaining("12");
        assertThatThrownBy(() -> service.signUpDentist(" ", "new@clinic.com", "Smile-Clinic-2026", null)).hasMessageContaining("name");
        assertThatThrownBy(() -> service.signUpDentist("Dr New", "not-an-email", "Smile-Clinic-2026", null)).hasMessageContaining("email");
        verify(users, never()).save(argThat(u -> u != null && u.getRole() == Role.DENTIST));
    }

    @Test
    void rightPasswordSignsInAndResetsTheCounter() {
        jane.setFailedAttempts(2);

        AppUser user = service.signIn("jane@clinic.com", "Correct-horse-42", null);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(audit).record(eq("LOGIN"), isNull(), eq(jane.getId()), eq("jane@clinic.com"), isNull());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameMessage() {
        assertThatThrownBy(() -> service.signIn("jane@clinic.com", "wrong-password-1", null))
                .hasMessage("Email or password is incorrect.");
        assertThatThrownBy(() -> service.signIn("nobody@clinic.com", "wrong-password-1", null))
                .hasMessage("Email or password is incorrect.");
    }

    @Test
    void repeatedFailuresLockTheAccount() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.signIn("jane@clinic.com", "wrong-password-1", null));
        }

        assertThat(jane.getLockedUntil()).isAfter(LocalDateTime.now());
        assertThatThrownBy(() -> service.signIn("jane@clinic.com", "Correct-horse-42", null))
                .hasMessageContaining("Too many failed attempts");
    }

    @Test
    void inactiveAccountsCannotSignIn() {
        jane.setActive(false);

        assertThatThrownBy(() -> service.signIn("jane@clinic.com", "Correct-horse-42", null))
                .hasMessage("Email or password is incorrect.");
    }

    @Test
    void theLastActiveAdminCannotBeRemoved() {
        AppUser admin = AppUser.builder().id(UUID.randomUUID()).email("admin@clinic.com").fullName("Admin")
                .role(Role.ADMIN).active(true).passwordHash("h").build();
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(users.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(admin.getId(), null, "DENTIST", null, null, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("at least one active Admin");
        assertThatThrownBy(() -> service.update(admin.getId(), null, null, false, null, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("at least one active Admin");
    }
}
