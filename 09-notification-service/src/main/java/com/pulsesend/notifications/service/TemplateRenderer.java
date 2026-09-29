package com.pulsesend.notifications.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Replaces {{placeholders}} in a template with values from the notification payload.
 * A placeholder with no matching value is left as it is, so a missing field is visible in
 * the delivered message rather than silently blank.
 */
@Component
public class TemplateRenderer {

    private static final Logger log = LoggerFactory.getLogger(TemplateRenderer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String render(String template, String payloadJson) {
        Map<String, Object> values = readPayload(payloadJson);
        String rendered = template;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return rendered;
    }

    private Map<String, Object> readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            log.warn("Could not read notification payload, rendering without values: {}", ex.getMessage());
            return Map.of();
        }
    }
}
