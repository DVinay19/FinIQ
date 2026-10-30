
package com.finiq;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceTest {

    private final AuditService service = new AuditService();

    @Test
    void detectsDuplicateInvoiceMissingAmountAndOutlier()
            throws Exception {

        String csv = """
            transaction_id,invoice_id,amount,vendor,transaction_type
            T01,I01,100,Alpha,Payment
            T02,I02,120,Beta,Payment
            T03,I03,140,Gamma,Payment
            T04,I04,160,Delta,Payment
            T05,I05,180,Alpha,Payment
            T06,I06,200,Beta,Payment
            T07,I07,220,Gamma,Payment
            T08,I08,240,Delta,Payment
            T09,I09,260,Alpha,Payment
            T10,I10,280,Beta,Payment
            T11,I11,99999,Gamma,Payment
            T12,I01,100,Alpha,Payment
            T13,I13,,Delta,Payment
            """;

        Map<String, Object> report =
            service.audit(csvFile(csv, "audit-test.csv"));

        Map<?, ?> summary = (Map<?, ?>) report.get("summary");
        List<?> findings = (List<?>) report.get("findings");

        assertEquals(
            13,
            ((Number) summary.get("total_transactions")).intValue()
        );

        assertTrue(hasIssue(findings, "Potential duplicate invoice"));
        assertTrue(hasIssue(findings,
            "Missing or invalid transaction data"));
        assertTrue(hasIssue(findings, "Unusual transaction amount"));
    }

    @Test
    void calculatesVendorExposure() throws Exception {

        String csv = """
            transaction_id,invoice_id,amount,vendor,transaction_type
            T01,I01,100,Alpha,Payment
            T02,I02,400,Beta,Payment
            T03,I03,200,Alpha,Payment
            """;

        Map<String, Object> report =
            service.audit(csvFile(csv, "vendors.csv"));

        List<?> vendors = (List<?>) report.get("vendorAnalysis");

        assertEquals(2, vendors.size());

        Map<?, ?> first = (Map<?, ?>) vendors.get(0);

        assertEquals("Beta", first.get("vendor"));
        assertEquals(1L, first.get("transactionCount"));
        assertEquals("400", first.get("totalAmount").toString());
    }

    @Test
    void checksRepeatedPaymentsWhenDatesExist() throws Exception {

        String csv = """
            transaction_id,invoice_id,amount,vendor,transaction_type,transaction_date
            T01,I01,100,Alpha,Payment,2026-01-01
            T02,I02,100,Alpha,Payment,2026-01-01
            T03,I03,120,Beta,Payment,2026-01-01
            T04,I04,140,Gamma,Payment,2026-01-01
            """;

        Map<String, Object> report =
            service.audit(csvFile(csv, "dated.csv"));

        List<?> findings = (List<?>) report.get("findings");

        assertTrue(hasIssue(findings, "Repeated payment pattern"));
        assertEquals(Boolean.TRUE, report.get("dateAnalysisAvailable"));
    }

    @Test
    void rejectsUnsupportedFile() {

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "notes.txt",
            "text/plain",
            "Not supported".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> service.audit(file)
        );
    }

    @Test
    void rejectsMissingRequiredColumns() {

        String csv = """
            transaction_id,amount,vendor
            T01,100,Alpha
            """;

        MockMultipartFile file = csvFile(csv, "incomplete.csv");

        assertThrows(
            IllegalArgumentException.class,
            () -> service.audit(file)
        );
    }

    private static MockMultipartFile csvFile(
            String content, String filename) {

        return new MockMultipartFile(
            "file",
            filename,
            "text/csv",
            content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static boolean hasIssue(
            List<?> findings, String expected) {

        return findings.stream().anyMatch(item ->
            item instanceof Map<?, ?> finding &&
            expected.equals(finding.get("issue"))
        );
    }
}
