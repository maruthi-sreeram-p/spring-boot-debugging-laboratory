package com.hirestack.portal.mapper;

import com.hirestack.portal.dto.JobPostingResponse;
import com.hirestack.portal.entity.Company;
import com.hirestack.portal.entity.EmploymentType;
import com.hirestack.portal.entity.JobPosting;
import com.hirestack.portal.entity.JobStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PortalMapperTest {

    private final PortalMapper mapper = new PortalMapper();

    @Test
    void mapsJobPostingForTheBoard() {
        Company company = new Company();
        company.setId(1L);
        company.setName("Northwind Retail Technologies");
        company.setSlug("northwind-retail");
        company.setIndustry("Retail Technology");
        company.setCity("Bengaluru");
        company.setVerified(true);

        JobPosting job = new JobPosting();
        job.setId(1L);
        job.setReference("JOB-2025-0101");
        job.setCompany(company);
        job.setPostedById(10L);
        job.setTitle("Senior Backend Engineer");
        job.setDescription("Own and scale the order platform.");
        job.setLocation("Bengaluru");
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setRemote(false);
        job.setMinExperience(5);
        job.setMinSalary(new BigDecimal("2800000.00"));
        job.setMaxSalary(new BigDecimal("4200000.00"));
        job.setStatus(JobStatus.PUBLISHED);
        job.setCreatedAt(Instant.parse("2025-04-02T10:00:00Z"));
        job.setClosesOn(LocalDate.of(2025, 12, 31));

        JobPostingResponse response = mapper.toResponse(job);

        assertThat(response.getReference()).isEqualTo("JOB-2025-0101");
        assertThat(response.getCompanyName()).isEqualTo("Northwind Retail Technologies");
        assertThat(response.getEmploymentType()).isEqualTo("FULL_TIME");
        assertThat(response.getStatus()).isEqualTo("PUBLISHED");
        assertThat(response.getMinSalary()).isEqualByComparingTo("2800000.00");
        assertThat(response.isRemote()).isFalse();
    }
}
