package com.athenaeum.lending.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fines")
@Getter
@Setter
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "days_overdue", nullable = false)
    private int daysOverdue;

    @Column(name = "assessed_at", nullable = false)
    private LocalDateTime assessedAt = LocalDateTime.now();

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FineStatus status = FineStatus.OUTSTANDING;
}
