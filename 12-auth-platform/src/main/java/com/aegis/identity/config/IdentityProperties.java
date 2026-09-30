package com.aegis.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aegis")
public class IdentityProperties {

    private final Jwt jwt = new Jwt();
    private final Denylist denylist = new Denylist();
    private final Lockout lockout = new Lockout();

    @Getter
    @Setter
    public static class Jwt {
        private String issuer = "aegis-identity";
        private String signingSecret;
        private int accessTokenMinutes = 15;
        private int refreshTokenDays = 14;
    }

    @Getter
    @Setter
    public static class Denylist {
        private String keyPrefix = "aegis:denylist:";
    }

    @Getter
    @Setter
    public static class Lockout {
        private int maxFailedAttempts = 5;
    }
}
