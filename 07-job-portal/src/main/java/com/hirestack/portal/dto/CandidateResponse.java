package com.hirestack.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandidateResponse {

    private Long id;
    private String fullName;
    private String email;
    private String headline;
    private String location;
    private Integer yearsExperience;
    private String skills;
    private BigDecimal expectedSalary;
}
