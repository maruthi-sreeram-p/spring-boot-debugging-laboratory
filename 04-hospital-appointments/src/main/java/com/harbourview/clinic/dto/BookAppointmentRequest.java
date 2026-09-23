package com.harbourview.clinic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BookAppointmentRequest {

    @NotNull
    private Long doctorId;

    @NotNull
    private Long patientId;

    @NotNull
    private LocalDateTime startTime;

    @Size(max = 300)
    private String reason;
}
