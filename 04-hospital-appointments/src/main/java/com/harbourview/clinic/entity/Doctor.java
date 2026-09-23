package com.harbourview.clinic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "doctors")
@Getter
@Setter
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 16, unique = true)
    private String code;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "specialty", nullable = false, length = 80)
    private String specialty;

    @Column(name = "email", nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "consultation_minutes", nullable = false)
    private Integer consultationMinutes = 30;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
