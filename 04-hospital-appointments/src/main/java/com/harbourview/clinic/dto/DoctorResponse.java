package com.harbourview.clinic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoctorResponse {

    private Long id;
    private String code;
    private String fullName;
    private String specialty;
    private Integer consultationMinutes;
    private boolean active;
}
