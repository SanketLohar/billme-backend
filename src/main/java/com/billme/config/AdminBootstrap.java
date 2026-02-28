package com.billme.config;

import com.billme.user.Role;
import com.billme.user.User;
import com.billme.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        String bootstrapEnabled = System.getenv("ADMIN_BOOTSTRAP");

        if (!"true".equalsIgnoreCase(bootstrapEnabled)) {
            return;
        }

        // 🔐 If ANY admin already exists → do nothing
        if (userRepository.existsByRole(Role.ADMIN)) {
            System.out.println("Admin already exists. Skipping bootstrap.");
            return;
        }

        String email = System.getenv("ADMIN_EMAIL");
        String password = System.getenv("ADMIN_PASSWORD");

        if (email == null || password == null) {
            System.out.println("Admin credentials not provided.");
            return;
        }

        User admin = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .active(true)
                .build();

        userRepository.save(admin);

        System.out.println("Secure Admin created");
    }
}