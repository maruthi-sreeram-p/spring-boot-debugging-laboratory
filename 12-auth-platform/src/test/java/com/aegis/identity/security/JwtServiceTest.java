package com.aegis.identity.security;

import com.aegis.identity.config.IdentityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        IdentityProperties properties = new IdentityProperties();
        properties.getJwt().setIssuer("aegis-identity");
        properties.getJwt().setSigningSecret("unit-test-signing-secret-long-enough-for-hs256");
        jwtService = new JwtService(properties);
    }

    @Test
    void anAccessTokenCarriesTheIdentityAndTheGrants() {
        JwtService.IssuedToken issued = jwtService.issueAccessToken(
                42L, "editor@aegis.test", List.of("EDITOR"), List.of("document:read", "document:write"));

        Claims claims = jwtService.parse(issued.value());

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getId()).isEqualTo(issued.tokenId());
        assertThat(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class)).isEqualTo(JwtService.TYPE_ACCESS);
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo("editor@aegis.test");
        assertThat(jwtService.stringListClaim(claims, JwtService.CLAIM_ROLES)).containsExactly("EDITOR");
        assertThat(jwtService.stringListClaim(claims, JwtService.CLAIM_PERMISSIONS))
                .containsExactly("document:read", "document:write");
    }

    @Test
    void aRefreshTokenIsMarkedAsSuchAndCarriesNoGrants() {
        JwtService.IssuedToken issued = jwtService.issueRefreshToken(42L, "editor@aegis.test");

        Claims claims = jwtService.parse(issued.value());

        assertThat(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class)).isEqualTo(JwtService.TYPE_REFRESH);
        assertThat(jwtService.stringListClaim(claims, JwtService.CLAIM_PERMISSIONS)).isEmpty();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        IdentityProperties other = new IdentityProperties();
        other.getJwt().setIssuer("aegis-identity");
        other.getJwt().setSigningSecret("a-completely-different-secret-of-sufficient-length");
        String foreign = new JwtService(other).issueAccessToken(1L, "x@y.test", List.of(), List.of()).value();

        assertThatThrownBy(() -> jwtService.parse(foreign)).isInstanceOf(JwtException.class);
    }
}
