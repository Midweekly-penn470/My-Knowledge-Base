package com.mykb.server.common.security;

import com.mykb.server.auth.entity.UserAccount;
import com.mykb.server.auth.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;
  private final UserAccountRepository userAccountRepository;

  public JwtAuthenticationFilter(
      JwtTokenProvider jwtTokenProvider, UserAccountRepository userAccountRepository) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.userAccountRepository = userAccountRepository;
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      String token = bearerToken.substring(7);
      jwtTokenProvider
          .parse(token)
          .ifPresent(
              parsed -> {
                UserAccount userAccount =
                    userAccountRepository.findById(parsed.userId()).orElse(null);
                if (userAccount != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                  AuthenticatedUser principal =
                      new AuthenticatedUser(userAccount.getId(), userAccount.getUsername());
                  UsernamePasswordAuthenticationToken authentication =
                      new UsernamePasswordAuthenticationToken(principal, null, List.of());
                  authentication.setDetails(
                      new WebAuthenticationDetailsSource().buildDetails(request));
                  SecurityContextHolder.getContext().setAuthentication(authentication);
                }
              });
    }
    filterChain.doFilter(request, response);
  }
}
