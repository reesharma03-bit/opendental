package com.clinic.opendental.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The API reads JSON in snake_case (spring.jackson.property-naming-strategy) while the
 * dashboard sends camelCase. Multi-word fields must arrive either way: a missing
 * currentPassword made every password change fail with "current password is incorrect".
 */
class AuthRequestBindingTest {

    private final ObjectMapper api = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void passwordChangeArrivesFromTheDashboard() throws Exception {
        AuthController.ChangePasswordRequest camel = api.readValue(
                "{\"currentPassword\":\"Smile-temp123\",\"newPassword\":\"Change-Me-2317!\"}", AuthController.ChangePasswordRequest.class);
        AuthController.ChangePasswordRequest snake = api.readValue(
                "{\"current_password\":\"Smile-temp123\",\"new_password\":\"Change-Me-2317!\"}", AuthController.ChangePasswordRequest.class);

        assertThat(camel.currentPassword()).isEqualTo("Smile-temp123");
        assertThat(camel.newPassword()).isEqualTo("Change-Me-2317!");
        assertThat(snake).isEqualTo(camel);
    }

    @Test
    void userNamesArriveFromTheDashboard() throws Exception {
        assertThat(api.readValue("{\"email\":\"a@b.co\",\"fullName\":\"Dr Ada\",\"role\":\"DENTIST\",\"password\":\"x\"}",
                UserAdminController.CreateUserRequest.class).fullName()).isEqualTo("Dr Ada");
        assertThat(api.readValue("{\"fullName\":\"Dr Ada Lovelace\"}",
                UserAdminController.UpdateUserRequest.class).fullName()).isEqualTo("Dr Ada Lovelace");
    }
}
