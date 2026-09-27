package com.hirestack.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CreateJobRequest {

    @NotBlank
    @Size(max = 160)
    private String title;

    @NotBlank
    @Size(max = 2000)
    private String description;

    @NotBlank
    @Size(max = 80)
    private String location;

    @NotBlank
    @Pattern(regexp = "FULL_TIME|PART_TIME|CONTRACT|INTERNSHIP")
    private String employmentType;

    private boolean remote;

    @NotNull
    @PositiveOrZero
    private Integer minExperience;

    @NotNull
    @PositiveOrZero
    private BigDecimal minSalary;

    @NotNull
    @PositiveOrZero
    private BigDecimal maxSalary;

    private LocalDate closesOn;
}
