package com.aegis.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String body;

    /** INTERNAL or CONFIDENTIAL. Defaults to INTERNAL when omitted. */
    private String sensitivity;
}
