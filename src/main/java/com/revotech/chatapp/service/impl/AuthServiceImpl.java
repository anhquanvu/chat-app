package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.exception.AppException;
import com.revotech.chatapp.model.dto.auth.JwtAuthResponse;
import com.revotech.chatapp.model.dto.auth.LoginRequest;
import com.revotech.chatapp.model.dto.auth.SignUpRequest;
import com.revotech.chatapp.model.entity.Role;
import com.revotech.chatapp.model.entity.User;
import com.revotech.chatapp.model.enums.RoleName;
import com.revotech.chatapp.repository.RoleRepository;
import com.revotech.chatapp.repository.UserRepository;
import com.revotech.chatapp.security.JwtTokenUtil;
import com.revotech.chatapp.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    public JwtAuthResponse signUp(SignUpRequest signUpRequest) {
        // Check if username already exists
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            throw new AppException("Username is already taken!");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new AppException("Email is already in use!");
        }

        // Get user role
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new AppException("User Role not set"));

        // Create new user
        User user = User.builder()
                .username(signUpRequest.getUsername())
                .email(signUpRequest.getEmail())
                .password(passwordEncoder.encode(signUpRequest.getPassword()))
                .fullName(signUpRequest.getFullName())
                .phoneNumber(signUpRequest.getPhoneNumber())
                .enabled(true)
                .isOnline(false)
                .roles(Set.of(userRole))
                .build();

        user = userRepository.save(user);

        log.info("New user registered: {}", user.getUsername());

        // Generate tokens
        String accessToken = jwtTokenUtil.generateAccessToken(user.getUsername());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getUsername());

        return JwtAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }

    @Override
    public JwtAuthResponse signIn(LoginRequest loginRequest) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        // Get user details
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new AppException("User not found"));

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        user.setIsOnline(true);
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtTokenUtil.generateAccessToken(user.getUsername());
        String refreshToken = jwtTokenUtil.generateRefreshToken(user.getUsername());

        log.info("User logged in: {}", user.getUsername());

        return JwtAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }

    @Override
    public JwtAuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenUtil.validateToken(refreshToken)) {
            throw new AppException("Invalid refresh token");
        }

        String username = jwtTokenUtil.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException("User not found"));

        // Generate new access token
        String newAccessToken = jwtTokenUtil.generateAccessToken(username);

        return JwtAuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }

    @Override
    public void logout(String token) {
        try {
            String username = jwtTokenUtil.getUsernameFromToken(token);
            User user = userRepository.findByUsername(username).orElse(null);

            if (user != null) {
                user.setIsOnline(false);
                user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);

                log.info("User logged out: {}", username);
            }

            // Add token to blacklist (implement token blacklist if needed)
            // jwtTokenUtil.blacklistToken(token);

        } catch (Exception e) {
            log.warn("Error during logout: {}", e.getMessage());
        }
    }

    @Override
    public void logoutAllDevices(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found"));

        user.setIsOnline(false);
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);

        // Invalidate all tokens for this user (implement token store if needed)
        // jwtTokenUtil.blacklistAllUserTokens(userId);

        log.info("All devices logged out for user: {}", user.getUsername());
    }
}