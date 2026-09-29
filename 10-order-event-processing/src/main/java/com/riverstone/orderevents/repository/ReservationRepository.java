package com.riverstone.orderevents.repository;

import com.riverstone.orderevents.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByOrderRefOrderByCreatedAtAsc(String orderRef);
}
