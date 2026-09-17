package com.commercecore.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Solves the bootstrap paradox (promoting a user to ADMIN normally requires an ADMIN token, but
 * there isn't one yet on a fresh database). Runs once on startup: if no ADMIN exists and
 * ADMIN_EMAIL/ADMIN_PASSWORD are both set, creates one. Deliberately does NOT fall back to a
 * guessable default password - an operator who wants this must supply real credentials.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapRunner(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${ADMIN_EMAIL:}") String adminEmail,
                                 @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            log.warn("No ADMIN user exists yet. Set ADMIN_EMAIL and ADMIN_PASSWORD to bootstrap one "
                    + "automatically on next startup, or promote an existing user once you have any admin "
                    + "via PATCH /api/users/{{id}}/role.");
            return;
        }

        if (userRepository.existsByEmail(adminEmail)) {
            User existing = userRepository.findByEmail(adminEmail).orElseThrow();
            existing.setRole(Role.ADMIN);
            userRepository.save(existing);
            log.info("Promoted existing user {} to ADMIN", adminEmail);
            return;
        }

        User admin = new User(adminEmail, passwordEncoder.encode(adminPassword), "Admin", "Bootstrap", Role.ADMIN);
        userRepository.save(admin);
        log.info("Bootstrap admin created: {}", adminEmail);
    }
}
