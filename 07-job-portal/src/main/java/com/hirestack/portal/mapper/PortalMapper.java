package com.hirestack.portal.mapper;

import com.hirestack.portal.dto.ApplicationResponse;
import com.hirestack.portal.dto.CandidateResponse;
import com.hirestack.portal.dto.CompanyResponse;
import com.hirestack.portal.dto.JobPostingResponse;
import com.hirestack.portal.entity.Candidate;
import com.hirestack.portal.entity.Company;
import com.hirestack.portal.entity.JobApplication;
import com.hirestack.portal.entity.JobPosting;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PortalMapper {

    public JobPostingResponse toResponse(JobPosting job) {
        return new JobPostingResponse(
                job.getId(),
                job.getReference(),
                job.getCompany().getId(),
                job.getCompany().getName(),
                job.getTitle(),
                job.getDescription(),
                job.getLocation(),
                job.getEmploymentType().name(),
                job.isRemote(),
                job.getMinExperience(),
                job.getMinSalary(),
                job.getMaxSalary(),
                job.getStatus().name(),
                job.getCreatedAt(),
                job.getClosesOn());
    }

    public List<JobPostingResponse> toJobResponses(List<JobPosting> jobs) {
        return jobs.stream().map(this::toResponse).toList();
    }

    public ApplicationResponse toResponse(JobApplication application) {
        JobPosting job = application.getJobPosting();
        Candidate candidate = application.getCandidate();
        return new ApplicationResponse(
                application.getId(),
                job.getId(),
                job.getReference(),
                job.getTitle(),
                job.getCompany().getName(),
                candidate.getId(),
                candidate.getFullName(),
                candidate.getEmail(),
                candidate.getHeadline(),
                application.getStatus().name(),
                application.getCoverLetter(),
                application.getAppliedAt(),
                application.getUpdatedAt());
    }

    public List<ApplicationResponse> toApplicationResponses(List<JobApplication> applications) {
        return applications.stream().map(this::toResponse).toList();
    }

    public CandidateResponse toResponse(Candidate candidate) {
        return new CandidateResponse(
                candidate.getId(),
                candidate.getFullName(),
                candidate.getEmail(),
                candidate.getHeadline(),
                candidate.getLocation(),
                candidate.getYearsExperience(),
                candidate.getSkills(),
                candidate.getExpectedSalary());
    }

    public CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getSlug(),
                company.getIndustry(),
                company.getCity(),
                company.isVerified());
    }

    public List<CompanyResponse> toCompanyResponses(List<Company> companies) {
        return companies.stream().map(this::toResponse).toList();
    }
}
