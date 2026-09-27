package com.hirestack.portal.repository;

import com.hirestack.portal.entity.ApplicationStatus;
import com.hirestack.portal.entity.Candidate;
import com.hirestack.portal.entity.Company;
import com.hirestack.portal.entity.EmploymentType;
import com.hirestack.portal.entity.JobApplication;
import com.hirestack.portal.entity.JobPosting;
import com.hirestack.portal.entity.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class JobApplicationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Test
    void listsApplicationsForOnePostingAndCountsOpenOnesPerCandidate() {
        Company company = new Company();
        company.setName("Test Co");
        company.setSlug("test-co");
        company.setIndustry("Testing");
        company.setCity("Bengaluru");
        entityManager.persist(company);

        JobPosting first = persistJob(company, "JOB-T-0001");
        JobPosting second = persistJob(company, "JOB-T-0002");
        Candidate candidate = persistCandidate("first.candidate@example.com");
        Candidate other = persistCandidate("second.candidate@example.com");

        persistApplication(first, candidate, ApplicationStatus.SUBMITTED);
        persistApplication(first, other, ApplicationStatus.SHORTLISTED);
        persistApplication(second, candidate, ApplicationStatus.REJECTED);
        entityManager.flush();
        entityManager.clear();

        List<JobApplication> forFirst = jobApplicationRepository
                .findByJobPostingIdOrderByAppliedAtAsc(first.getId());
        assertThat(forFirst).hasSize(2);

        long open = jobApplicationRepository.countByCandidateIdAndStatusIn(
                candidate.getId(), List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.SHORTLISTED));
        assertThat(open).isEqualTo(1);
    }

    private JobPosting persistJob(Company company, String reference) {
        JobPosting job = new JobPosting();
        job.setReference(reference);
        job.setCompany(company);
        job.setPostedById(1L);
        job.setTitle("Test role");
        job.setDescription("Test description");
        job.setLocation("Bengaluru");
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setMinExperience(1);
        job.setMinSalary(new BigDecimal("1000000.00"));
        job.setMaxSalary(new BigDecimal("2000000.00"));
        job.setStatus(JobStatus.PUBLISHED);
        return entityManager.persist(job);
    }

    private Candidate persistCandidate(String email) {
        Candidate candidate = new Candidate();
        candidate.setFullName("Test Candidate");
        candidate.setEmail(email);
        candidate.setHeadline("Engineer");
        candidate.setLocation("Bengaluru");
        candidate.setYearsExperience(3);
        candidate.setSkills("Java");
        return entityManager.persist(candidate);
    }

    private void persistApplication(JobPosting job, Candidate candidate, ApplicationStatus status) {
        JobApplication application = new JobApplication();
        application.setJobPosting(job);
        application.setCandidate(candidate);
        application.setStatus(status);
        entityManager.persist(application);
    }
}
