package com.hirestack.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateApplicationStatusRequest {

    @NotBlank
    @Pattern(regexp = "SUBMITTED|SHORTLISTED|INTERVIEW|OFFERED|REJECTED|WITHDRAWN")
    private String status;
}
