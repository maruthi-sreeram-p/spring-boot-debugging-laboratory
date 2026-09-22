package com.meridian.banking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    @Size(max = 120)
    private String fullName;

    @NotBlank
    @Pattern(regexp = "NID-[0-9]{4}-[0-9]{4}", message = "must look like NID-0000-0000")
    private String nationalId;
}
