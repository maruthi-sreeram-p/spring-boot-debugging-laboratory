package com.athenaeum.lending.controller;

import com.athenaeum.lending.dto.BorrowRequest;
import com.athenaeum.lending.dto.FineResponse;
import com.athenaeum.lending.dto.LoanResponse;
import com.athenaeum.lending.dto.MemberResponse;
import com.athenaeum.lending.dto.ReservationRequest;
import com.athenaeum.lending.dto.ReservationResponse;
import com.athenaeum.lending.dto.ReturnRequest;
import com.athenaeum.lending.service.FineService;
import com.athenaeum.lending.service.LoanService;
import com.athenaeum.lending.service.MemberService;
import com.athenaeum.lending.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/circulation")
public class CirculationController {

    private final LoanService loanService;
    private final ReservationService reservationService;
    private final MemberService memberService;
    private final FineService fineService;

    public CirculationController(LoanService loanService,
                                 ReservationService reservationService,
                                 MemberService memberService,
                                 FineService fineService) {
        this.loanService = loanService;
        this.reservationService = reservationService;
        this.memberService = memberService;
        this.fineService = fineService;
    }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse borrow(@Valid @RequestBody BorrowRequest request) {
        return loanService.borrow(request.getBookId(), request.getMemberId());
    }

    @PostMapping("/returns")
    public LoanResponse returnCopy(@Valid @RequestBody ReturnRequest request) {
        return loanService.returnCopy(request.getBarcode());
    }

    @PostMapping("/loans/{loanId}/renew")
    public LoanResponse renew(@PathVariable Long loanId) {
        return loanService.renew(loanId);
    }

    @GetMapping("/loans/{loanId}")
    public LoanResponse loan(@PathVariable Long loanId) {
        return loanService.byId(loanId);
    }

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse placeHold(@Valid @RequestBody ReservationRequest request) {
        return reservationService.place(request.getBookId(), request.getMemberId());
    }

    @DeleteMapping("/holds/{reservationId}")
    public ReservationResponse cancelHold(@PathVariable Long reservationId) {
        return reservationService.cancel(reservationId);
    }

    @GetMapping("/members/{memberId}")
    public MemberResponse member(@PathVariable Long memberId) {
        return memberService.byId(memberId);
    }

    @GetMapping("/members/{memberId}/loans")
    public List<LoanResponse> memberLoans(@PathVariable Long memberId) {
        return memberService.loansOf(memberId);
    }

    @GetMapping("/members/{memberId}/holds")
    public List<ReservationResponse> memberHolds(@PathVariable Long memberId) {
        return reservationService.forMember(memberId);
    }

    @GetMapping("/members/{memberId}/fines")
    public List<FineResponse> memberFines(@PathVariable Long memberId) {
        return fineService.forMember(memberId);
    }
}
