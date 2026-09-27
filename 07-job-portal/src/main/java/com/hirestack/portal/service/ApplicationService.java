package com.hirestack.portal.service;

import com.hirestack.portal.config.PortalProperties;
import com.hirestack.portal.dto.ApplicationResponse;
import com.hirestack.portal.dto.ApplyRequest;
import com.hirestack.portal.dto.PagedResponse;
import com.hirestack.portal.dto.UpdateApplicationStatusRequest;
import com.hirestack.portal.entity.ApplicationStatus;
import com.hirestack.portal.entity.Candidate;
import com.hirestack.portal.entity.JobApplication;
import com.hirestack.portal.entity.JobPosting;
import com.hirestack.portal.entity.JobStatus;
import com.hirestack.portal.exception.DuplicateApplicationException;
import com.hirestack.portal.exception.HiringRuleException;
import com.hirestack.portal.exception.ResourceNotFoundException;
import com.hirestack.portal.mapper.PortalMapper;
import com.hirestack.portal.repository.CandidateRepository;
import com.hirestack.portal.repository.JobApplicationRepository;
import com.hirestack.portal.repository.JobPostingRepository;
import com.hirestack.portal.security.PortalUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private static final List<ApplicationStatus> OPEN_STATUSES = List.of(
            ApplicationStatus.SUBMITTED, ApplicationStatus.SHORTLISTED,
            ApplicationStatus.INTERVIEW, ApplicationStatus.OFFERED);

    private final JobApplicationRepository jobApplicationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CandidateRepository candidateRepository;
    private final PortalMapper portalMapper;
    private final PortalProperties properties;

    public ApplicationService(JobApplicationRepository jobApplicationRepository,
                              JobPostingRepository jobPostingRepository,
                              CandidateRepository candidateRepository,
                              PortalMapper portalMapper,
                              PortalProperties properties) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.candidateRepository = candidateRepository;
        this.portalMapper = portalMapper;
        this.properties = properties;
    }

    @Transactional
    public ApplicationResponse apply(Long jobPostingId, PortalUser principal, ApplyRequest request) {
        if (principal.getCandidateId() == null) {
            throw new HiringRuleException("Only a candidate account can apply to a posting");
        }

        JobPosting job = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting", jobPostingId));
        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw new HiringRuleException("This posting is not open for applications");
        }

        Candidate candidate = candidateRepository.findById(principal.getCandidateId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", principal.getCandidateId()));

        if (jobApplicationRepository.existsByJobPostingIdAndCandidateIdAndStatus(
                job.getId(), candidate.getId(), ApplicationStatus.SUBMITTED)) {
            throw new DuplicateApplicationException(job.getReference());
        }

        long openApplications = jobApplicationRepository
                .countByCandidateIdAndStatusIn(candidate.getId(), OPEN_STATUSES);
        if (openApplications >= properties.getApplications().getMaxOpenPerCandidate()) {
            throw new HiringRuleException("You already have "
                    + properties.getApplications().getMaxOpenPerCandidate() + " open applications");
        }

        JobApplication application = new JobApplication();
        application.setJobPosting(job);
        application.setCandidate(candidate);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setCoverLetter(request.getCoverLetter());

        JobApplication saved = jobApplicationRepository.save(application);
        log.info("Candidate {} applied to {}", candidate.getEmail(), job.getReference());
        return portalMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ApplicationResponse> myApplications(PortalUser principal, Pageable pageable) {
        Page<JobApplication> page = jobApplicationRepository
                .findByCandidateIdOrderByAppliedAtDesc(principal.getCandidateId(), pageable);
        return PagedResponse.of(page, portalMapper.toApplicationResponses(page.getContent()));
    }

    /**
     * The applicant pipeline a recruiter sees for one of their postings.
     */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> applicationsForPosting(Long jobPostingId, PortalUser principal) {
        JobPosting job = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting", jobPostingId));

        List<JobApplication> applications =
                jobApplicationRepository.findByJobPostingIdOrderByAppliedAtAsc(job.getId());
        return portalMapper.toApplicationResponses(applications);
    }

    @Transactional
    public ApplicationResponse updateStatus(Long applicationId, PortalUser principal,
                                            UpdateApplicationStatusRequest request) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));

        if (!principal.isAdmin()
                && !application.getJobPosting().getCompany().getId().equals(principal.getCompanyId())) {
            throw new ResourceNotFoundException("Application", applicationId);
        }

        application.setStatus(ApplicationStatus.valueOf(request.getStatus()));
        log.info("Application {} moved to {}", applicationId, request.getStatus());
        return portalMapper.toResponse(application);
    }

    @Transactional
    public ApplicationResponse withdraw(Long applicationId, PortalUser principal) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
        if (!application.getCandidate().getId().equals(principal.getCandidateId())) {
            throw new ResourceNotFoundException("Application", applicationId);
        }
        application.setStatus(ApplicationStatus.WITHDRAWN);
        return portalMapper.toResponse(application);
    }
}
