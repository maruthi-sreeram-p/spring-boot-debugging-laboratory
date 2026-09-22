package com.northgate.hr.mapper;

import com.northgate.hr.dto.EmployeeResponse;
import com.northgate.hr.entity.Employee;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmployeeMapper {

    public EmployeeResponse toResponse(Employee employee) {
        EmployeeResponse response = new EmployeeResponse();
        response.setId(employee.getId());
        response.setEmployeeCode(employee.getEmployeeCode());
        response.setFullName(employee.getFirstName() + " " + employee.getLastName());
        response.setEmail(employee.getEmail());
        response.setPhone(employee.getPhone());
        response.setJobTitle(employee.getJobTitle());
        response.setEmploymentStatus(employee.getEmploymentStatus().name());
        response.setHireDate(employee.getHireDate());
        response.setSalary(employee.getSalary());

        if (employee.getDepartment() != null) {
            response.setDepartmentName(employee.getDepartment().getName());
        }
        if (employee.getManager() != null) {
            response.setManagerName(employee.getManager().getFirstName() + " " + employee.getManager().getLastName());
        }
        return response;
    }

    public List<EmployeeResponse> toResponses(List<Employee> employees) {
        return employees.stream().map(this::toResponse).toList();
    }
}
