package com.hirestack.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterCandidateRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    @Size(max = 120)
    private String fullName;

    @Size(max = 24)
    private String phone;

    @NotBlank
    @Size(max = 200)
    private String headline;

    @NotBlank
    @Size(max = 80)
    private String location;

    @PositiveOrZero
    private Integer yearsExperience;

    @Size(max = 400)
    private String skills;
}
