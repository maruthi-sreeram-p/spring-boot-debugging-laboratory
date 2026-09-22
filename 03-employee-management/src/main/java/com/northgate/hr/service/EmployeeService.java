package com.northgate.hr.service;

import com.northgate.hr.dto.CreateEmployeeRequest;
import com.northgate.hr.dto.EmployeeResponse;
import com.northgate.hr.dto.EmployeeSearchCriteria;
import com.northgate.hr.dto.PagedResponse;
import com.northgate.hr.dto.UpdateEmployeeRequest;
import com.northgate.hr.entity.Department;
import com.northgate.hr.entity.Employee;
import com.northgate.hr.entity.EmploymentStatus;
import com.northgate.hr.exception.DuplicateEmployeeException;
import com.northgate.hr.exception.ResourceNotFoundException;
import com.northgate.hr.mapper.EmployeeMapper;
import com.northgate.hr.repository.DepartmentRepository;
import com.northgate.hr.repository.EmployeeRepository;
import com.northgate.hr.security.DirectoryUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);
    private static final String HR_ADMIN = "ROLE_HR_ADMIN";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeMapper employeeMapper;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           EmployeeMapper employeeMapper) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.employeeMapper = employeeMapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<EmployeeResponse> search(EmployeeSearchCriteria criteria, Pageable pageable) {
        Page<Employee> page = employeeRepository.findAll(EmployeeSpecifications.matching(criteria), pageable);
        log.debug("Directory search returned {} of {} employees", page.getNumberOfElements(), page.getTotalElements());
        return PagedResponse.of(page, employeeMapper.toResponses(page.getContent()));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(Long employeeId, DirectoryUser principal) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        EmployeeResponse response = employeeMapper.toResponse(employee);
        if (!maySeeCompensation(principal, employee)) {
            response.setSalary(null);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> directReports(Long managerId) {
        if (!employeeRepository.existsById(managerId)) {
            throw new ResourceNotFoundException("Employee", managerId);
        }
        List<Employee> reports = employeeRepository.findByManagerIdOrderByLastNameAsc(managerId);
        List<EmployeeResponse> responses = employeeMapper.toResponses(reports);
        responses.forEach(response -> response.setSalary(null));
        return responses;
    }

    @PreAuthorize("hasRole('HR_ADMIN')")
    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmployeeException("An employee already exists with email " + request.getEmail());
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));

        Employee employee = new Employee();
        employee.setEmployeeCode(nextEmployeeCode());
        employee.setFirstName(request.getFirstName().trim());
        employee.setLastName(request.getLastName().trim());
        employee.setEmail(request.getEmail().trim());
        employee.setPhone(request.getPhone());
        employee.setJobTitle(request.getJobTitle().trim());
        employee.setDepartment(department);
        employee.setManager(resolveManager(request.getManagerId()));
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setHireDate(request.getHireDate());
        employee.setSalary(request.getSalary());

        Employee saved = employeeRepository.save(employee);
        log.info("Created employee {} ({})", saved.getEmployeeCode(), saved.getEmail());
        return employeeMapper.toResponse(saved);
    }

    @PreAuthorize("hasRole('HR_ADMIN')")
    @Transactional
    public EmployeeResponse update(Long employeeId, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", request.getDepartmentId()));

        employee.setFirstName(request.getFirstName().trim());
        employee.setLastName(request.getLastName().trim());
        employee.setEmail(request.getEmail().trim());
        employee.setPhone(request.getPhone());
        employee.setJobTitle(request.getJobTitle().trim());
        employee.setDepartment(department);
        employee.setManager(resolveManager(request.getManagerId()));
        employee.setSalary(request.getSalary());
        employee.setEmploymentStatus(EmploymentStatus.valueOf(request.getEmploymentStatus()));

        log.info("Updated employee {}", employee.getEmployeeCode());
        return employeeMapper.toResponse(employee);
    }

    @PreAuthorize("hasRole('HR_ADMIN')")
    @Transactional
    public void terminate(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        employee.setEmploymentStatus(EmploymentStatus.TERMINATED);
        log.info("Employee {} marked as terminated", employee.getEmployeeCode());
    }

    private boolean maySeeCompensation(DirectoryUser principal, Employee employee) {
        if (principal.hasRole(HR_ADMIN)) {
            return true;
        }
        return employee.getId().equals(principal.getEmployeeId());
    }

    private Employee resolveManager(Long managerId) {
        if (managerId == null) {
            return null;
        }
        return employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", managerId));
    }

    private String nextEmployeeCode() {
        long next = employeeRepository.count() + 1001;
        String candidate = String.format("NG-%04d", next);
        while (employeeRepository.existsByEmployeeCode(candidate)) {
            next++;
            candidate = String.format("NG-%04d", next);
        }
        return candidate;
    }
}
