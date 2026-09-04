package com.AIRevenueRecovery.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getServletPath();

        return path.startsWith("/oauth2/")
                || path.startsWith("/login/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No JWT supplied
        if (authHeader == null || authHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization header exists but is not Bearer
        if (!authHeader.startsWith("Bearer ")) {

            if (request.getServletPath().startsWith("/api/")) {
                sendUnauthorized(
                        response,
                        "Invalid Authorization header"
                );
                return;
            }

            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7).trim();

        // Empty JWT
        if (jwt.isEmpty()) {
            sendUnauthorized(
                    response,
                    "JWT token is missing"
            );
            return;
        }

        try {

            // Extract email from JWT subject
            String email = jwtService.extractUsername(jwt);

            if (email == null || email.isBlank()) {
                sendUnauthorized(
                        response,
                        "Invalid JWT token"
                );
                return;
            }

            /*
             * Authenticate only when there is no existing
             * authentication in SecurityContext.
             */
            if (SecurityContextHolder
                    .getContext()
                    .getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(email);

                /*
                 * Validate JWT against the user.
                 */
                boolean valid =
                        jwtService.isTokenValid(
                                jwt,
                                userDetails.getUsername()
                        );

                if (!valid) {

                    System.out.println(
                            "JWT validation failed for user: " + email
                    );

                    sendUnauthorized(
                            response,
                            "Invalid or expired JWT token"
                    );
                    return;
                }

                /*
                 * JWT is valid.
                 * Create authenticated SecurityContext.
                 */
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);

                System.out.println(
                        "JWT authentication successful for: " + email
                );
            }

            // Continue request
            filterChain.doFilter(request, response);

        } catch (Exception e) {

            System.out.println(
                    "JWT authentication failed: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + e.getMessage()
            );

            if (request.getServletPath().startsWith("/api/")) {

                SecurityContextHolder.clearContext();

                sendUnauthorized(
                        response,
                        "Authentication failed"
                );
                return;
            }

            filterChain.doFilter(request, response);
        }
    }

    private void sendUnauthorized(
            HttpServletResponse response,
            String message
    ) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
                "{\"error\":\"" + message + "\"}"
        );
    }
}