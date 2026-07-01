package com.studyplatform.dto.tournament;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Result of running candidate code (free run or final grading) against test cases. */
@Data
@Builder
public class CodeRunResult {

    /** True if the program ran without a runtime/compile error. */
    private boolean success;

    /** Combined stdout of the (last) run shown to the candidate. */
    private String stdout;

    /** stderr / compile errors, if any. */
    private String stderr;

    /** Per-test-case outcome. */
    private List<CaseResult> cases;

    /** True when every test case passed. */
    private boolean allPassed;

    @Data
    @Builder
    public static class CaseResult {
        private boolean passed;
        private String input;
        private String expected;
        private String actual;
    }
}
