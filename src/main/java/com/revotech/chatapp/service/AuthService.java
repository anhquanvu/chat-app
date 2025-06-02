package com.revotech.chatapp.service;

import com.revotech.chatapp.model.dto.auth.JwtAuthResponse;
import com.revotech.chatapp.model.dto.auth.LoginRequest;
import com.revotech.chatapp.model.dto.auth.SignUpRequest;

public interface AuthService {
    JwtAuthResponse signUp(SignUpRequest signUpRequest);
    JwtAuthResponse signIn(LoginRequest loginRequest);
    JwtAuthResponse refreshToken(String refreshToken);
    void logout(String token);
    void logoutAllDevices(Long userId);
}