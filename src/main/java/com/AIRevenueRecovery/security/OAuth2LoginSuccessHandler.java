package com.AIRevenueRecovery.security;

import com.AIRevenueRecovery.entity.User;
import com.AIRevenueRecovery.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public OAuth2LoginSuccessHandler(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        System.out.println(
                "========== GOOGLE LOGIN SUCCESS =========="
        );

        OAuth2User oauth2User =
                (OAuth2User) authentication.getPrincipal();

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");

        System.out.println("Google Email: " + email);
        System.out.println("Google Name: " + name);

        // Validate email
        if (email == null || email.isBlank()) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Google account email not available"
            );
            return;
        }

        // Find existing user
        User user = userRepository
                .findByEmail(email)
                .orElse(null);

        // Create user if doesn't exist
        if (user == null) {

            user = User.builder()
                    .name(
                            name != null && !name.isBlank()
                                    ? name
                                    : "Google User"
                    )
                    .email(email)
                    .password(
                            passwordEncoder.encode(
                                    UUID.randomUUID().toString()
                            )
                    )
                    .role("USER")
                    .enabled(true)
                    .build();

            user = userRepository.save(user);

            System.out.println(
                    "New Google user created: " + user.getEmail()
            );
        }

        // Generate JWT
        String token = jwtService.generateToken(user);

        System.out.println("JWT generated successfully");

        // Encode user information
        String encodedToken = URLEncoder.encode(
                token,
                StandardCharsets.UTF_8
        );

        String encodedName = URLEncoder.encode(
                user.getName(),
                StandardCharsets.UTF_8
        );

        String encodedEmail = URLEncoder.encode(
                user.getEmail(),
                StandardCharsets.UTF_8
        );

        String encodedRole = URLEncoder.encode(
                user.getRole(),
                StandardCharsets.UTF_8
        );

        // Redirect to frontend
        String redirectUrl =
                frontendUrl + "/oauth2/callback"
                        + "?token=" + encodedToken
                        + "&userId=" + user.getId()
                        + "&name=" + encodedName
                        + "&email=" + encodedEmail
                        + "&role=" + encodedRole;

        System.out.println(
                "Redirecting to: " + frontendUrl + "/oauth2/callback"
        );

        response.sendRedirect(redirectUrl);
    }
}