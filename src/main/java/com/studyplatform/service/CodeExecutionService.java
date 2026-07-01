package com.studyplatform.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs candidate code in a sandbox via the public Wandbox API (wandbox.org).
 * Used by CODE_CHALLENGE tournament questions — both the "Execute" button and the
 * final grading go through here. Wandbox is synchronous and keyless.
 *
 * <p>Note: Wandbox's Java compiler expects the public class to be named
 * {@code Main}; the AI prompt is instructed accordingly.</p>
 */
@Service
@Slf4j
public class CodeExecutionService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    /** language → Wandbox compiler name. */
    private static final Map<String, String> COMPILERS = Map.of(
            "python", "cpython-3.10.15",
            "java", "openjdk-jdk-21+35",
            "javascript", "nodejs-20.17.0",
            "cpp", "gcc-13.2.0",
            "c", "gcc-13.2.0-c"
    );
    private static final String DEFAULT_COMPILER = "cpython-3.10.15";

    private final RestTemplate restTemplate;
    private final String wandboxUrl;
    private final ObjectMapper objectMapper;

    public CodeExecutionService(@Value("${app.wandbox.url}") String wandboxUrl,
                                ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
        this.wandboxUrl = wandboxUrl;
        this.objectMapper = objectMapper;
    }

    @Data
    public static class ExecResult {
        private final String stdout;
        private final String stderr;
        private final int exitCode;
        /** True if the program compiled and ran without error. */
        public boolean isSuccess() { return exitCode == 0 && (stderr == null || stderr.isBlank()); }
    }

    /** Execute {@code code} in {@code language}, feeding {@code stdin}; never throws — errors come back as stderr. */
    public ExecResult execute(String language, String code, String stdin) {
        String compiler = COMPILERS.getOrDefault(normalizeLanguage(language), DEFAULT_COMPILER);

        Map<String, Object> body = new HashMap<>();
        body.put("code", code == null ? "" : code);
        body.put("compiler", compiler);
        body.put("stdin", stdin == null ? "" : stdin);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            // Wandbox replies with content-type application/octet-stream, so we read the
            // raw body as String and parse it ourselves rather than relying on a
            // content-type-matched message converter.
            ResponseEntity<String> resp = restTemplate.exchange(
                    wandboxUrl + "/compile.json", HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);

            WandboxResponse wr = (resp.getBody() == null || resp.getBody().isBlank())
                    ? null
                    : objectMapper.readValue(resp.getBody(), WandboxResponse.class);
            if (wr == null) {
                return new ExecResult("", "Aucune réponse du moteur d'exécution.", 1);
            }
            // Surface compile errors (C/C++/Java) before runtime errors.
            String stderr = "";
            if (wr.getCompilerError() != null && !wr.getCompilerError().isBlank()) {
                stderr = wr.getCompilerError();
            } else if (wr.getProgramError() != null && !wr.getProgramError().isBlank()) {
                stderr = wr.getProgramError();
            }
            int exitCode = parseStatus(wr.getStatus());
            return new ExecResult(
                    wr.getProgramOutput() == null ? "" : wr.getProgramOutput(),
                    stderr, exitCode);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Wandbox execution failed ({}): {}", compiler, e.getMessage());
            return new ExecResult("", "Moteur d'exécution indisponible. Réessayez.", 1);
        }
    }

    private int parseStatus(String status) {
        if (status == null || status.isBlank()) return 1;
        try { return Integer.parseInt(status.trim()); }
        catch (NumberFormatException e) { return 1; }
    }

    private String normalizeLanguage(String language) {
        if (language == null) return "python";
        String l = language.trim().toLowerCase();
        return switch (l) {
            case "js", "node", "nodejs" -> "javascript";
            case "c++", "cplusplus" -> "cpp";
            case "py", "python3" -> "python";
            default -> l;
        };
    }

    // ── Wandbox API DTOs ──────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class WandboxResponse {
        private String status;
        @JsonProperty("program_output")
        private String programOutput;
        @JsonProperty("program_error")
        private String programError;
        @JsonProperty("compiler_error")
        private String compilerError;
    }
}
