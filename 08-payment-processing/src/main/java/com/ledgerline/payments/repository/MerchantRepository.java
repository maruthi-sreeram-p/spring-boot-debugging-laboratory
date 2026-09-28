package com.ledgerline.payments.repository;

import com.ledgerline.payments.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByCode(String code);
}
