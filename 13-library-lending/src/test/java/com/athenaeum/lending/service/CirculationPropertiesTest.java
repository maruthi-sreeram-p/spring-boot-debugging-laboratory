package com.athenaeum.lending.service;

import com.athenaeum.lending.config.CirculationProperties;
import com.athenaeum.lending.entity.MemberTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CirculationPropertiesTest {

    private final CirculationProperties properties = new CirculationProperties();

    @Test
    void loanLengthDependsOnTheTier() {
        assertThat(properties.getCirculation().loanDaysFor(MemberTier.STANDARD)).isEqualTo(14);
        assertThat(properties.getCirculation().loanDaysFor(MemberTier.PREMIUM)).isEqualTo(21);
        assertThat(properties.getCirculation().loanDaysFor(MemberTier.STAFF)).isEqualTo(28);
    }

    @Test
    void borrowingLimitDependsOnTheTier() {
        assertThat(properties.getCirculation().loanLimitFor(MemberTier.STANDARD)).isEqualTo(4);
        assertThat(properties.getCirculation().loanLimitFor(MemberTier.PREMIUM)).isEqualTo(8);
        assertThat(properties.getCirculation().loanLimitFor(MemberTier.STAFF)).isEqualTo(12);
    }
}
