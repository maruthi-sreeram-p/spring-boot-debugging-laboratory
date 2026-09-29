package com.pulsesend.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BroadcastTarget {

    @NotBlank
    @Size(max = 120)
    private String recipientRef;

    @NotBlank
    @Size(max = 160)
    private String destination;
}
