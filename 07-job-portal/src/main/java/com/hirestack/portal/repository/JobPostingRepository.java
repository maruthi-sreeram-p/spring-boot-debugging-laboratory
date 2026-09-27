package com.hirestack.portal.repository;

import com.hirestack.portal.entity.JobPosting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long>,
        JpaSpecificationExecutor<JobPosting> {

    Page<JobPosting> findByPostedByIdOrderByCreatedAtDesc(Long postedById, Pageable pageable);

    Page<JobPosting> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    boolean existsByReference(String reference);
}
