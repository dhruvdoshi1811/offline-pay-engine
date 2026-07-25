package com.dhruv.offlinepayment_relay.config;

import com.dhruv.offlinepayment_relay.entity.Role;
import com.dhruv.offlinepayment_relay.entity.User;
import com.dhruv.offlinepayment_relay.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email:admin@demo.local}") String adminEmail,
            @Value("${app.admin.password:change-me-admin}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        User admin = User.builder()
                .id(UUID.randomUUID())
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);
    }
}
