
package com.finiq;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AuditController {

    private final AuditService auditService;
    private final AuditRunRepository repository;
    private final ObjectMapper objectMapper;
    private final PdfReportService pdfReportService;

    public AuditController(
            AuditService auditService,
            AuditRunRepository repository,
            ObjectMapper objectMapper,
            PdfReportService pdfReportService) {

        this.auditService = auditService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.pdfReportService = pdfReportService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "application", "FinIQ",
            "version", "2.0"
        );
    }

    @PostMapping(value = "/audit",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> audit(
            @RequestParam("file") MultipartFile file) {

        try {

            Map<String, Object> report = auditService.audit(file);

            Map<?, ?> summary = (Map<?, ?>) report.get("summary");

            AuditRun run = new AuditRun();

            run.setFileName(Objects.requireNonNullElse(
                file.getOriginalFilename(), "upload.csv"
            ));

            run.setTotalTransactions(
                number(summary.get("total_transactions"))
            );

            run.setTotalFindings(
                number(summary.get("total_findings"))
            );

            run.setHighRiskFindings(
                number(summary.get("high_risk_findings"))
            );

            run.setMediumRiskFindings(
                number(summary.get("medium_risk_findings"))
            );

            run.setLowRiskFindings(
                number(summary.get("low_risk_findings"))
            );

            Object statusValue = summary.get("status");

            run.setStatus(statusValue == null
                ? "COMPLETE"
                : statusValue.toString());

            run.setDetectionMethod(String.valueOf(
                report.getOrDefault(
                    "detection_method", "Audit rules"
                )
            ));

            run.setCreatedAt(LocalDateTime.now());
            run.setReportJson("{}");

            // Save to generate the database ID.
            run = repository.save(run);

            report.put("auditRunId", run.getId());
            report.put("source_file", run.getFileName());
            report.put("createdAt", run.getCreatedAt().toString());

            // Save the complete report with all findings and vendor metrics.
            run.setReportJson(
                objectMapper.writeValueAsString(report)
            );

            repository.save(run);

            return ResponseEntity.ok(report);

        } catch (IllegalArgumentException ex) {

            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error",
                    Objects.requireNonNullElse(
                        ex.getMessage(), "Invalid uploaded file."
                    )
                ));

        } catch (IOException ex) {

            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "Could not process the uploaded file."
                ));

        } catch (Exception ex) {

            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "error", "Audit failed. Check server logs."
                ));
        }
    }

    @GetMapping("/audit/history")
    public List<Map<String, Object>> history() {

        return repository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(run -> {

                Map<String, Object> item = new LinkedHashMap<>();

                item.put("id", run.getId());
                item.put("fileName", run.getFileName());
                item.put("totalTransactions", run.getTotalTransactions());
                item.put("totalFindings", run.getTotalFindings());
                item.put("highRiskFindings", run.getHighRiskFindings());
                item.put("mediumRiskFindings", run.getMediumRiskFindings());
                item.put("lowRiskFindings", run.getLowRiskFindings());
                item.put("status", run.getStatus());
                item.put("createdAt", run.getCreatedAt());

                return item;
            })
            .toList();
    }

    @GetMapping("/audit/history/{id}")
    public ResponseEntity<?> reportHistory(
            @PathVariable Long id) throws IOException {

        Optional<AuditRun> found = repository.findById(id);

        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(readReport(found.get()));
    }

    @GetMapping(value = "/audit/history/{id}/pdf",
                produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Long id) throws IOException {

        Optional<AuditRun> found = repository.findById(id);

        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> report = readReport(found.get());

        byte[] pdf = pdfReportService.generate(report);

        String filename = "finiq-audit-" + id + ".pdf";

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(filename)
                    .build()
                    .toString()
            )
            .body(pdf);
    }

    @GetMapping("/audit/compare")
    public ResponseEntity<?> compare(
            @RequestParam Long firstId,
            @RequestParam Long secondId) throws IOException {

        if (firstId.equals(secondId)) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "Choose two different audit runs."
                ));
        }

        Optional<AuditRun> first = repository.findById(firstId);
        Optional<AuditRun> second = repository.findById(secondId);

        if (first.isEmpty() || second.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> reportA = readReport(first.get());
        Map<String, Object> reportB = readReport(second.get());

        Map<?, ?> summaryA = (Map<?, ?>) reportA.get("summary");
        Map<?, ?> summaryB = (Map<?, ?>) reportB.get("summary");

        Map<String, Long> issuesA = issueCounts(reportA);
        Map<String, Long> issuesB = issueCounts(reportB);

        Set<String> names = new TreeSet<>();

        names.addAll(issuesA.keySet());
        names.addAll(issuesB.keySet());

        List<Map<String, Object>> lines = new ArrayList<>();

        for (String issue : names) {

            long a = issuesA.getOrDefault(issue, 0L);
            long b = issuesB.getOrDefault(issue, 0L);

            Map<String, Object> line = new LinkedHashMap<>();

            line.put("issue", issue);
            line.put("firstCount", a);
            line.put("secondCount", b);
            line.put("change", b - a);

            lines.add(line);
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("firstAuditId", firstId);
        result.put("secondAuditId", secondId);

        result.put("firstFile", first.get().getFileName());
        result.put("secondFile", second.get().getFileName());

        result.put(
            "transactionChange",
            number(summaryB.get("total_transactions")) -
            number(summaryA.get("total_transactions"))
        );

        result.put(
            "findingChange",
            number(summaryB.get("total_findings")) -
            number(summaryA.get("total_findings"))
        );

        result.put(
            "highRiskChange",
            number(summaryB.get("high_risk_findings")) -
            number(summaryA.get("high_risk_findings"))
        );

        result.put("firstSummary", summaryA);
        result.put("secondSummary", summaryB);
        result.put("issueComparison", lines);

        return ResponseEntity.ok(result);
    }

    private Map<String, Object> readReport(AuditRun run)
            throws IOException {

        return objectMapper.readValue(
            run.getReportJson(),
            new TypeReference<Map<String, Object>>() {}
        );
    }

    private Map<String, Long> issueCounts(
            Map<String, Object> report) {

        Map<String, Long> counts = new TreeMap<>();

        Object value = report.get("findings");

        if (value instanceof List<?> findings) {

            for (Object item : findings) {

                if (item instanceof Map<?, ?> finding) {

                    String issue = String.valueOf(
                        finding.get("issue")
                    );

                    counts.merge(issue, 1L, Long::sum);
                }
            }
        }

        return counts;
    }

    private static long number(Object value) {

        if (value instanceof Number n) {
            return n.longValue();
        }

        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return 0L;
        }
    }
}
