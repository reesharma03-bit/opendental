package com.clinic.opendental.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first Admin when there are no users yet, from AUTH_BOOTSTRAP_ADMIN_EMAIL
 * and AUTH_BOOTSTRAP_ADMIN_PASSWORD. That account must change its password at first
 * sign-in; after that the environment variables can be removed.
 */
@Component
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    private final String name;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwords,
                          @Value("${auth.bootstrap-admin-email:}") String email,
                          @Value("${auth.bootstrap-admin-password:}") String password,
                          @Value("${auth.bootstrap-admin-name:Practice Admin}") String name) {
        this.users = users;
        this.passwords = passwords;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (users.count() > 0) return;
            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                log.warn("No dashboard users exist yet. Set AUTH_BOOTSTRAP_ADMIN_EMAIL and AUTH_BOOTSTRAP_ADMIN_PASSWORD "
                        + "and restart to create the first Admin.");
                return;
            }
            UserService.checkPassword(password, email);
            users.save(AppUser.builder()
                    .email(email.trim())
                    .fullName(name)
                    .role(Role.ADMIN)
                    .passwordHash(passwords.encode(password))
                    .active(true)
                    .mustChangePassword(true)
                    .build());
            log.info("Created the first Admin ({}). They must change the password at first sign-in.", email.trim());
        } catch (Exception e) {
            log.error("Could not create the first Admin: {}. Has supabase-app-users.sql been run?", e.getMessage());
        }
    }
}
