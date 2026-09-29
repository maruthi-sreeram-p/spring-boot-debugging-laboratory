package com.pulsesend.notifications.controller;

import com.pulsesend.notifications.dto.PreferenceResponse;
import com.pulsesend.notifications.dto.UpdatePreferenceRequest;
import com.pulsesend.notifications.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/{recipientRef}")
    public List<PreferenceResponse> get(@PathVariable String recipientRef) {
        return preferenceService.forRecipient(recipientRef);
    }

    @PutMapping("/{recipientRef}")
    public PreferenceResponse update(@PathVariable String recipientRef,
                                     @Valid @RequestBody UpdatePreferenceRequest request) {
        return preferenceService.update(recipientRef, request);
    }
}
