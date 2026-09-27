package com.hirestack.portal.service;

import com.hirestack.portal.dto.JobSearchCriteria;
import com.hirestack.portal.entity.JobPosting;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the predicate behind the job search. Every filter the caller supplies narrows the
 * result set; filters that were left out are skipped.
 */
public final class JobSpecifications {

    private JobSpecifications() {
    }

    public static Specification<JobPosting> matching(JobSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(criteria.getQ())) {
                String pattern = "%" + criteria.getQ().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }

            if (StringUtils.hasText(criteria.getLocation())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("location")),
                        criteria.getLocation().toLowerCase(Locale.ROOT)));
            }

            if (criteria.getEmploymentType() != null) {
                predicates.add(builder.equal(root.get("employmentType"), criteria.getEmploymentType()));
            }

            if (criteria.getCompanyId() != null) {
                predicates.add(builder.equal(root.get("company").get("id"), criteria.getCompanyId()));
            }

            if (criteria.getMinSalary() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("maxSalary"), criteria.getMinSalary()));
            }

            if (criteria.getMaxExperience() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("minExperience"), criteria.getMaxExperience()));
            }

            predicates.add(builder.equal(root.get("remote"), criteria.isRemote()));

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
