package com.harbourview.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelAppointmentRequest {

    @NotBlank
    @Size(max = 300)
    private String reason;
}
