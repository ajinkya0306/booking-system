package com.techpulse.booking.security;

import com.techpulse.booking.entity.AppUser;
import com.techpulse.booking.entity.Role;
import com.techpulse.booking.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Seeds demo users on first start (idempotent - safe across restarts). */
@Component
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing("admin", "admin123", Role.ADMIN);
        createIfMissing("user1", "user123", Role.USER);
        createIfMissing("user2", "user123", Role.USER);
    }

    private void createIfMissing(String username, String rawPassword, Role role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(new AppUser(username, passwordEncoder.encode(rawPassword), role));
        }
    }
}
