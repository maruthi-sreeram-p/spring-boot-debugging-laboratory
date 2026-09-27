package com.hirestack.portal.service;

import com.hirestack.portal.dto.CandidateResponse;
import com.hirestack.portal.dto.RegisterCandidateRequest;
import com.hirestack.portal.entity.AppUser;
import com.hirestack.portal.entity.Candidate;
import com.hirestack.portal.exception.DuplicateEmailException;
import com.hirestack.portal.exception.ResourceNotFoundException;
import com.hirestack.portal.mapper.PortalMapper;
import com.hirestack.portal.repository.AppUserRepository;
import com.hirestack.portal.repository.CandidateRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PortalMapper portalMapper;

    public CandidateService(CandidateRepository candidateRepository,
                            AppUserRepository appUserRepository,
                            PasswordEncoder passwordEncoder,
                            PortalMapper portalMapper) {
        this.candidateRepository = candidateRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.portalMapper = portalMapper;
    }

    @Transactional
    public CandidateResponse register(RegisterCandidateRequest request) {
        String email = request.getEmail().trim();
        if (appUserRepository.existsByEmail(email) || candidateRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        Candidate candidate = new Candidate();
        candidate.setFullName(request.getFullName().trim());
        candidate.setEmail(email);
        candidate.setPhone(request.getPhone());
        candidate.setHeadline(request.getHeadline().trim());
        candidate.setLocation(request.getLocation().trim());
        candidate.setYearsExperience(request.getYearsExperience() == null ? 0 : request.getYearsExperience());
        candidate.setSkills(request.getSkills() == null ? "" : request.getSkills());
        Candidate savedCandidate = candidateRepository.save(candidate);

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoleName("ROLE_CANDIDATE");
        user.setCandidateId(savedCandidate.getId());
        appUserRepository.save(user);

        return portalMapper.toResponse(savedCandidate);
    }

    @Transactional(readOnly = true)
    public CandidateResponse getCandidate(Long candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate", candidateId));
        return portalMapper.toResponse(candidate);
    }
}
