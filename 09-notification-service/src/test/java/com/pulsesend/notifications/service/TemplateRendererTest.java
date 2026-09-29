package com.pulsesend.notifications.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void substitutesPlaceholdersFromThePayload() {
        String rendered = renderer.render(
                "Hello {{name}}, order {{orderRef}} is on its way.",
                "{\"name\":\"Ravi\",\"orderRef\":\"ORD-5001\"}");

        assertThat(rendered).isEqualTo("Hello Ravi, order ORD-5001 is on its way.");
    }

    @Test
    void leavesUnknownPlaceholdersVisible() {
        String rendered = renderer.render("Hello {{name}}, your code is {{code}}.", "{\"name\":\"Asha\"}");

        assertThat(rendered).isEqualTo("Hello Asha, your code is {{code}}.");
    }

    @Test
    void toleratesAnEmptyOrBrokenPayload() {
        assertThat(renderer.render("Hello {{name}}", null)).isEqualTo("Hello {{name}}");
        assertThat(renderer.render("Hello {{name}}", "not json")).isEqualTo("Hello {{name}}");
    }
}
