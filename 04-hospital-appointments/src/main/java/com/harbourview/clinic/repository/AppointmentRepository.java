package com.harbourview.clinic.repository;

import com.harbourview.clinic.entity.Appointment;
import com.harbourview.clinic.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByDoctorIdAndStatusAndStartTimeBetween(
            Long doctorId, AppointmentStatus status, LocalDateTime from, LocalDateTime to);

    List<Appointment> findByDoctorIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long doctorId, LocalDateTime from, LocalDateTime to);

    Page<Appointment> findByPatientIdOrderByStartTimeDesc(Long patientId, Pageable pageable);

    Page<Appointment> findByDoctorIdOrderByStartTimeDesc(Long doctorId, Pageable pageable);

    List<Appointment> findByEndTimeBefore(LocalDateTime cutoff);

    boolean existsByReference(String reference);
}
