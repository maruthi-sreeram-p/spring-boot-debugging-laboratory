package com.hirestack.portal.controller;

import com.hirestack.portal.config.PortalProperties;
import com.hirestack.portal.dto.ApplicationResponse;
import com.hirestack.portal.dto.ApplyRequest;
import com.hirestack.portal.dto.JobPostingResponse;
import com.hirestack.portal.dto.JobSearchCriteria;
import com.hirestack.portal.dto.PagedResponse;
import com.hirestack.portal.entity.EmploymentType;
import com.hirestack.portal.security.PortalUser;
import com.hirestack.portal.service.ApplicationService;
import com.hirestack.portal.service.JobService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final ApplicationService applicationService;
    private final PortalProperties properties;

    public JobController(JobService jobService,
                         ApplicationService applicationService,
                         PortalProperties properties) {
        this.jobService = jobService;
        this.applicationService = applicationService;
        this.properties = properties;
    }

    @GetMapping
    public PagedResponse<JobPostingResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) BigDecimal minSalary,
            @RequestParam(required = false) Integer maxExperience,
            @RequestParam(defaultValue = "false") boolean remote,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {

        JobSearchCriteria criteria = new JobSearchCriteria();
        criteria.setQ(q);
        criteria.setLocation(location);
        criteria.setEmploymentType(employmentType);
        criteria.setCompanyId(companyId);
        criteria.setMinSalary(minSalary);
        criteria.setMaxExperience(maxExperience);
        criteria.setRemote(remote);

        int pageSize = Math.min(size == null ? properties.getSearch().getDefaultPageSize() : size,
                properties.getSearch().getMaxPageSize());
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return jobService.search(criteria, pageable);
    }

    @GetMapping("/{jobId}")
    public JobPostingResponse get(@PathVariable Long jobId) {
        return jobService.getJob(jobId);
    }

    @PostMapping("/{jobId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(@AuthenticationPrincipal PortalUser principal,
                                     @PathVariable Long jobId,
                                     @Valid @RequestBody ApplyRequest request) {
        return applicationService.apply(jobId, principal, request);
    }
}
