package com.athenaeum.lending.repository;

import com.athenaeum.lending.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByMembershipNumber(String membershipNumber);

    Optional<Member> findByEmail(String email);

    /**
     * Keeps the cached borrowing count in step with the loans table.
     */
    @Modifying
    @Query("update Member m set m.activeLoanCount = :count where m.id = :memberId")
    int updateActiveLoanCount(@Param("memberId") Long memberId, @Param("count") int count);
}
