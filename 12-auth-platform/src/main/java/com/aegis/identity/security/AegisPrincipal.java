package com.aegis.identity.security;

import java.util.List;

/**
 * What the resource endpoints see once a token has been accepted. Everything here comes out of
 * the token, so no database call is needed to authorize a request.
 */
public record AegisPrincipal(Long accountId,
                             String email,
                             String accessTokenId,
                             List<String> roles,
                             List<String> permissions) {

    @Override
    public String toString() {
        return email;
    }
}
