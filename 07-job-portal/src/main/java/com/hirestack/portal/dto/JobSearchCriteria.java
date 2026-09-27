package com.hirestack.portal.dto;

import com.hirestack.portal.entity.EmploymentType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class JobSearchCriteria {

    private String q;
    private String location;
    private EmploymentType employmentType;
    private Long companyId;
    private BigDecimal minSalary;
    private Integer maxExperience;
    private boolean remote;
}
