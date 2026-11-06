package dev.jagadeesh.shortlink.service;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeGeneratorTest {

    @Test
    void multiplierIsCoprimeWithTheCodeSpace_soTheMappingIsABijection() {
        assertThat(BigInteger.valueOf(CodeGenerator.MULTIPLIER).gcd(BigInteger.valueOf(CodeGenerator.SPACE)))
                .isEqualTo(BigInteger.ONE);
        assertThat(BigInteger.valueOf(62).pow(7).longValue()).isEqualTo(CodeGenerator.SPACE);
    }

    @Test
    void consecutiveNumbersGiveDistinctFixedLengthCodes() {
        Set<String> seen = new HashSet<>();
        for (long n = 1; n <= 200_000; n++) {
            String code = CodeGenerator.scramble(n);
            assertThat(code).hasSize(7).matches("[0-9A-Za-z]{7}");
            assertThat(seen.add(code)).as("duplicate code for %d", n).isTrue();
        }
    }

    @Test
    void neighbouringNumbersDoNotLookSequential() {
        String a = CodeGenerator.scramble(1000);
        String b = CodeGenerator.scramble(1001);
        int samePrefix = 0;
        while (samePrefix < 7 && a.charAt(samePrefix) == b.charAt(samePrefix)) {
            samePrefix++;
        }
        assertThat(samePrefix).isLessThan(4);
    }

    @Test
    void base62RoundTrips() {
        for (long v : new long[]{0, 1, 61, 62, 3_843, 916_132_831L, CodeGenerator.SPACE - 1}) {
            assertThat(Base62.decode(Base62.encode(v, 7))).isEqualTo(v);
        }
        assertThatThrownBy(() -> Base62.encode(CodeGenerator.SPACE, 7)).isInstanceOf(IllegalArgumentException.class);
    }
}
