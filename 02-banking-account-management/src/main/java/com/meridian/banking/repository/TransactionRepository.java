package com.meridian.banking.repository;

import com.meridian.banking.entity.AccountTransaction;
import com.meridian.banking.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface TransactionRepository extends JpaRepository<AccountTransaction, Long> {

    Page<AccountTransaction> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    Page<AccountTransaction> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long accountId, Instant from, Instant to, Pageable pageable);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from AccountTransaction t
            where t.account.id = :accountId
              and t.type = :type
              and t.createdAt >= :since
            """)
    BigDecimal sumByTypeSince(@Param("accountId") Long accountId,
                              @Param("type") TransactionType type,
                              @Param("since") Instant since);

    boolean existsByReference(String reference);
}
