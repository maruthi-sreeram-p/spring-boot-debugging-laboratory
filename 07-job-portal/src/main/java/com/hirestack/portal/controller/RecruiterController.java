package com.hirestack.portal.controller;

import com.hirestack.portal.config.PortalProperties;
import com.hirestack.portal.dto.ApplicationResponse;
import com.hirestack.portal.dto.CandidateResponse;
import com.hirestack.portal.dto.CreateJobRequest;
import com.hirestack.portal.dto.JobPostingResponse;
import com.hirestack.portal.dto.PagedResponse;
import com.hirestack.portal.dto.UpdateApplicationStatusRequest;
import com.hirestack.portal.security.PortalUser;
import com.hirestack.portal.service.ApplicationService;
import com.hirestack.portal.service.CandidateService;
import com.hirestack.portal.service.JobService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recruiter")
public class RecruiterController {

    private final JobService jobService;
    private final ApplicationService applicationService;
    private final CandidateService candidateService;
    private final PortalProperties properties;

    public RecruiterController(JobService jobService,
                               ApplicationService applicationService,
                               CandidateService candidateService,
                               PortalProperties properties) {
        this.jobService = jobService;
        this.applicationService = applicationService;
        this.candidateService = candidateService;
        this.properties = properties;
    }

    @GetMapping("/jobs")
    public PagedResponse<JobPostingResponse> myPostings(@AuthenticationPrincipal PortalUser principal,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(required = false) Integer size) {
        int pageSize = Math.min(size == null ? properties.getSearch().getDefaultPageSize() : size,
                properties.getSearch().getMaxPageSize());
        Pageable pageable = PageRequest.of(page, pageSize);
        return jobService.postingsForRecruiter(principal, pageable);
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobPostingResponse createJob(@AuthenticationPrincipal PortalUser principal,
                                        @Valid @RequestBody CreateJobRequest request) {
        return jobService.createJob(principal, request);
    }

    @PostMapping("/jobs/{jobId}/publish")
    public JobPostingResponse publish(@AuthenticationPrincipal PortalUser principal,
                                      @PathVariable Long jobId) {
        return jobService.publish(jobId, principal);
    }

    @PostMapping("/jobs/{jobId}/close")
    public JobPostingResponse close(@AuthenticationPrincipal PortalUser principal,
                                    @PathVariable Long jobId) {
        return jobService.close(jobId, principal);
    }

    @GetMapping("/jobs/{jobId}/applications")
    public List<ApplicationResponse> applications(@AuthenticationPrincipal PortalUser principal,
                                                  @PathVariable Long jobId) {
        return applicationService.applicationsForPosting(jobId, principal);
    }

    @PostMapping("/applications/{applicationId}/status")
    public ApplicationResponse updateStatus(@AuthenticationPrincipal PortalUser principal,
                                            @PathVariable Long applicationId,
                                            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return applicationService.updateStatus(applicationId, principal, request);
    }

    @GetMapping("/candidates/{candidateId}")
    public CandidateResponse candidate(@PathVariable Long candidateId) {
        return candidateService.getCandidate(candidateId);
    }
}
