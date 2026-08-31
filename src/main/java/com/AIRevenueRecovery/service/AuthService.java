package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.dto.AuthResponse;
import com.AIRevenueRecovery.dto.LoginRequest;
import com.AIRevenueRecovery.dto.SignupRequest;
import com.AIRevenueRecovery.entity.User;
import com.AIRevenueRecovery.repository.UserRepository;
import com.AIRevenueRecovery.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already registered");
        }
        User user = User.builder()
                .name(request.getName()).email(request.getEmail()).password(passwordEncoder.encode(request.getPassword()))
                .role("USER").enabled(true).build();
        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(savedUser);
        return AuthResponse.builder()
                .token(token).tokenType("Bearer").userId(savedUser.getId())
                .name(savedUser.getName()).email(savedUser.getEmail()).role(savedUser.getRole()).build();
    }
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new RuntimeException("User not found"));
        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token).tokenType("Bearer").userId(user.getId()).name(user.getName()).email(user.getEmail())
                .role(user.getRole()).build();
    }
}