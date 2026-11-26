package com.memcyco.scheduler.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronCanonicalizerTest {

    @ParameterizedTest(name = "{0}  ==  {1}")
    @CsvSource(delimiter = '|', value = {
            "0 0 9 ? * MON          | 0 0 9 ? * 2",
            "0 0 9 ? * MON,WED      | 0 0 9 ? * WED,MON",
            "0 0 9 ? * MON-WED      | 0 0 9 ? * 2,3,4",
            "0 */15 * * * ?         | 0 0,15,30,45 * * * ?",
            "0 0/15 * * * ?         | 0 0,15,30,45 * * * ?",
            "0 0 9 * * ?            | 0 0 9 ? * *",
            "0 0 9 ? * SUN-SAT      | 0 0 9 * * ?",
            "0 0 9 1 JAN ?          | 0 0 9 1 1 ?",
            "0 0 9 ? * MON *        | 0 0 9 ? * MON",
            "0  0  9 ? *  mon       | 0 0 9 ? * MON",
            "0 0 22-2 * * ?         | 0 0 0,1,2,22,23 * * ?",
            "0 0 0-23 * * ?         | 0 0 * * * ?"
    })
    void equivalentExpressionsHaveTheSameCanonicalForm(String a, String b) {
        assertThat(CronCanonicalizer.canonicalize(a)).isEqualTo(CronCanonicalizer.canonicalize(b));
    }

    @ParameterizedTest(name = "{0}  !=  {1}")
    @CsvSource(delimiter = '|', value = {
            "0 0 9 ? * MON          | 0 0 9 ? * TUE",
            "0 */15 * * * ?         | 0 */5 * * * ?",
            "0 0 9 ? * MON          | 0 0 10 ? * MON",
            "0 0 9 L * ?            | 0 0 9 1 * ?"
    })
    void differentExpressionsStayDifferent(String a, String b) {
        assertThat(CronCanonicalizer.canonicalize(a)).isNotEqualTo(CronCanonicalizer.canonicalize(b));
    }

    @Test
    void producesSevenNormalizedFields() {
        assertThat(CronCanonicalizer.canonicalize("0 30 9 ? * MON,WED")).isEqualTo("0 30 9 * * 2,4 *");
    }

    @Test
    void keepsSpecialQuartzTokens() {
        assertThat(CronCanonicalizer.canonicalize("0 0 9 ? * FRIL")).isEqualTo("0 0 9 * * 6L *");
        assertThat(CronCanonicalizer.canonicalize("0 0 9 ? * MON#2")).isEqualTo("0 0 9 * * 2#2 *");
    }

    @Test
    void rejectsWrongNumberOfFields() {
        assertThatThrownBy(() -> CronCanonicalizer.canonicalize("0 9 * * 1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
