package com.hirestack.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponse {

    private Long id;
    private Long jobPostingId;
    private String jobReference;
    private String jobTitle;
    private String companyName;
    private Long candidateId;
    private String candidateName;
    private String candidateEmail;
    private String candidateHeadline;
    private String status;
    private String coverLetter;
    private Instant appliedAt;
    private Instant updatedAt;
}
