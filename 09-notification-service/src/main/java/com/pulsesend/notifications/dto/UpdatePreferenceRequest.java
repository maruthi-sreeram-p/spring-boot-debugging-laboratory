package com.pulsesend.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePreferenceRequest {

    @NotBlank
    @Pattern(regexp = "EMAIL|SMS|IN_APP")
    private String channel;

    @NotNull
    private Boolean enabled;
}
