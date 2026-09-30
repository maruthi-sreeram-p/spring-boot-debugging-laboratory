package com.athenaeum.lending.repository;

import com.athenaeum.lending.entity.Reservation;
import com.athenaeum.lending.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByBookIdAndStatusOrderByQueuePositionAsc(Long bookId, ReservationStatus status);

    Optional<Reservation> findFirstByBookIdAndStatusOrderByQueuePositionAsc(Long bookId, ReservationStatus status);

    List<Reservation> findByMemberIdOrderByPlacedAtDesc(Long memberId);

    boolean existsByBookIdAndMemberIdAndStatus(Long bookId, Long memberId, ReservationStatus status);

    long countByBookIdAndStatus(Long bookId, ReservationStatus status);

    List<Reservation> findByStatus(ReservationStatus status);
}
