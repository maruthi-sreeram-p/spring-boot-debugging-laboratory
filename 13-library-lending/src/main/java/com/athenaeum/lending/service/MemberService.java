package com.athenaeum.lending.service;

import com.athenaeum.lending.config.CirculationProperties;
import com.athenaeum.lending.dto.LoanResponse;
import com.athenaeum.lending.dto.MemberResponse;
import com.athenaeum.lending.entity.Member;
import com.athenaeum.lending.exception.ResourceNotFoundException;
import com.athenaeum.lending.mapper.CirculationMapper;
import com.athenaeum.lending.repository.LoanRepository;
import com.athenaeum.lending.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final FineService fineService;
    private final CirculationMapper circulationMapper;
    private final CirculationProperties properties;

    public MemberService(MemberRepository memberRepository,
                         LoanRepository loanRepository,
                         FineService fineService,
                         CirculationMapper circulationMapper,
                         CirculationProperties properties) {
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.fineService = fineService;
        this.circulationMapper = circulationMapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public MemberResponse byId(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
        return circulationMapper.toResponse(member,
                properties.getCirculation().loanLimitFor(member.getTier()),
                fineService.outstandingFor(memberId));
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> all() {
        return memberRepository.findAll().stream()
                .map(member -> circulationMapper.toResponse(member,
                        properties.getCirculation().loanLimitFor(member.getTier()),
                        fineService.outstandingFor(member.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> loansOf(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("Member", memberId);
        }
        return circulationMapper.toLoanResponses(
                loanRepository.findByMemberIdOrderByBorrowedAtDesc(memberId));
    }
}
