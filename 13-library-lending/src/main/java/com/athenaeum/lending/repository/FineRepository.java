package com.athenaeum.lending.repository;

import com.athenaeum.lending.entity.Fine;
import com.athenaeum.lending.entity.FineStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {

    List<Fine> findByMemberIdOrderByAssessedAtDesc(Long memberId);

    List<Fine> findByMemberIdAndStatus(Long memberId, FineStatus status);

    Optional<Fine> findByLoanId(Long loanId);

    boolean existsByLoanId(Long loanId);
}
