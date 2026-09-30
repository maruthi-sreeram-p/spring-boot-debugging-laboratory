package com.aegis.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Turns a bearer token into an {@link org.springframework.security.core.Authentication}.
 *
 * A token that is missing, malformed, expired or denied simply leaves the context empty; the
 * authorization rules downstream then decide whether that matters for the requested path.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenDenylist denylist;

    public JwtAuthenticationFilter(JwtService jwtService, TokenDenylist denylist) {
        this.jwtService = jwtService;
        this.denylist = denylist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length()).trim();
        try {
            Claims claims = jwtService.parse(token);

            if (!JwtService.TYPE_ACCESS.equals(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class))) {
                log.debug("Rejecting token {}: not an access token", claims.getId());
                filterChain.doFilter(request, response);
                return;
            }

            if (denylist.contains(claims.getId())) {
                log.debug("Rejecting token {}: on the denylist", claims.getId());
                filterChain.doFilter(request, response);
                return;
            }

            List<String> roles = jwtService.stringListClaim(claims, JwtService.CLAIM_ROLES);
            List<String> permissions = jwtService.stringListClaim(claims, JwtService.CLAIM_PERMISSIONS);

            List<GrantedAuthority> authorities = Stream.concat(roles.stream(), permissions.stream())
                    .map(name -> "ROLE_" + name)
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();

            AegisPrincipal principal = new AegisPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get(JwtService.CLAIM_EMAIL, String.class),
                    claims.getId(),
                    roles,
                    permissions);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Authenticated {} with authorities {}", principal.email(), authorities);
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("Rejecting token on {}: {}", request.getRequestURI(), exception.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
