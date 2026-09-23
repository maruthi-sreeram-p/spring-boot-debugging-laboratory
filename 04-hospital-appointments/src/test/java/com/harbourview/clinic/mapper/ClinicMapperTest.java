package com.harbourview.clinic.mapper;

import com.harbourview.clinic.dto.AppointmentResponse;
import com.harbourview.clinic.entity.Appointment;
import com.harbourview.clinic.entity.AppointmentStatus;
import com.harbourview.clinic.entity.Doctor;
import com.harbourview.clinic.entity.Patient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicMapperTest {

    private final ClinicMapper mapper = new ClinicMapper();

    @Test
    void mapsAppointmentWithDoctorAndPatientDetails() {
        Doctor doctor = new Doctor();
        doctor.setId(1L);
        doctor.setCode("DOC-0101");
        doctor.setFullName("Dr Anita Deshpande");
        doctor.setSpecialty("General Medicine");
        doctor.setConsultationMinutes(30);

        Patient patient = new Patient();
        patient.setId(1L);
        patient.setMrn("MRN-100241");
        patient.setFullName("Rhea Sundaram");
        patient.setDateOfBirth(LocalDate.of(1991, 3, 14));

        Appointment appointment = new Appointment();
        appointment.setId(1L);
        appointment.setReference("APT-20250210-4471");
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setStartTime(LocalDateTime.of(2025, 2, 10, 9, 30));
        appointment.setEndTime(LocalDateTime.of(2025, 2, 10, 10, 0));
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setReason("Annual check-up");

        AppointmentResponse response = mapper.toResponse(appointment);

        assertThat(response.getReference()).isEqualTo("APT-20250210-4471");
        assertThat(response.getDoctorName()).isEqualTo("Dr Anita Deshpande");
        assertThat(response.getPatientMrn()).isEqualTo("MRN-100241");
        assertThat(response.getStartTime()).isEqualTo(LocalDateTime.of(2025, 2, 10, 9, 30));
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }
}
