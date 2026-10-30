
package com.finiq;

import org.apache.commons.csv.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class AuditService {

    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MIN_OUTLIER_SAMPLE = 10;
    private static final int MAX_VENDOR_DAILY_COUNT = 5;

    private static final List<String> REQUIRED = List.of(
        "transaction_id", "invoice_id", "amount",
        "vendor", "transaction_type"
    );

    private record ParsedFile(
        List<String> headers,
        List<Map<String, String>> rows
    ) {}

    private record PaymentKey(
        String vendor, String amount, String type, LocalDate date
    ) {}

    public Map<String, Object> audit(MultipartFile file)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Select a non-empty file.");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                "File exceeds the 10 MB limit."
            );
        }

        String name = Objects.requireNonNullElse(
            file.getOriginalFilename(), ""
        ).toLowerCase(Locale.ROOT);

        ParsedFile parsed;

        if (name.endsWith(".csv")) {
            parsed = readCsv(file);
        } else if (name.endsWith(".xlsx") || name.endsWith(".xlsm")) {
            parsed = readExcel(file);
        } else {
            throw new IllegalArgumentException(
                "Unsupported format. Upload CSV or XLSX."
            );
        }

        return analyze(parsed);
    }

    private ParsedFile readCsv(MultipartFile file) throws IOException {

        try (
            Reader reader = new BufferedReader(
                new InputStreamReader(
                    file.getInputStream(),
                    StandardCharsets.UTF_8
                )
            );

            CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get()
                .parse(reader)
        ) {

            List<String> headers = parser.getHeaderNames()
                .stream()
                .map(AuditService::normalize)
                .toList();

            validateHeaders(headers);

            List<Map<String, String>> rows = new ArrayList<>();

            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();

                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i),
                        i < record.size() ? record.get(i).trim() : "");
                }

                rows.add(row);
            }

            return new ParsedFile(headers, rows);

        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IOException("Could not read CSV file.", ex);
        }
    }

    private ParsedFile readExcel(MultipartFile file) throws IOException {

        try (
            InputStream input = file.getInputStream();
            Workbook workbook = WorkbookFactory.create(input)
        ) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException(
                    "Workbook contains no sheets."
                );
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());

            if (headerRow == null || headerRow.getLastCellNum() <= 0) {
                throw new IllegalArgumentException(
                    "Excel header row is empty."
                );
            }

            DataFormatter formatter = new DataFormatter(Locale.US);

            FormulaEvaluator evaluator = workbook.getCreationHelper()
                .createFormulaEvaluator();

            List<String> headers = new ArrayList<>();

            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                headers.add(normalize(formatter.formatCellValue(
                    headerRow.getCell(c), evaluator
                )));
            }

            validateHeaders(headers);

            List<Map<String, String>> rows = new ArrayList<>();

            for (int r = headerRow.getRowNum() + 1;
                 r <= sheet.getLastRowNum(); r++) {

                Row source = sheet.getRow(r);
                Map<String, String> row = new LinkedHashMap<>();
                boolean nonEmpty = false;

                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = source == null ? null : source.getCell(c);

                    String value = cell == null
                        ? ""
                        : formatter.formatCellValue(cell, evaluator).trim();

                    row.put(headers.get(c), value);

                    if (!value.isBlank()) {
                        nonEmpty = true;
                    }
                }

                if (nonEmpty) {
                    rows.add(row);
                }
            }

            return new ParsedFile(headers, rows);

        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException(
                "Could not read Excel workbook. Check its format.", ex
            );
        }
    }

    private void validateHeaders(List<String> headers) {

        if (headers.isEmpty() ||
            headers.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                "File contains an empty or missing column heading."
            );
        }

        if (new HashSet<>(headers).size() != headers.size()) {
            throw new IllegalArgumentException(
                "File contains duplicate column headings."
            );
        }

        List<String> missing = REQUIRED.stream()
            .filter(c -> !headers.contains(c))
            .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                "Missing required columns: " +
                String.join(", ", missing)
            );
        }
    }

    private Map<String, Object> analyze(ParsedFile parsed) {

        List<Map<String, String>> rows = parsed.rows();

        List<Map<String, Object>> findings = new ArrayList<>();

        Map<String, List<Integer>> invoiceGroups = new HashMap<>();
        Map<PaymentKey, List<Integer>> paymentGroups = new HashMap<>();
        Map<String, List<Integer>> vendorDayGroups = new HashMap<>();

        List<Double> amounts = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        String dateColumn = findDateColumn(parsed.headers());

        for (int i = 0; i < rows.size(); i++) {

            Map<String, String> row = rows.get(i);

            String id = idFor(row, i);
            String vendor = val(row, "vendor");
            String invoice = val(row, "invoice_id");
            String type = val(row, "transaction_type");
            BigDecimal amount = parseAmount(val(row, "amount"));

            if (val(row, "transaction_id").isBlank()) {
                add(findings, id, i + 2,
                    "Missing transaction identifier", "Low",
                    "Transaction ID is blank.");
            }

            if (vendor.isBlank()) {
                add(findings, id, i + 2,
                    "Missing vendor", "Medium",
                    "Vendor information is missing.");
            }

            if (invoice.isBlank()) {
                add(findings, id, i + 2,
                    "Missing invoice reference", "Medium",
                    "Invoice reference is missing.");
            }

            if (type.isBlank()) {
                add(findings, id, i + 2,
                    "Missing transaction type", "Low",
                    "Transaction type is missing.");
            }

            if (amount == null) {
                add(findings, id, i + 2,
                    "Missing or invalid transaction data", "Medium",
                    "Amount is missing, non-positive or invalid.");
            } else {
                amounts.add(amount.doubleValue());
                totalAmount = totalAmount.add(amount);
            }

            if (!vendor.isBlank() && !invoice.isBlank()) {

                String key =
                    vendor.toLowerCase(Locale.ROOT) + "\u001f" +
                    invoice.toLowerCase(Locale.ROOT);

                invoiceGroups.computeIfAbsent(
                    key, k -> new ArrayList<>()
                ).add(i);
            }

            if (dateColumn != null) {

                String dateText = val(row, dateColumn);
                LocalDate date = parseDate(dateText);

                if (!dateText.isBlank() && date == null) {
                    add(findings, id, i + 2,
                        "Invalid transaction date", "Low",
                        "Could not interpret date: " + dateText);
                }

                if (date != null && !vendor.isBlank()) {

                    String dayKey =
                        vendor.toLowerCase(Locale.ROOT) + "|" + date;

                    vendorDayGroups.computeIfAbsent(
                        dayKey, k -> new ArrayList<>()
                    ).add(i);

                    if (amount != null && isPayment(type)) {

                        PaymentKey paymentKey = new PaymentKey(
                            vendor.toLowerCase(Locale.ROOT),
                            amount.stripTrailingZeros().toPlainString(),
                            type.toLowerCase(Locale.ROOT),
                            date
                        );

                        paymentGroups.computeIfAbsent(
                            paymentKey, k -> new ArrayList<>()
                        ).add(i);
                    }
                }
            }
        }

        // Duplicate invoice analysis.
        for (List<Integer> group : invoiceGroups.values()) {

            if (group.size() > 1) {

                for (int index : group) {

                    Map<String, String> row = rows.get(index);

                    add(findings, idFor(row, index), index + 2,
                        "Potential duplicate invoice", "High",
                        "Invoice " + val(row, "invoice_id") +
                        " appears more than once for vendor " +
                        val(row, "vendor") + ".");
                }
            }
        }

        // Same vendor, amount, type and transaction date.
        int duplicatePaymentGroups = 0;

        for (Map.Entry<PaymentKey, List<Integer>> entry :
                paymentGroups.entrySet()) {

            List<Integer> group = entry.getValue();

            if (group.size() < 2) continue;

            duplicatePaymentGroups++;

            for (int index : group) {

                Map<String, String> row = rows.get(index);

                add(findings, idFor(row, index), index + 2,
                    "Repeated payment pattern", "High",
                    "Same vendor, amount, type and date occur " +
                    group.size() + " times. Verify whether these " +
                    "are separate payments.");
            }
        }

        int frequencyGroups = 0;

        for (Map.Entry<String, List<Integer>> entry :
                vendorDayGroups.entrySet()) {

            List<Integer> group = entry.getValue();

            if (group.size() >= MAX_VENDOR_DAILY_COUNT) {

                frequencyGroups++;

                for (int index : group) {

                    Map<String, String> row = rows.get(index);

                    add(findings, idFor(row, index), index + 2,
                        "High vendor transaction frequency", "Medium",
                        "Vendor has " + group.size() +
                        " transactions on the same date.");
                }
            }
        }

        int outlierFindings =
            detectOutliers(rows, amounts, findings);

        List<Map<String, Object>> vendorAnalysis =
            buildVendorAnalysis(rows);

        long high = countRisk(findings, "High");
        long medium = countRisk(findings, "Medium");
        long low = countRisk(findings, "Low");

        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("total_transactions", rows.size());
        summary.put("total_amount", totalAmount);
        summary.put("total_findings", findings.size());

        summary.put("high_risk_findings", high);
        summary.put("medium_risk_findings", medium);
        summary.put("low_risk_findings", low);

        summary.put("vendor_count", vendorAnalysis.size());
        summary.put("duplicate_payment_groups", duplicatePaymentGroups);
        summary.put("vendor_frequency_groups", frequencyGroups);
        summary.put("outlier_findings", outlierFindings);

        summary.put("status",
            findings.isEmpty() ? "NO FLAGS" : "REVIEW REQUIRED");

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("summary", summary);
        result.put("findings", findings);
        result.put("vendorAnalysis", vendorAnalysis);

        result.put("dateAnalysisAvailable", dateColumn != null);
        result.put("dateColumn", dateColumn);

        result.put("detection_method",
            "Duplicate invoice matching, data validation, " +
            "vendor exposure, optional payment-pattern and frequency " +
            "screening, IQR and modified MAD outlier analysis");

        return result;
    }

    private int detectOutliers(
            List<Map<String, String>> rows,
            List<Double> amounts,
            List<Map<String, Object>> findings) {

        if (amounts.size() < MIN_OUTLIER_SAMPLE ||
            amounts.stream().distinct().count() < 2) {
            return 0;
        }

        List<Double> sorted = amounts.stream()
            .sorted()
            .toList();

        double q1 = percentile(sorted, .25);
        double q3 = percentile(sorted, .75);
        double iqr = q3 - q1;

        double lower = q1 - 1.5 * iqr;
        double upper = q3 + 1.5 * iqr;

        double median = percentile(sorted, .5);

        List<Double> deviations = sorted.stream()
            .map(v -> Math.abs(v - median))
            .sorted()
            .toList();

        double mad = percentile(deviations, .5);

        int flagged = 0;

        for (int i = 0; i < rows.size(); i++) {

            BigDecimal amount = parseAmount(val(rows.get(i), "amount"));
            if (amount == null) continue;

            double value = amount.doubleValue();

            boolean iqrOutlier = value < lower || value > upper;

            boolean madOutlier = mad > 0 &&
                0.6745 * Math.abs(value - median) / mad > 3.5;

            if (iqrOutlier || madOutlier) {

                flagged++;

                String method =
                    iqrOutlier && madOutlier
                        ? "IQR and modified MAD"
                        : iqrOutlier ? "IQR" : "modified MAD";

                add(findings, idFor(rows.get(i), i), i + 2,
                    "Unusual transaction amount", "Medium",
                    "Amount " + amount.toPlainString() +
                    " was flagged by " + method +
                    " statistical screening.");
            }
        }

        return flagged;
    }

    private List<Map<String, Object>> buildVendorAnalysis(
            List<Map<String, String>> rows) {

        class Aggregate {
            long count;
            BigDecimal total = BigDecimal.ZERO;
        }

        Map<String, Aggregate> vendors =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Map<String, String> row : rows) {

            String vendor = val(row, "vendor");
            BigDecimal amount = parseAmount(val(row, "amount"));

            if (vendor.isBlank() || amount == null) continue;

            Aggregate a = vendors.computeIfAbsent(
                vendor, k -> new Aggregate()
            );

            a.count++;
            a.total = a.total.add(amount);
        }

        BigDecimal grandTotal = vendors.values().stream()
            .map(v -> v.total)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> result = new ArrayList<>();

        vendors.entrySet().stream()
            .sorted((a, b) ->
                b.getValue().total.compareTo(a.getValue().total))
            .limit(20)
            .forEach(entry -> {

                Aggregate a = entry.getValue();

                BigDecimal average = a.total.divide(
                    BigDecimal.valueOf(a.count),
                    2,
                    java.math.RoundingMode.HALF_UP
                );

                double exposure = grandTotal.signum() == 0
                    ? 0
                    : a.total.multiply(BigDecimal.valueOf(100))
                        .divide(grandTotal, 2,
                            java.math.RoundingMode.HALF_UP)
                        .doubleValue();

                Map<String, Object> item = new LinkedHashMap<>();

                item.put("vendor", entry.getKey());
                item.put("transactionCount", a.count);
                item.put("totalAmount", a.total);
                item.put("averageAmount", average);
                item.put("exposurePercentage", exposure);

                result.add(item);
            });

        return result;
    }

    private static String findDateColumn(List<String> headers) {

        for (String candidate : List.of(
                "transaction_date",
                "transaction_datetime",
                "date")) {

            if (headers.contains(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static LocalDate parseDate(String value) {

        if (value == null || value.isBlank()) return null;

        String v = value.trim();

        for (String pattern : List.of(
                "uuuu-MM-dd",
                "uuuu-MM-dd HH:mm:ss",
                "d/M/uuuu",
                "d-M-uuuu",
                "M/d/uuuu",
                "d.M.uuuu")) {

            try {
                return pattern.contains("HH")
                    ? LocalDateTime.parse(
                        v, DateTimeFormatter.ofPattern(pattern)
                      ).toLocalDate()
                    : LocalDate.parse(
                        v, DateTimeFormatter.ofPattern(pattern)
                      );
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }

        return null;
    }

    private static boolean isPayment(String type) {
        String t = type.toLowerCase(Locale.ROOT);
        return t.contains("payment") || t.contains("pay");
    }

    private static long countRisk(
            List<Map<String, Object>> findings, String risk) {

        return findings.stream()
            .filter(f -> risk.equals(f.get("risk")))
            .count();
    }

    private static double percentile(
            List<Double> values, double p) {

        double position = p * (values.size() - 1);

        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);

        if (lower == upper) return values.get(lower);

        double fraction = position - lower;

        return values.get(lower) * (1 - fraction)
            + values.get(upper) * fraction;
    }

    private static String normalize(String value) {
        return value == null
            ? ""
            : value.replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String val(
            Map<String, String> row, String key) {

        return Objects.requireNonNullElse(row.get(key), "").trim();
    }

    private static String idFor(
            Map<String, String> row, int index) {

        String id = val(row, "transaction_id");

        return id.isBlank() ? "ROW-" + (index + 2) : id;
    }

    private static BigDecimal parseAmount(String text) {

        if (text == null || text.isBlank()) return null;

        try {

            String cleaned = text.trim()
                .replace(",", "")
                .replace("₹", "")
                .replace("INR", "")
                .trim();

            BigDecimal number = new BigDecimal(cleaned);

            return number.signum() > 0 ? number : null;

        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void add(
            List<Map<String, Object>> findings,
            String id,
            int row,
            String issue,
            String risk,
            String details) {

        Map<String, Object> finding = new LinkedHashMap<>();

        finding.put("transaction_id", id);
        finding.put("row_number", row);
        finding.put("issue", issue);
        finding.put("risk", risk);
        finding.put("details", details);

        findings.add(finding);
    }
}
