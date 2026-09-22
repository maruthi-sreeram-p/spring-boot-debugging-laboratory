package com.northgate.hr.mapper;

import com.northgate.hr.dto.DepartmentResponse;
import com.northgate.hr.entity.Department;
import org.springframework.stereotype.Component;

@Component
public class DepartmentMapper {

    public DepartmentResponse toResponse(Department department, long headcount) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getLocation(),
                headcount);
    }
}
