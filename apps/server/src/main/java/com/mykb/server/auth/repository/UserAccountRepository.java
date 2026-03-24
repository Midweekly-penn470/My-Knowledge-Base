package com.mykb.server.auth.repository;

import com.mykb.server.auth.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  boolean existsByUsernameIgnoreCase(String username);

  boolean existsByEmailIgnoreCase(String email);

  Optional<UserAccount> findByUsernameIgnoreCase(String username);

  Optional<UserAccount> findByEmailIgnoreCase(String email);
}
