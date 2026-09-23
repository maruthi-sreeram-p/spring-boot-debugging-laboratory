package com.harbourview.clinic.service;

import com.harbourview.clinic.config.ClinicProperties;
import com.harbourview.clinic.dto.AppointmentResponse;
import com.harbourview.clinic.dto.BookAppointmentRequest;
import com.harbourview.clinic.dto.CancelAppointmentRequest;
import com.harbourview.clinic.dto.PagedResponse;
import com.harbourview.clinic.entity.Appointment;
import com.harbourview.clinic.entity.AppointmentStatus;
import com.harbourview.clinic.entity.Doctor;
import com.harbourview.clinic.entity.Patient;
import com.harbourview.clinic.exception.IllegalStateTransitionException;
import com.harbourview.clinic.exception.ResourceNotFoundException;
import com.harbourview.clinic.exception.SchedulingRuleException;
import com.harbourview.clinic.exception.SlotUnavailableException;
import com.harbourview.clinic.mapper.ClinicMapper;
import com.harbourview.clinic.repository.AppointmentRepository;
import com.harbourview.clinic.repository.DoctorRepository;
import com.harbourview.clinic.repository.PatientRepository;
import com.harbourview.clinic.security.ClinicUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final ClinicMapper clinicMapper;
    private final ClinicProperties properties;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              DoctorRepository doctorRepository,
                              PatientRepository patientRepository,
                              ClinicMapper clinicMapper,
                              ClinicProperties properties) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.clinicMapper = clinicMapper;
        this.properties = properties;
    }

    @Transactional
    public AppointmentResponse book(BookAppointmentRequest request, ClinicUser principal) {
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", request.getDoctorId()));
        if (!doctor.isActive()) {
            throw new SchedulingRuleException(doctor.getFullName() + " is not taking appointments");
        }

        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", request.getPatientId()));

        LocalDateTime start = request.getStartTime();
        LocalDateTime end = start.plusMinutes(doctor.getConsultationMinutes());
        validateBookingWindow(start);
        validateWithinWorkingHours(doctor, start, end);

        List<Appointment> sameDay = appointmentRepository.findByDoctorIdAndStatusAndStartTimeBetween(
                doctor.getId(), AppointmentStatus.BOOKED,
                start.toLocalDate().atStartOfDay(), start.toLocalDate().atTime(LocalTime.MAX));

        for (Appointment existing : sameDay) {
            if (clashes(existing, start, end)) {
                throw new SlotUnavailableException(
                        doctor.getFullName() + " is already booked at " + existing.getStartTime());
            }
        }

        Appointment appointment = new Appointment();
        appointment.setReference(nextReference(start));
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setStartTime(start);
        appointment.setEndTime(end);
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setReason(request.getReason());

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Booked {} for patient {} with doctor {} at {}",
                saved.getReference(), patient.getMrn(), doctor.getCode(), start);
        return clinicMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AppointmentResponse> myAppointments(ClinicUser principal, Pageable pageable) {
        Page<Appointment> page;
        if (principal.isDoctor()) {
            page = appointmentRepository.findByDoctorIdOrderByStartTimeDesc(principal.getDoctorId(), pageable);
        } else {
            page = appointmentRepository.findByPatientIdOrderByStartTimeDesc(principal.getPatientId(), pageable);
        }
        return PagedResponse.of(page, clinicMapper.toAppointmentResponses(page.getContent()));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> daySheet(Long doctorId, LocalDate date) {
        List<Appointment> appointments = appointmentRepository
                .findByDoctorIdAndStartTimeBetweenOrderByStartTimeAsc(
                        doctorId, date.atStartOfDay(), date.atTime(LocalTime.MAX));
        return clinicMapper.toAppointmentResponses(appointments);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(Long appointmentId, ClinicUser principal) {
        Appointment appointment = loadVisibleAppointment(appointmentId, principal);
        return clinicMapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(Long appointmentId, CancelAppointmentRequest request, ClinicUser principal) {
        Appointment appointment = loadVisibleAppointment(appointmentId, principal);

        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new IllegalStateTransitionException(appointment.getStatus().name(), "CANCELLED");
        }

        if (!principal.isSchedulingDesk()) {
            Duration notice = Duration.between(LocalDateTime.now(), appointment.getStartTime());
            if (notice.toHours() < properties.getCancellation().getMinimumNoticeHours()) {
                throw new SchedulingRuleException("Appointments must be cancelled at least "
                        + properties.getCancellation().getMinimumNoticeHours() + " hours in advance");
            }
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(request.getReason());
        log.info("Cancelled {} ({})", appointment.getReference(), request.getReason());
        return clinicMapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse complete(Long appointmentId, ClinicUser principal) {
        Appointment appointment = loadVisibleAppointment(appointmentId, principal);

        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new IllegalStateTransitionException(appointment.getStatus().name(), "COMPLETED");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        log.info("Completed {}", appointment.getReference());
        return clinicMapper.toResponse(appointment);
    }

    private Appointment loadVisibleAppointment(Long appointmentId, ClinicUser principal) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        if (principal.isSchedulingDesk()) {
            return appointment;
        }
        if (principal.isDoctor() && appointment.getDoctor().getId().equals(principal.getDoctorId())) {
            return appointment;
        }
        if (principal.isPatient() && appointment.getPatient().getId().equals(principal.getPatientId())) {
            return appointment;
        }
        throw new ResourceNotFoundException("Appointment", appointmentId);
    }

    /**
     * Rejects anything already in the past or too far ahead. The clinic wants a little
     * notice so the front desk can pull the notes before the patient arrives.
     */
    private void validateBookingWindow(LocalDateTime start) {
        LocalDateTime earliest = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                .plusMinutes(properties.getBooking().getMinimumNoticeMinutes());
        if (start.isBefore(earliest)) {
            throw new SchedulingRuleException("Appointments need at least "
                    + properties.getBooking().getMinimumNoticeMinutes() + " minutes notice");
        }

        LocalDateTime latest = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                .plusDays(properties.getBooking().getMaximumDaysAhead());
        if (start.isAfter(latest)) {
            throw new SchedulingRuleException("Appointments cannot be booked more than "
                    + properties.getBooking().getMaximumDaysAhead() + " days ahead");
        }
    }

    private void validateWithinWorkingHours(Doctor doctor, LocalDateTime start, LocalDateTime end) {
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new SchedulingRuleException("An appointment cannot span two days");
        }
    }

    private boolean clashes(Appointment existing, LocalDateTime start, LocalDateTime end) {
        return !existing.getStartTime().isBefore(start) && existing.getStartTime().isBefore(end);
    }

    private String nextReference(LocalDateTime start) {
        String candidate;
        do {
            candidate = "APT-" + start.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + ThreadLocalRandom.current().nextInt(1000, 9999);
        } while (appointmentRepository.existsByReference(candidate));
        return candidate;
    }
}
