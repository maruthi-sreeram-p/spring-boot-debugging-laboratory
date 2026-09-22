package com.northgate.hr.service;

import com.northgate.hr.dto.DepartmentResponse;
import com.northgate.hr.dto.EmployeeResponse;
import com.northgate.hr.dto.EmployeeSearchCriteria;
import com.northgate.hr.dto.PagedResponse;
import com.northgate.hr.entity.Department;
import com.northgate.hr.exception.ResourceNotFoundException;
import com.northgate.hr.mapper.DepartmentMapper;
import com.northgate.hr.repository.DepartmentRepository;
import com.northgate.hr.repository.EmployeeRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentMapper departmentMapper;
    private final EmployeeService employeeService;

    public DepartmentService(DepartmentRepository departmentRepository,
                             EmployeeRepository employeeRepository,
                             DepartmentMapper departmentMapper,
                             EmployeeService employeeService) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.departmentMapper = departmentMapper;
        this.employeeService = employeeService;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments() {
        List<DepartmentResponse> responses = new ArrayList<>();
        for (Department department : departmentRepository.findAllByOrderByNameAsc()) {
            long headcount = employeeRepository.countByDepartmentId(department.getId());
            responses.add(departmentMapper.toResponse(department, headcount));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public PagedResponse<EmployeeResponse> employeesOf(Long departmentId, Pageable pageable) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new ResourceNotFoundException("Department", departmentId);
        }
        EmployeeSearchCriteria criteria = new EmployeeSearchCriteria();
        criteria.setDepartmentId(departmentId);
        return employeeService.search(criteria, pageable);
    }
}
