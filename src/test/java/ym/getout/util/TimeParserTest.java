package ym.getout.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeParserTest {

    @Test
    void shouldParseCompositeDuration() {
        long expected = 86_400_000L + 7_200_000L + 1_800_000L;
        assertEquals(expected, TimeParser.parse("1d2h30m"));
    }

    @Test
    void shouldTreatTrailingNumberAsSeconds() {
        assertEquals(60_000L, TimeParser.parse("60"));
    }

    @Test
    void shouldRejectInvalidDuration() {
        assertEquals(-1L, TimeParser.parse("abc"));
        assertEquals(-1L, TimeParser.parse("1x"));
        assertEquals(-1L, TimeParser.parse(""));
    }
}
