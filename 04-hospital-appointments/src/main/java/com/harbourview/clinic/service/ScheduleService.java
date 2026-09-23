package com.harbourview.clinic.service;

import com.harbourview.clinic.dto.DaySlotsResponse;
import com.harbourview.clinic.dto.SlotResponse;
import com.harbourview.clinic.entity.Appointment;
import com.harbourview.clinic.entity.AppointmentStatus;
import com.harbourview.clinic.entity.Doctor;
import com.harbourview.clinic.entity.DoctorSchedule;
import com.harbourview.clinic.exception.ResourceNotFoundException;
import com.harbourview.clinic.repository.AppointmentRepository;
import com.harbourview.clinic.repository.DoctorRepository;
import com.harbourview.clinic.repository.DoctorScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the weekly availability rows of a doctor into the concrete slots a patient can
 * pick from on a given date, marking the ones that are already taken.
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final AppointmentRepository appointmentRepository;

    public ScheduleService(DoctorRepository doctorRepository,
                           DoctorScheduleRepository doctorScheduleRepository,
                           AppointmentRepository appointmentRepository) {
        this.doctorRepository = doctorRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public DaySlotsResponse slotsFor(Long doctorId, LocalDate date) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));

        List<DoctorSchedule> schedules =
                doctorScheduleRepository.findByDoctorIdAndDayOfWeekAndActiveTrue(doctorId, date.getDayOfWeek());

        List<Appointment> booked = appointmentRepository.findByDoctorIdAndStatusAndStartTimeBetween(
                doctorId, AppointmentStatus.BOOKED, date.atStartOfDay(), date.atTime(LocalTime.MAX));

        List<SlotResponse> slots = new ArrayList<>();
        for (DoctorSchedule schedule : schedules) {
            for (LocalTime cursor = schedule.getStartTime();
                 !cursor.isAfter(schedule.getEndTime());
                 cursor = cursor.plusMinutes(schedule.getSlotMinutes())) {

                LocalDateTime slotStart = date.atTime(cursor);
                LocalDateTime slotEnd = slotStart.plusMinutes(schedule.getSlotMinutes());
                slots.add(new SlotResponse(slotStart, slotEnd, isFree(booked, slotStart, slotEnd)));
            }
        }

        log.debug("Generated {} slot(s) for doctor {} on {}", slots.size(), doctorId, date);
        return new DaySlotsResponse(doctor.getId(), doctor.getFullName(), date,
                date.getDayOfWeek().name(), slots);
    }

    private boolean isFree(List<Appointment> booked, LocalDateTime slotStart, LocalDateTime slotEnd) {
        for (Appointment appointment : booked) {
            if (appointment.getStartTime().isBefore(slotEnd) && appointment.getEndTime().isAfter(slotStart)) {
                return false;
            }
        }
        return true;
    }
}
