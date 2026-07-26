package com.hotsearch.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String STRONG_SECRET = "0123456789abcdef0123456789abcdef"; // 32 字节

    @Test
    void generateAndValidateRoundTrip() {
        JwtUtil jwtUtil = new JwtUtil(STRONG_SECRET, 60_000);

        String token = jwtUtil.generateToken(7L, "admin");

        assertThat(jwtUtil.validate(token)).isTrue();
        assertThat(jwtUtil.getUserId(token)).isEqualTo(7L);
        assertThat(jwtUtil.getUsername(token)).isEqualTo("admin");
    }

    @Test
    void rejectsTamperedToken() {
        JwtUtil jwtUtil = new JwtUtil(STRONG_SECRET, 60_000);
        String token = jwtUtil.generateToken(7L, "admin");

        assertThat(jwtUtil.validate(token + "x")).isFalse();
    }

    @Test
    void shortSecretIsUsableViaKeyDerivation() {
        JwtUtil jwtUtil = new JwtUtil("only-10ch!", 60_000);

        String token = jwtUtil.generateToken(1L, "admin");

        assertThat(jwtUtil.validate(token)).isTrue();
        assertThat(jwtUtil.getUserId(token)).isEqualTo(1L);
    }

    @Test
    void differentSecretsProduceIncompatibleTokens() {
        String token = new JwtUtil("secret-a", 60_000).generateToken(1L, "admin");

        assertThat(new JwtUtil("secret-b", 60_000).validate(token)).isFalse();
    }

    @Test
    void rejectsMissingOrDefaultSecret() {
        assertThatThrownBy(() -> new JwtUtil("", 60_000))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtUtil("changeme", 60_000))
                .isInstanceOf(IllegalStateException.class);
    }
}
