package com.hirestack.portal.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplyRequest {

    @Size(max = 2000)
    private String coverLetter;
}
