package com.hirestack.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobPostingResponse {

    private Long id;
    private String reference;
    private Long companyId;
    private String companyName;
    private String title;
    private String description;
    private String location;
    private String employmentType;
    private boolean remote;
    private Integer minExperience;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private String status;
    private Instant createdAt;
    private LocalDate closesOn;
}
