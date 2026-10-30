
"use strict";

const $ = selector => document.querySelector(selector);

let latestReport = null;
let latestFileName = "";
let noticeTimer = null;


/* =====================================
   01. PAGE NAVIGATION
===================================== */

function showPage(page, updateAddress = true) {
  const validPages = [
    "overview",
    "analysis",
    "findings",
    "analytics",
    "history"
  ];

  if (!validPages.includes(page)) page = "overview";

  document.querySelectorAll(".page-view").forEach(view => {
    view.classList.toggle("active", view.dataset.view === page);
  });

  document.querySelectorAll(".top-nav .nav").forEach(button => {
    const active = button.dataset.page === page;

    button.classList.toggle("active", active);

    if (active) {
      button.setAttribute("aria-current", "page");
    } else {
      button.removeAttribute("aria-current");
    }
  });

  if (updateAddress) {
    window.history.replaceState(null, "", `#${page}`);
  }

  window.scrollTo({ top: 0, behavior: "smooth" });
}

document.querySelectorAll("[data-page]").forEach(button => {
  button.addEventListener("click", () => {
    showPage(button.dataset.page);
  });
});

document.querySelectorAll("[data-go-page]").forEach(button => {
  button.addEventListener("click", () => {
    showPage(button.dataset.goPage);
  });
});

const initialPage = window.location.hash.replace("#", "");

showPage(
  ["overview", "analysis", "findings", "analytics", "history"]
    .includes(initialPage) ? initialPage : "overview",
  false
);


/* =====================================
   02. COMMON UTILITIES
===================================== */

function escapeHTML(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  })[char]);
}

function setNotice(message, type = "success") {
  const el = $("#notice");

  el.textContent = message;
  el.className = `notice ${type}`;
  el.hidden = false;

  clearTimeout(noticeTimer);

  noticeTimer = setTimeout(() => {
    el.hidden = true;
  }, 7000);
}

function setMessage(message, type = "") {
  const el = $("#message");

  el.textContent = message;
  el.className = `form-message ${type}`.trim();
}

async function requestJSON(url, options = {}) {
  const response = await fetch(url, options);

  let body = {};

  try {
    body = await response.json();
  } catch {
    body = {};
  }

  if (!response.ok) {
    throw new Error(
      body.error ||
      body.message ||
      `Request failed with status ${response.status}.`
    );
  }

  return body;
}

function downloadFile(filename, content, mime) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);

  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;

  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();

  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function csvCell(value) {
  let text = String(value ?? "");

  // Reduce spreadsheet formula-injection risk in CSV exports.
  if (/^[\t\r=+\-@]/.test(text)) {
    text = "'" + text;
  }

  return `"${text.replace(/"/g, '""')}"`;
}


/* =====================================
   03. DASHBOARD SUMMARY
===================================== */

function updateSummary(report) {
  const summary = report.summary || {};

  $("#totalTransactions").textContent =
    Number(summary.total_transactions || 0).toLocaleString("en-IN");

  $("#totalFindings").textContent =
    Number(summary.total_findings || 0).toLocaleString("en-IN");

  $("#highRisk").textContent =
    Number(summary.high_risk_findings || 0).toLocaleString("en-IN");

  $("#auditStatus").textContent = summary.status || "COMPLETE";

  $("#statusDescription").textContent =
    Number(summary.total_findings || 0) > 0
      ? "Potential issues require review"
      : "No issues flagged by current checks";

  const sourceFile =
    latestFileName || report.source_file || "Uploaded file";

  $("#summaryFile").textContent = sourceFile;
  $("#summaryFileAnalytics").textContent = sourceFile;

  $("#summaryRecords").textContent =
    summary.total_transactions ?? 0;

  $("#summaryRecordsAnalytics").textContent =
    summary.total_transactions ?? 0;

  $("#summaryFindings").textContent =
    summary.total_findings ?? 0;

  $("#summaryFindingsAnalytics").textContent =
    summary.total_findings ?? 0;

  const method =
    report.detection_method || "Audit rules";

  $("#summaryMethod").textContent = method;
  $("#summaryMethodAnalytics").textContent = method;

  $("#exportJson").disabled = false;
  $("#exportCsv").disabled = false;
}


/* =====================================
   04. RISK DISTRIBUTION
===================================== */

