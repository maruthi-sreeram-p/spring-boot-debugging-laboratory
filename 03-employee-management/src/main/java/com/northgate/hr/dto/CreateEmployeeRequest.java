package com.northgate.hr.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CreateEmployeeRequest {

    @NotBlank
    @Size(max = 60)
    private String firstName;

    @NotBlank
    @Size(max = 60)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 160)
    private String email;

    @Size(max = 24)
    private String phone;

    @NotBlank
    @Size(max = 80)
    private String jobTitle;

    @NotNull
    private Long departmentId;

    private Long managerId;

    @NotNull
    @PastOrPresent
    private LocalDate hireDate;

    @NotNull
    @PositiveOrZero
    private BigDecimal salary;
}
