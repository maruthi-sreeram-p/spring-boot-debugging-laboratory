package com.northgate.hr.service;

import com.northgate.hr.dto.EmployeeSearchCriteria;
import com.northgate.hr.entity.Employee;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the dynamic predicate behind the directory search box and its filter panel.
 * Every supplied filter narrows the result set; filters that were not supplied are skipped.
 */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    public static Specification<Employee> matching(EmployeeSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getDepartmentId() != null) {
                predicates.add(builder.equal(root.get("department").get("id"), criteria.getDepartmentId()));
            }

            if (criteria.getStatus() != null) {
                predicates.add(builder.equal(root.get("employmentStatus"), criteria.getStatus()));
            }

            if (StringUtils.hasText(criteria.getJobTitle())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("jobTitle")),
                        criteria.getJobTitle().toLowerCase(Locale.ROOT)));
            }

            if (StringUtils.hasText(criteria.getTerm())) {
                String pattern = "%" + criteria.getTerm().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("firstName")), pattern),
                        builder.like(builder.lower(root.get("lastName")), pattern),
                        builder.like(builder.lower(root.get("email")), pattern)));
            }

            if (predicates.isEmpty()) {
                return builder.conjunction();
            }
            return builder.or(predicates.toArray(new Predicate[0]));
        };
    }
}