function renderRiskChart(findings) {
  const levels = [
    { name: "High risk", cls: "high" },
    { name: "Medium risk", cls: "medium" },
    { name: "Low risk", cls: "low" }
  ];

  const counts = levels.map(level => ({
    ...level,
    count: findings.filter(f =>
      String(f.risk || "").toLowerCase() === level.cls
    ).length
  }));

  const total = findings.length;

  $("#chartTotal").textContent = `${total} findings`;

  if (total === 0) {
    $("#riskChart").innerHTML = `
      <div class="empty-chart">
        No findings were raised by the current checks.
      </div>`;
    return;
  }

  $("#riskChart").innerHTML = counts.map(item => {
    const percent = item.count / total * 100;

    return `
      <div class="risk-line">
        <span class="risk-name">${item.name}</span>

        <div class="risk-track"
             role="img"
             aria-label="${item.name}: ${item.count}">
          <div class="risk-fill ${item.cls}"
               style="width:${percent}%"></div>
        </div>

        <span class="risk-number">${item.count}</span>
      </div>`;
  }).join("");
}


/* =====================================
   05. FINDINGS REGISTER
===================================== */

function renderFindings() {
  if (!latestReport) return;

  const findings = latestReport.findings || [];

  const search = $("#searchFindings").value
    .trim()
    .toLowerCase();

  const risk = $("#riskFilter").value;

  const filtered = findings.filter(f => {
    const text = [
      f.transaction_id,
      f.issue,
      f.details,
      f.row_number,
      f.risk
    ].join(" ").toLowerCase();

    return text.includes(search) &&
      (risk === "ALL" || f.risk === risk);
  });

  $("#findingsCount").textContent =
    `${filtered.length} finding(s)`;

  $("#tableStatus").textContent =
    `${filtered.length} shown · ${findings.length} total findings`;

  if (filtered.length === 0) {
    $("#findingRows").innerHTML = `
      <tr>
        <td colspan="4" class="empty-cell">
          ${findings.length
            ? "No findings match these filters."
            : "No audit flags were detected by the current checks."}
        </td>
      </tr>`;
    return;
  }

  $("#findingRows").innerHTML = filtered.map(f => {
    const level = ["High", "Medium", "Low"].includes(f.risk)
      ? f.risk
      : "Medium";

    return `
      <tr>
        <td>
          <span class="row-id">
            ${escapeHTML(f.transaction_id || "—")}
          </span>
          <span class="row-number">
            Row ${escapeHTML(f.row_number ?? "—")}
          </span>
        </td>

        <td>
          <span class="issue-title">
            ${escapeHTML(f.issue || "Unclassified finding")}
          </span>
        </td>

        <td>
          <span class="risk-badge ${level.toLowerCase()}">
            ${level}
          </span>
        </td>

        <td class="evidence">
          ${escapeHTML(f.details || "No additional evidence supplied.")}
        </td>
      </tr>`;
  }).join("");
}


/* =====================================
   06. VENDOR ANALYSIS
===================================== */

function renderVendorAnalysis(report) {
  const body = $("#vendorRows");
  const vendors = report.vendorAnalysis || [];

  if (vendors.length === 0) {
    body.innerHTML = `
      <tr>
        <td colspan="4" class="empty-cell">
          No vendor totals available for this audit.
        </td>
      </tr>`;
    return;
  }

  body.innerHTML = vendors.map(vendor => {
    const percent = Math.max(
      0,
      Math.min(100, Number(vendor.exposurePercentage) || 0)
    );

    return `
      <tr>
        <td><strong>${escapeHTML(vendor.vendor)}</strong></td>

        <td>${Number(vendor.transactionCount || 0)}</td>

        <td>${escapeHTML(String(vendor.totalAmount ?? "—"))}</td>

        <td>
          <div class="vendor-exposure">
            <div class="vendor-exposure-track">
              <div class="vendor-exposure-fill"
                   style="width:${percent}%"></div>
            </div>
            <strong>${percent.toFixed(1)}%</strong>
          </div>
        </td>
      </tr>`;
  }).join("");
}


/* =====================================
   07. AUDIT HISTORY
===================================== */

function populateCompareOptions(records) {
  ["#compareFirst", "#compareSecond"].forEach(selector => {
    const select = $(selector);
    const previous = select.value;

    select.innerHTML = `
      <option value="">Select an audit</option>
      ${records.map(run => `
        <option value="${Number(run.id)}">
          #${Number(run.id)} — ${escapeHTML(run.fileName)}
        </option>
      `).join("")}
    `;

    if (records.some(r => String(r.id) === previous)) {
      select.value = previous;
    }
  });
}

