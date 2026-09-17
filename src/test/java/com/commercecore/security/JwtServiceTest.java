package com.commercecore.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No collaborators here (JwtService takes only config values in its constructor), so there's
 * nothing for Mockito to mock - this is a plain unit test of the token logic itself.
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-not-for-production-use-only-min-256-bits";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60_000);
    }

    @Test
    void generateToken_thenExtractUsername_roundTrips() {
        String token = jwtService.generateToken("alice@example.com", "CUSTOMER");

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice@example.com");
    }

    @Test
    void isTokenValid_returnsTrue_forFreshTokenMatchingSubject() {
        String token = jwtService.generateToken("alice@example.com", "CUSTOMER");

        assertThat(jwtService.isTokenValid(token, "alice@example.com")).isTrue();
    }

    @Test
    void isTokenValid_returnsFalse_whenSubjectDoesNotMatch() {
        String token = jwtService.generateToken("alice@example.com", "CUSTOMER");

        assertThat(jwtService.isTokenValid(token, "someone-else@example.com")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forMalformedToken() {
        assertThat(jwtService.isTokenValid("not-a-real-token", "alice@example.com")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forExpiredToken() {
        JwtService alreadyExpired = new JwtService(SECRET, -60_000);
        String token = alreadyExpired.generateToken("alice@example.com", "CUSTOMER");

        assertThat(alreadyExpired.isTokenValid(token, "alice@example.com")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forTokenSignedWithADifferentSecret() {
        JwtService otherService = new JwtService("a-completely-different-secret-key-also-256-bits-long", 60_000);
        String token = otherService.generateToken("alice@example.com", "CUSTOMER");

        assertThat(jwtService.isTokenValid(token, "alice@example.com")).isFalse();
    }

    @Test
    void extractUsername_throws_forTokenSignedWithADifferentSecret() {
        JwtService otherService = new JwtService("a-completely-different-secret-key-also-256-bits-long", 60_000);
        String token = otherService.generateToken("alice@example.com", "CUSTOMER");

        assertThatThrownBy(() -> jwtService.extractUsername(token)).isInstanceOf(JwtException.class);
    }
}
