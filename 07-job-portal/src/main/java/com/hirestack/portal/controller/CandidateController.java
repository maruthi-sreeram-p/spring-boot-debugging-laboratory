package com.hirestack.portal.controller;

import com.hirestack.portal.dto.ApplicationResponse;
import com.hirestack.portal.dto.CandidateResponse;
import com.hirestack.portal.dto.CompanyResponse;
import com.hirestack.portal.dto.PagedResponse;
import com.hirestack.portal.dto.RegisterCandidateRequest;
import com.hirestack.portal.mapper.PortalMapper;
import com.hirestack.portal.repository.CompanyRepository;
import com.hirestack.portal.security.PortalUser;
import com.hirestack.portal.service.ApplicationService;
import com.hirestack.portal.service.CandidateService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CandidateController {

    private final CandidateService candidateService;
    private final ApplicationService applicationService;
    private final CompanyRepository companyRepository;
    private final PortalMapper portalMapper;

    public CandidateController(CandidateService candidateService,
                               ApplicationService applicationService,
                               CompanyRepository companyRepository,
                               PortalMapper portalMapper) {
        this.candidateService = candidateService;
        this.applicationService = applicationService;
        this.companyRepository = companyRepository;
        this.portalMapper = portalMapper;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateResponse register(@Valid @RequestBody RegisterCandidateRequest request) {
        return candidateService.register(request);
    }

    @GetMapping("/auth/me")
    public CandidateResponse me(@AuthenticationPrincipal PortalUser principal) {
        return candidateService.getCandidate(principal.getCandidateId());
    }

    @GetMapping("/applications")
    public PagedResponse<ApplicationResponse> myApplications(@AuthenticationPrincipal PortalUser principal,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return applicationService.myApplications(principal, pageable);
    }

    @PostMapping("/applications/{applicationId}/withdraw")
    public ApplicationResponse withdraw(@AuthenticationPrincipal PortalUser principal,
                                        @PathVariable Long applicationId) {
        return applicationService.withdraw(applicationId, principal);
    }

    @GetMapping("/companies")
    @Transactional(readOnly = true)
    public List<CompanyResponse> companies() {
        return portalMapper.toCompanyResponses(companyRepository.findAllByOrderByNameAsc());
    }
}