async function loadHistory() {
  const body = $("#historyRows");
  const status = $("#historyStatus");

  body.innerHTML = `
    <tr>
      <td colspan="7" class="empty-cell">Loading...</td>
    </tr>`;

  status.textContent = "Retrieving saved audits...";

  try {
    const records = await requestJSON("/api/audit/history");

    if (!Array.isArray(records) || records.length === 0) {
      body.innerHTML = `
        <tr>
          <td colspan="7" class="empty-cell">
            No saved audit runs yet.
          </td>
        </tr>`;

      status.textContent = "0 saved audit runs";
      populateCompareOptions([]);
      return;
    }

    body.innerHTML = records.map(run => {
      const id = Number(run.id);

      const date = run.createdAt
        ? new Date(run.createdAt).toLocaleString("en-IN")
        : "—";

      return `
        <tr>
          <td><strong>#${id}</strong></td>

          <td>${escapeHTML(run.fileName)}</td>

          <td>${Number(run.totalTransactions || 0)}</td>

          <td>${Number(run.totalFindings || 0)}</td>

          <td>
            <span class="history-status">
              ${escapeHTML(run.status || "COMPLETE")}
            </span>
          </td>

          <td>${escapeHTML(date)}</td>

          <td>
            <div class="history-actions">
              <button class="history-action"
                      data-open-audit="${id}">
                Open report
              </button>

              <a class="history-action"
                 href="/api/audit/history/${id}/pdf"
                 target="_blank"
                 rel="noopener">
                PDF
              </a>
            </div>
          </td>
        </tr>`;
    }).join("");

    status.textContent =
      `${records.length} saved audit run(s)`;

    populateCompareOptions(records);

  } catch (error) {
    body.innerHTML = `
      <tr>
        <td colspan="7" class="empty-cell">
          Unable to retrieve audit history.
        </td>
      </tr>`;

    status.textContent = error.message;
  }
}

async function openSavedAudit(id) {
  try {
    const report = await requestJSON(
      `/api/audit/history/${encodeURIComponent(id)}`
    );

    if (!report.summary || !Array.isArray(report.findings)) {
      throw new Error("Saved report has an unexpected format.");
    }

    latestReport = report;

    latestFileName =
      report.source_file || `Audit #${id}`;

    updateSummary(report);
    renderRiskChart(report.findings);
    renderVendorAnalysis(report);
    renderFindings();

    setNotice(`Saved audit #${id} reopened successfully.`);

    showPage("findings");

  } catch (error) {
    setNotice(
      `Could not reopen audit: ${error.message}`,
      "error"
    );
  }
}


/* =====================================
   08. AUDIT COMPARISON
===================================== */

async function compareAudits() {
  const first = $("#compareFirst").value;
  const second = $("#compareSecond").value;
  const result = $("#compareResults");
  const button = $("#compareButton");

  if (!first || !second) {
    result.textContent = "Select two audit runs first.";
    return;
  }

  if (first === second) {
    result.textContent = "Choose two different audit runs.";
    return;
  }

  button.disabled = true;
  button.textContent = "Comparing...";

  try {
    const query = new URLSearchParams({
      firstId: first,
      secondId: second
    });

    const comparison = await requestJSON(
      `/api/audit/compare?${query.toString()}`
    );

    const lines = comparison.issueComparison || [];

    result.innerHTML = `
      <div class="compare-metric">
        <span>Transaction change</span>
        <strong>${Number(comparison.transactionChange)}</strong>
      </div>

      <div class="compare-metric">
        <span>Finding change</span>
        <strong>${Number(comparison.findingChange)}</strong>
      </div>

      <div class="compare-metric">
        <span>High-risk finding change</span>
        <strong>${Number(comparison.highRiskChange)}</strong>
      </div>

      <div class="comparison-detail">
        <strong>Finding breakdown</strong>

        ${lines.length
          ? lines.map(line => `
              <div class="compare-metric">
                <span>${escapeHTML(line.issue)}</span>
                <strong>
                  ${Number(line.firstCount)} →
                  ${Number(line.secondCount)}
                  (${Number(line.change) > 0 ? "+" : ""}
                   ${Number(line.change)})
                </strong>
              </div>
            `).join("")
          : "<p>No findings in either report.</p>"
        }
      </div>

      <p class="muted">
        Audit #${Number(comparison.firstAuditId)}
        compared with audit #${Number(comparison.secondAuditId)}.
      </p>
    `;

  } catch (error) {
    result.textContent =
      `Comparison failed: ${error.message}`;

  } finally {
    button.disabled = false;
    button.textContent = "Compare audit runs →";
  }
}


/* =====================================
   09. FILE SELECTION AND DRAG/DROP
===================================== */

$("#auditFile").addEventListener("change", () => {
  const file = $("#auditFile").files[0];

  $("#fileLabel").textContent = file
    ? file.name
    : "Choose a financial data file";

  setMessage(file
    ? `Selected: ${file.name}`
    : "Ready to analyze your data."
  );
});

const dropZone = $("#dropZone");

dropZone.addEventListener("dragover", event => {
  event.preventDefault();
  dropZone.classList.add("dragging");
});

