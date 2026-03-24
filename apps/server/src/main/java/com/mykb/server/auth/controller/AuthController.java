package com.mykb.server.auth.controller;

import com.mykb.server.auth.dto.AuthResponse;
import com.mykb.server.auth.dto.LoginRequest;
import com.mykb.server.auth.dto.RegisterRequest;
import com.mykb.server.auth.dto.UserProfileResponse;
import com.mykb.server.auth.service.AuthService;
import com.mykb.server.common.api.ApiResponse;
import com.mykb.server.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(authService.register(request)));
  }

  @PostMapping("/login")
  public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(authService.login(request));
  }

  @GetMapping("/me")
  public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
    return ApiResponse.success(authService.getProfile(user.userId()));
  }
}
