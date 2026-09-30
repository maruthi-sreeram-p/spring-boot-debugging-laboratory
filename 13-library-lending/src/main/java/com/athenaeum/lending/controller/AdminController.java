package com.athenaeum.lending.controller;

import com.athenaeum.lending.dto.AccrualSummary;
import com.athenaeum.lending.dto.FineResponse;
import com.athenaeum.lending.dto.LoanResponse;
import com.athenaeum.lending.dto.MemberResponse;
import com.athenaeum.lending.service.FineService;
import com.athenaeum.lending.service.LoanService;
import com.athenaeum.lending.service.MemberService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Desk supervision: the overdue list, the fines ledger and the accrual run.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final LoanService loanService;
    private final FineService fineService;
    private final MemberService memberService;

    public AdminController(LoanService loanService,
                           FineService fineService,
                           MemberService memberService) {
        this.loanService = loanService;
        this.fineService = fineService;
        this.memberService = memberService;
    }

    @GetMapping("/loans")
    public List<LoanResponse> activeLoans() {
        return loanService.active();
    }

    @GetMapping("/loans/overdue")
    public List<LoanResponse> overdueLoans() {
        return loanService.overdue();
    }

    @GetMapping("/members")
    public List<MemberResponse> members() {
        return memberService.all();
    }

    /**
     * Runs the accrual immediately. {@code asOf} lets the desk re-run a night that was missed.
     */
    @PostMapping("/fines/accrual")
    public AccrualSummary runAccrual(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
        return fineService.runAccrual(asOf == null ? LocalDateTime.now() : asOf);
    }

    @PostMapping("/fines/{fineId}/pay")
    public FineResponse pay(@PathVariable Long fineId) {
        return fineService.pay(fineId);
    }

    @PostMapping("/fines/{fineId}/waive")
    public FineResponse waive(@PathVariable Long fineId) {
        return fineService.waive(fineId);
    }
}