dropZone.addEventListener("dragleave", () => {
  dropZone.classList.remove("dragging");
});

dropZone.addEventListener("drop", event => {
  event.preventDefault();
  dropZone.classList.remove("dragging");

  const file = event.dataTransfer.files[0];
  if (!file) return;

  const extension = file.name.split(".").pop().toLowerCase();

  if (!["csv", "xlsx", "xlsm"].includes(extension)) {
    setMessage("Choose a CSV or Excel workbook.", "error");
    return;
  }

  if (file.size > 10 * 1024 * 1024) {
    setMessage("The file exceeds the 10 MB limit.", "error");
    return;
  }

  const transfer = new DataTransfer();
  transfer.items.add(file);

  $("#auditFile").files = transfer.files;
  $("#fileLabel").textContent = file.name;

  setMessage(`Selected: ${file.name}`);
});


/* =====================================
   10. UPLOAD AND RUN AUDIT
===================================== */

$("#auditForm").addEventListener("submit", async event => {
  event.preventDefault();

  const file = $("#auditFile").files[0];

  if (!file) {
    setMessage("Choose a file before starting the audit.", "error");
    return;
  }

  const extension = file.name.split(".").pop().toLowerCase();

  if (!["csv", "xlsx", "xlsm"].includes(extension)) {
    setMessage("Supported formats are CSV, XLSX and XLSM.", "error");
    return;
  }

  if (file.size > 10 * 1024 * 1024) {
    setMessage("The file exceeds the 10 MB limit.", "error");
    return;
  }

  const button = $("#auditButton");

  button.disabled = true;
  button.innerHTML = `
    <span>Analyzing financial data...</span>
    <span>◌</span>`;

  setMessage("Uploading file to the Java audit engine...");

  const formData = new FormData();
  formData.append("file", file);

  try {
    const report = await requestJSON("/api/audit", {
      method: "POST",
      body: formData
    });

    if (!report.summary || !Array.isArray(report.findings)) {
      throw new Error(
        "The server response did not contain audit findings."
      );
    }

    latestReport = report;
    latestFileName = file.name;

    updateSummary(report);
    renderRiskChart(report.findings);
    renderVendorAnalysis(report);
    renderFindings();

    await loadHistory();

    const total = Number(report.summary.total_findings || 0);

    setMessage(
      `Analysis complete: ${report.summary.total_transactions} records checked, ${total} findings returned.`,
      "success"
    );

    setNotice(
      total
        ? `Audit complete. ${total} potential issue(s) require review.`
        : "Audit complete. No issues were flagged by the current checks."
    );

    // Move to analytics after a successful analysis.
    showPage("analytics");

  } catch (error) {
    setMessage(
      error.message || "Audit failed. Check the server output.",
      "error"
    );

    setNotice(
      error.message || "The audit request failed.",
      "error"
    );

  } finally {
    button.disabled = false;
    button.innerHTML = `
      <span>Analyze financial data</span>
      <span>→</span>`;
  }
});


/* =====================================
   11. SEARCH AND FILTERS
===================================== */

$("#searchFindings").addEventListener("input", renderFindings);

$("#riskFilter").addEventListener("change", renderFindings);


/* =====================================
   12. EXPORT JSON
===================================== */

$("#exportJson").addEventListener("click", () => {
  if (!latestReport) return;

  const report = {
    application: "FinIQ",
    source_file: latestFileName,
    exported_at: new Date().toISOString(),
    ...latestReport
  };

  downloadFile(
    "finiq-audit-report.json",
    JSON.stringify(report, null, 2),
    "application/json;charset=utf-8"
  );
});


/* =====================================
   13. EXPORT FINDINGS CSV
===================================== */

$("#exportCsv").addEventListener("click", () => {
  if (!latestReport) return;

  const findings = latestReport.findings || [];

  const columns = [
    "transaction_id",
    "row_number",
    "issue",
    "risk",
    "details"
  ];

  const csv = [
    columns.join(","),
    ...findings.map(f =>
      columns.map(key => csvCell(f[key])).join(",")
    )
  ].join("\r\n");

  downloadFile(
    "finiq-audit-findings.csv",
    "\uFEFF" + csv,
    "text/csv;charset=utf-8"
  );
});


/* =====================================
   14. HISTORY AND COMPARISON EVENTS
===================================== */

$("#historyRows").addEventListener("click", event => {
  const button = event.target.closest("[data-open-audit]");
  if (!button) return;

  openSavedAudit(button.dataset.openAudit);
});

$("#refreshHistory").addEventListener("click", loadHistory);

$("#compareButton").addEventListener("click", compareAudits);


/* =====================================
   15. APPLICATION INITIALIZATION
===================================== */

loadHistory();
