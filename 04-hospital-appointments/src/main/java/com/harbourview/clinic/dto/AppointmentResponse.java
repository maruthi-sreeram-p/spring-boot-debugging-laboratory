package com.harbourview.clinic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {

    private Long id;
    private String reference;
    private Long doctorId;
    private String doctorName;
    private Long patientId;
    private String patientName;
    private String patientMrn;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String reason;
    private String cancellationReason;
}
