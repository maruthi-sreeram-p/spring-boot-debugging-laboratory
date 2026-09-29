package com.pulsesend.notifications.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BroadcastRequest {

    @NotBlank
    @Size(max = 80)
    private String templateCode;

    @NotEmpty
    @Valid
    private List<BroadcastTarget> targets;

    @Size(max = 2000)
    private String payload;
}
