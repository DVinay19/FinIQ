
package com.finiq;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
public class PdfReportService {

    public byte[] generate(Map<String, Object> report)
            throws IOException {

        List<String> lines = new ArrayList<>();

        lines.add("FINIQ - FINANCIAL AUDIT REPORT");
        lines.add("==============================================");
        lines.add("Generated: " + new Date());
        lines.add("Audit ID: " +
            report.getOrDefault("auditRunId", "N/A"));
        lines.add("Source file: " +
            report.getOrDefault("source_file", "Uploaded file"));
        lines.add("");

        Object summaryObject = report.get("summary");

        if (summaryObject instanceof Map<?, ?> summary) {

            lines.add("AUDIT SUMMARY");
            lines.add("----------------------------------------------");

            lines.add("Transactions: " +
                summary.get("total_transactions"));

            lines.add("Total amount: " +
                summary.get("total_amount"));

            lines.add("Total findings: " +
                summary.get("total_findings"));

            lines.add("High risk: " +
                summary.get("high_risk_findings"));

            lines.add("Medium risk: " +
                summary.get("medium_risk_findings"));

            lines.add("Low risk: " +
                summary.get("low_risk_findings"));

            lines.add("Vendors analyzed: " +
                summary.get("vendor_count"));

            lines.add("Audit status: " + summary.get("status"));
            lines.add("");
        }

        lines.add("VENDOR ANALYSIS");
        lines.add("----------------------------------------------");

        Object vendorObject = report.get("vendorAnalysis");

        if (vendorObject instanceof List<?> vendors &&
                !vendors.isEmpty()) {

            for (Object item : vendors) {

                if (item instanceof Map<?, ?> vendor) {

                    lines.add(
                        vendor.get("vendor") +
                        " | Transactions: " +
                        vendor.get("transactionCount") +
                        " | Total: " +
                        vendor.get("totalAmount") +
                        " | Exposure: " +
                        vendor.get("exposurePercentage") + "%"
                    );
                }
            }

        } else {
            lines.add("No vendor data available.");
        }

        lines.add("");
        lines.add("AUDIT FINDINGS");
        lines.add("----------------------------------------------");

        Object findingsObject = report.get("findings");

        if (findingsObject instanceof List<?> findings &&
                !findings.isEmpty()) {

            int count = 1;

            for (Object item : findings) {

                if (item instanceof Map<?, ?> finding) {

                    lines.add(count++ + ". " + finding.get("issue"));

                    lines.add(
                        "Transaction: " + finding.get("transaction_id") +
                        " | Row: " + finding.get("row_number")
                    );

                    lines.add("Severity: " + finding.get("risk"));

                    lines.add("Evidence: " + finding.get("details"));
                    lines.add("");
                }
            }

        } else {
            lines.add("No findings were raised.");
        }

        lines.add("");
        lines.add("DISCLAIMER");
        lines.add(
            "Findings are indicators for human review, not proof of fraud."
        );

        List<String> wrapped = new ArrayList<>();

        for (String line : lines) {

            if (line.length() <= 95) {
                wrapped.add(line);
            } else {

                for (int i = 0; i < line.length(); i += 95) {
                    wrapped.add(
                        line.substring(i, Math.min(i + 95, line.length()))
                    );
                }
            }
        }

        try (PDDocument document = new PDDocument()) {

            PDFont regular = new PDType1Font(
                Standard14Fonts.FontName.HELVETICA
            );

            PDFont bold = new PDType1Font(
                Standard14Fonts.FontName.HELVETICA_BOLD
            );

            final int linesPerPage = 43;

            for (int start = 0; start < wrapped.size();
                 start += linesPerPage) {

                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try (PDPageContentStream stream =
                         new PDPageContentStream(document, page)) {

                    float y = 785;

                    for (int i = start;
                         i < Math.min(start + linesPerPage, wrapped.size());
                         i++) {

                        String line = wrapped.get(i)
                            .replaceAll("[^\\x20-\\x7E]", "?");

                        boolean heading =
                            line.equals("FINIQ - FINANCIAL AUDIT REPORT") ||
                            line.equals("AUDIT SUMMARY") ||
                            line.equals("VENDOR ANALYSIS") ||
                            line.equals("AUDIT FINDINGS") ||
                            line.equals("DISCLAIMER");

                        stream.beginText();

                        stream.setFont(
                            heading ? bold : regular,
                            heading ? 12 : 9
                        );

                        stream.newLineAtOffset(48, y);
                        stream.showText(line);
                        stream.endText();

                        y -= heading ? 23 : 15;
                    }

                    stream.beginText();
                    stream.setFont(regular, 8);
                    stream.newLineAtOffset(48, 30);
                    stream.showText(
                        "FinIQ | Financial Audit Intelligence"
                    );
                    stream.endText();
                }
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            document.save(output);

            return output.toByteArray();
        }
    }
}
