package com.hirestack.portal.repository;

import com.hirestack.portal.entity.ApplicationStatus;
import com.hirestack.portal.entity.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Page<JobApplication> findByCandidateIdOrderByAppliedAtDesc(Long candidateId, Pageable pageable);

    List<JobApplication> findByJobPostingIdOrderByAppliedAtAsc(Long jobPostingId);

    boolean existsByJobPostingIdAndCandidateIdAndStatus(Long jobPostingId, Long candidateId, ApplicationStatus status);

    long countByCandidateIdAndStatusIn(Long candidateId, List<ApplicationStatus> statuses);
}
