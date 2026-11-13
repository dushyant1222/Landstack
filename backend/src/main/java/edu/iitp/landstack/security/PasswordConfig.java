package edu.iitp.landstack.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {
    /**
     * Argon2id with Spring Security 5.8+ defaults: 16-byte salt, 32-byte hash, parallelism 1,
     * memory 16 MiB (1 << 14 KiB), 2 iterations. Requires BouncyCastle (bcprov-jdk18on).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
