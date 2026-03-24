package com.mykb.server.auth.service;

import com.mykb.server.auth.dto.AuthResponse;
import com.mykb.server.auth.dto.LoginRequest;
import com.mykb.server.auth.dto.RegisterRequest;
import com.mykb.server.auth.dto.UserProfileResponse;
import com.mykb.server.auth.entity.UserAccount;
import com.mykb.server.auth.repository.UserAccountRepository;
import com.mykb.server.common.exception.AppException;
import com.mykb.server.common.security.JwtTokenProvider;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  public AuthService(
      UserAccountRepository userAccountRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.userAccountRepository = userAccountRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    String username = normalize(request.username());
    String email = normalize(request.email());

    if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
      throw new AppException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
    }
    if (userAccountRepository.existsByEmailIgnoreCase(email)) {
      throw new AppException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "邮箱已存在");
    }

    UserAccount userAccount = new UserAccount();
    userAccount.setUsername(username);
    userAccount.setEmail(email);
    userAccount.setPasswordHash(passwordEncoder.encode(request.password()));
    UserAccount savedUser = userAccountRepository.save(userAccount);

    log.info("User registered. userId={}, email={}", savedUser.getId(), savedUser.getEmail());
    return toAuthResponse(savedUser);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    String identity = normalize(request.identity());
    UserAccount userAccount =
        userAccountRepository
            .findByUsernameIgnoreCase(identity)
            .or(() -> userAccountRepository.findByEmailIgnoreCase(identity))
            .orElseThrow(
                () -> new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误"));

    if (!passwordEncoder.matches(request.password(), userAccount.getPasswordHash())) {
      throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误");
    }

    log.info("User login succeeded. userId={}", userAccount.getId());
    return toAuthResponse(userAccount);
  }

  @Transactional(readOnly = true)
  public UserProfileResponse getProfile(UUID userId) {
    UserAccount userAccount =
        userAccountRepository
            .findById(userId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
    return toUserProfile(userAccount);
  }

  private AuthResponse toAuthResponse(UserAccount userAccount) {
    return new AuthResponse(
        jwtTokenProvider.generateToken(userAccount), toUserProfile(userAccount));
  }

  private UserProfileResponse toUserProfile(UserAccount userAccount) {
    return new UserProfileResponse(
        userAccount.getId(), userAccount.getUsername(), userAccount.getEmail());
  }

  private String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }
}
