package com.hirestack.portal.service;

import com.hirestack.portal.dto.CreateJobRequest;
import com.hirestack.portal.dto.JobPostingResponse;
import com.hirestack.portal.dto.JobSearchCriteria;
import com.hirestack.portal.dto.PagedResponse;
import com.hirestack.portal.entity.Company;
import com.hirestack.portal.entity.EmploymentType;
import com.hirestack.portal.entity.JobPosting;
import com.hirestack.portal.entity.JobStatus;
import com.hirestack.portal.exception.HiringRuleException;
import com.hirestack.portal.exception.ResourceNotFoundException;
import com.hirestack.portal.mapper.PortalMapper;
import com.hirestack.portal.repository.CompanyRepository;
import com.hirestack.portal.repository.JobPostingRepository;
import com.hirestack.portal.security.PortalUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final PortalMapper portalMapper;

    public JobService(JobPostingRepository jobPostingRepository,
                      CompanyRepository companyRepository,
                      PortalMapper portalMapper) {
        this.jobPostingRepository = jobPostingRepository;
        this.companyRepository = companyRepository;
        this.portalMapper = portalMapper;
    }

    /**
     * Backs the public job board.
     */
    @Transactional(readOnly = true)
    public PagedResponse<JobPostingResponse> search(JobSearchCriteria criteria, Pageable pageable) {
        Page<JobPosting> page = jobPostingRepository.findAll(JobSpecifications.matching(criteria), pageable);
        log.debug("Job search matched {} posting(s)", page.getTotalElements());
        return PagedResponse.of(page, portalMapper.toJobResponses(page.getContent()));
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getJob(Long jobId) {
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting", jobId));
        return portalMapper.toResponse(job);
    }

    /**
     * The postings screen a recruiter sees when they sign in.
     */
    @Transactional(readOnly = true)
    public PagedResponse<JobPostingResponse> postingsForRecruiter(PortalUser principal, Pageable pageable) {
        Page<JobPosting> page = jobPostingRepository
                .findByPostedByIdOrderByCreatedAtDesc(principal.getUserId(), pageable);
        return PagedResponse.of(page, portalMapper.toJobResponses(page.getContent()));
    }

    @Transactional
    public JobPostingResponse createJob(PortalUser principal, CreateJobRequest request) {
        if (principal.getCompanyId() == null) {
            throw new HiringRuleException("Only a recruiter attached to a company can post a job");
        }
        Company company = companyRepository.findById(principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", principal.getCompanyId()));

        if (request.getMaxSalary().compareTo(request.getMinSalary()) < 0) {
            throw new HiringRuleException("The maximum salary cannot be below the minimum salary");
        }

        JobPosting job = new JobPosting();
        job.setReference(nextReference());
        job.setCompany(company);
        job.setPostedById(principal.getUserId());
        job.setTitle(request.getTitle().trim());
        job.setDescription(request.getDescription().trim());
        job.setLocation(request.getLocation().trim());
        job.setEmploymentType(EmploymentType.valueOf(request.getEmploymentType()));
        job.setRemote(request.isRemote());
        job.setMinExperience(request.getMinExperience());
        job.setMinSalary(request.getMinSalary());
        job.setMaxSalary(request.getMaxSalary());
        job.setStatus(JobStatus.DRAFT);
        job.setClosesOn(request.getClosesOn());

        JobPosting saved = jobPostingRepository.save(job);
        log.info("Recruiter {} created draft posting {} for {}",
                principal.getUserId(), saved.getReference(), company.getName());
        return portalMapper.toResponse(saved);
    }

    @Transactional
    public JobPostingResponse publish(Long jobId, PortalUser principal) {
        JobPosting job = loadOwnedPosting(jobId, principal);
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new HiringRuleException("A closed posting cannot be published again");
        }
        job.setStatus(JobStatus.PUBLISHED);
        log.info("Posting {} published", job.getReference());
        return portalMapper.toResponse(job);
    }

    @Transactional
    public JobPostingResponse close(Long jobId, PortalUser principal) {
        JobPosting job = loadOwnedPosting(jobId, principal);
        job.setStatus(JobStatus.CLOSED);
        log.info("Posting {} closed", job.getReference());
        return portalMapper.toResponse(job);
    }

    private JobPosting loadOwnedPosting(Long jobId, PortalUser principal) {
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting", jobId));
        if (!principal.isAdmin() && !job.getCompany().getId().equals(principal.getCompanyId())) {
            throw new ResourceNotFoundException("Job posting", jobId);
        }
        return job;
    }

    private String nextReference() {
        String candidate;
        do {
            candidate = "JOB-" + LocalDate.now(ZoneOffset.UTC).getYear()
                    + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(200, 9999));
        } while (jobPostingRepository.existsByReference(candidate));
        return candidate;
    }
}
