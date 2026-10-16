package com.clinic.opendental.security;

import com.clinic.opendental.exception.ApiException;
import com.clinic.opendental.model.Clinic;
import com.clinic.opendental.repository.ClinicRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Which clinic a request works on: an Admin chooses any; everyone else is held to their own. */
class CurrentClinicTest {

    private static final Clinic SMITH = Clinic.builder().id(UUID.randomUUID()).clinicCode("SMITH").clinicName("Smith").isActive(true).build();
    private static final Clinic JONES = Clinic.builder().id(UUID.randomUUID()).clinicCode("JONES").clinicName("Jones").isActive(true).build();

    private final ClinicRepository clinics = mock(ClinicRepository.class);

    {
        when(clinics.findByIsActiveTrue()).thenReturn(List.of(SMITH, JONES));
        when(clinics.findByClinicCode("SMITH")).thenReturn(Optional.of(SMITH));
        when(clinics.findByClinicCode("JONES")).thenReturn(Optional.of(JONES));
        when(clinics.findById(SMITH.getId())).thenReturn(Optional.of(SMITH));
        when(clinics.findById(JONES.getId())).thenReturn(Optional.of(JONES));
    }

    @AfterEach
    void signOut() {
        TestSession.clear();
    }

    @Test
    void anAdminWorksOnTheClinicChosenInTheSwitcher() {
        TestSession.signIn(Role.ADMIN, null, "JONES");
        assertThat(CurrentClinic.require(clinics)).isSameAs(JONES);
    }

    @Test
    void anAdminWhoChoseNothingWorksOnTheFirstConnectedClinic() {
        TestSession.signIn(Role.ADMIN, null);
        assertThat(CurrentClinic.require(clinics)).isSameAs(SMITH);
    }

    @Test
    void aDentistWorksOnTheirOwnClinicsOnly() {
        UUID dentist = TestSession.signIn(Role.DENTIST, null, "JONES");
        Clinic mine = Clinic.builder().id(UUID.randomUUID()).clinicCode("MINE").clinicName("Mine").isActive(true).ownerUserId(dentist).build();
        Clinic second = Clinic.builder().id(UUID.randomUUID()).clinicCode("MINE2").clinicName("Mine 2").isActive(true).ownerUserId(dentist).build();
        when(clinics.findByClinicCode("MINE2")).thenReturn(Optional.of(second));
        when(clinics.findByOwnerUserIdOrderByCreatedAtAsc(dentist)).thenReturn(List.of(mine, second));

        // Another dentist's clinic: refused, even when asked for by its code.
        assertThatThrownBy(() -> CurrentClinic.require(clinics)).isInstanceOf(ApiException.class)
                .hasMessageContaining("don't have access to clinic JONES");
        TestSession.clear();

        signInAs(dentist, "MINE2");
        assertThat(CurrentClinic.require(clinics)).isSameAs(second);
        TestSession.clear();

        signInAs(dentist, null); // nothing chosen: their first connected clinic
        assertThat(CurrentClinic.require(clinics)).isSameAs(mine);
    }

    private static void signInAs(UUID dentist, String chosen) {
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
                .header("alg", "none").subject(dentist.toString()).claim("role", "DENTIST").claim("clinic", "").build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt, List.of()));
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        if (chosen != null) request.addHeader(CurrentClinic.HEADER, chosen);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
    }

    @Test
    void staffAreHeldToTheirOwnClinicWhateverTheySend() {
        for (Role role : List.of(Role.FRONT_DESK, Role.BILLING, Role.READ_ONLY)) {
            TestSession.signIn(role, SMITH.getId(), "JONES");
            assertThat(CurrentClinic.require(clinics)).as(role.name()).isSameAs(SMITH);
            TestSession.clear();
        }
        verify(clinics, never()).findByClinicCode("JONES");
    }

    @Test
    void withoutAClinicTheUserIsToldWhatToDo() {
        UUID dentist = TestSession.signIn(Role.DENTIST, null);
        when(clinics.findByOwnerUserIdOrderByCreatedAtAsc(dentist)).thenReturn(List.of());
        assertThatThrownBy(() -> CurrentClinic.require(clinics)).isInstanceOf(ApiException.class)
                .hasMessageContaining("Register your clinic");
        TestSession.clear();

        TestSession.signIn(Role.FRONT_DESK, null);
        assertThatThrownBy(() -> CurrentClinic.require(clinics)).hasMessageContaining("isn't linked to a clinic");
    }

    @Test
    void withoutASignedInUserThereIsNoClinic() {
        // Background jobs and webhooks name their clinic themselves; nothing falls back to "some" clinic.
        assertThat(CurrentClinic.find(clinics)).isEmpty();
    }

    @Test
    void anUnknownClinicChosenByAnAdminIsAnError() {
        TestSession.signIn(Role.ADMIN, null, "NOPE");
        when(clinics.findByClinicCode("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> CurrentClinic.require(clinics)).hasMessageContaining("no clinic NOPE");
    }
}
