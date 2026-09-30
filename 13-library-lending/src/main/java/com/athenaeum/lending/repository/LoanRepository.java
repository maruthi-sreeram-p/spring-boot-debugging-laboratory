package com.athenaeum.lending.repository;

import com.athenaeum.lending.entity.Loan;
import com.athenaeum.lending.entity.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByMemberIdOrderByBorrowedAtDesc(Long memberId);

    List<Loan> findByMemberIdAndStatus(Long memberId, LoanStatus status);

    Optional<Loan> findFirstByCopyIdAndStatus(Long copyId, LoanStatus status);

    long countByMemberIdAndStatus(Long memberId, LoanStatus status);

    @Query("""
            select l
            from Loan l
              join fetch l.copy c
              join fetch c.book
              join fetch l.member
            where l.status = com.athenaeum.lending.entity.LoanStatus.ACTIVE
              and l.dueAt < :asOf
            order by l.dueAt asc
            """)
    List<Loan> findOverdue(@Param("asOf") LocalDateTime asOf);

    @Query("""
            select l
            from Loan l
              join fetch l.copy c
              join fetch c.book
              join fetch l.member
            where l.id = :loanId
            """)
    Optional<Loan> findDetailed(@Param("loanId") Long loanId);

    @Query("""
            select l
            from Loan l
              join fetch l.copy c
              join fetch c.book
              join fetch l.member
            where l.status = com.athenaeum.lending.entity.LoanStatus.ACTIVE
            order by l.dueAt asc
            """)
    List<Loan> findAllActive();
}
