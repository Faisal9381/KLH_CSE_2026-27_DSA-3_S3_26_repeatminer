package com.repeatminer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 sanity test: proves the JUnit 5 toolchain runs through Maven
 * ({@code mvnw test}) before any algorithm code exists. Replaced by the real
 * per-class test suites in Phase 14.
 */
class SmokeTest {

    @Test
    void mavenAndJUnitToolchainWorks() {
        assertTrue(5 > 0, "trivial check — verifies the test runner itself");
    }
}
