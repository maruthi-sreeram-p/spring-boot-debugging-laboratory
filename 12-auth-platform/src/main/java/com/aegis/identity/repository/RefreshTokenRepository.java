package com.aegis.identity.repository;

import com.aegis.identity.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenId(String tokenId);

    List<RefreshToken> findByAccountIdOrderByIssuedAtDesc(Long accountId);
}
