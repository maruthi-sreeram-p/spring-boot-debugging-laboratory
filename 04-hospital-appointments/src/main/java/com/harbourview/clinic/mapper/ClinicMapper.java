package com.harbourview.clinic.mapper;

import com.harbourview.clinic.dto.AppointmentResponse;
import com.harbourview.clinic.dto.DoctorResponse;
import com.harbourview.clinic.entity.Appointment;
import com.harbourview.clinic.entity.Doctor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClinicMapper {

    public DoctorResponse toResponse(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getCode(),
                doctor.getFullName(),
                doctor.getSpecialty(),
                doctor.getConsultationMinutes(),
                doctor.isActive());
    }

    public List<DoctorResponse> toDoctorResponses(List<Doctor> doctors) {
        return doctors.stream().map(this::toResponse).toList();
    }

    public AppointmentResponse toResponse(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getReference(),
                appointment.getDoctor().getId(),
                appointment.getDoctor().getFullName(),
                appointment.getPatient().getId(),
                appointment.getPatient().getFullName(),
                appointment.getPatient().getMrn(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus().name(),
                appointment.getReason(),
                appointment.getCancellationReason());
    }

    public List<AppointmentResponse> toAppointmentResponses(List<Appointment> appointments) {
        return appointments.stream().map(this::toResponse).toList();
    }
}
