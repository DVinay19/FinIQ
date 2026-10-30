
package com.finiq;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_runs")
public class AuditRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String fileName;

    private long totalTransactions;
    private long totalFindings;
    private long highRiskFindings;
    private long mediumRiskFindings;
    private long lowRiskFindings;

    @Column(nullable = false, length = 60)
    private String status;

    @Column(length = 1500)
    private String detectionMethod;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String reportJson;

    public AuditRun() {}

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public long getTotalFindings() {
        return totalFindings;
    }

    public long getHighRiskFindings() {
        return highRiskFindings;
    }

    public long getMediumRiskFindings() {
        return mediumRiskFindings;
    }

    public long getLowRiskFindings() {
        return lowRiskFindings;
    }

    public String getStatus() {
        return status;
    }

    public String getDetectionMethod() {
        return detectionMethod;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setFileName(String value) {
        fileName = value;
    }

    public void setTotalTransactions(long value) {
        totalTransactions = value;
    }

    public void setTotalFindings(long value) {
        totalFindings = value;
    }

    public void setHighRiskFindings(long value) {
        highRiskFindings = value;
    }

    public void setMediumRiskFindings(long value) {
        mediumRiskFindings = value;
    }

    public void setLowRiskFindings(long value) {
        lowRiskFindings = value;
    }

    public void setStatus(String value) {
        status = value;
    }

    public void setDetectionMethod(String value) {
        detectionMethod = value;
    }

    public void setCreatedAt(LocalDateTime value) {
        createdAt = value;
    }

    public void setReportJson(String value) {
        reportJson = value;
    }
}
