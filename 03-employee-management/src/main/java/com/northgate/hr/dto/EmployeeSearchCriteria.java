package com.northgate.hr.dto;

import com.northgate.hr.entity.EmploymentStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeSearchCriteria {

    private Long departmentId;
    private EmploymentStatus status;
    private String jobTitle;
    private String term;
}
