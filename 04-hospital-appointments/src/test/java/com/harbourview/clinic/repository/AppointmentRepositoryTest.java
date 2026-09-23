package com.harbourview.clinic.repository;

import com.harbourview.clinic.entity.Appointment;
import com.harbourview.clinic.entity.AppointmentStatus;
import com.harbourview.clinic.entity.Doctor;
import com.harbourview.clinic.entity.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AppointmentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Test
    void findsBookedAppointmentsForOneDoctorOnOneDay() {
        Doctor doctor = persistDoctor("DOC-9001", "Dr Test One", "test.one@harbourview.test");
        Doctor other = persistDoctor("DOC-9002", "Dr Test Two", "test.two@harbourview.test");
        Patient patient = persistPatient("MRN-900001", "patient.one@example.com");

        LocalDate day = LocalDate.of(2025, 5, 12);
        persistAppointment("APT-1", doctor, patient, day.atTime(9, 0), 30, AppointmentStatus.BOOKED);
        persistAppointment("APT-2", doctor, patient, day.atTime(11, 0), 30, AppointmentStatus.CANCELLED);
        persistAppointment("APT-3", doctor, patient, day.plusDays(1).atTime(9, 0), 30, AppointmentStatus.BOOKED);
        persistAppointment("APT-4", other, patient, day.atTime(9, 0), 30, AppointmentStatus.BOOKED);
        entityManager.flush();
        entityManager.clear();

        List<Appointment> booked = appointmentRepository.findByDoctorIdAndStatusAndStartTimeBetween(
                doctor.getId(), AppointmentStatus.BOOKED, day.atStartOfDay(), day.atTime(LocalTime.MAX));

        assertThat(booked).extracting(Appointment::getReference).containsExactly("APT-1");
    }

    private Doctor persistDoctor(String code, String name, String email) {
        Doctor doctor = new Doctor();
        doctor.setCode(code);
        doctor.setFullName(name);
        doctor.setSpecialty("General Medicine");
        doctor.setEmail(email);
        doctor.setConsultationMinutes(30);
        doctor.setActive(true);
        return entityManager.persist(doctor);
    }

    private Patient persistPatient(String mrn, String email) {
        Patient patient = new Patient();
        patient.setMrn(mrn);
        patient.setFullName("Test Patient");
        patient.setEmail(email);
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return entityManager.persist(patient);
    }

    private void persistAppointment(String reference, Doctor doctor, Patient patient,
                                    LocalDateTime start, int minutes, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setReference(reference);
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setStartTime(start);
        appointment.setEndTime(start.plusMinutes(minutes));
        appointment.setStatus(status);
        entityManager.persist(appointment);
    }
}
