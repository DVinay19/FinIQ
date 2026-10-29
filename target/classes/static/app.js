
const $ = (selector) => document.querySelector(selector);

let latestReport = null;
let latestFileName = "";
let noticeTimer;

function escapeHTML(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;",
    '"': "&quot;", "'": "&#39;"
  })[char]);
}

function setNotice(message, type = "success") {
  const el = $("#notice");
  el.textContent = message;
  el.className = `notice ${type}`;
  el.hidden = false;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => { el.hidden = true; }, 7000);
}

function setMessage(message, type = "") {
  $("#message").textContent = message;
  $("#message").className = `form-message ${type}`.trim();
}

async function requestJSON(url, options = {}) {
  const response = await fetch(url, options);
  let body;

  try {
    body = await response.json();
  } catch {
    body = {};
  }

  if (!response.ok) {
    throw new Error(body.error || body.message ||
      `Request failed with status ${response.status}.`);
  }
  return body;
}

function setConnection(online) {
  $("#connectionDot").classList.toggle("online", online);
  $("#connectionLabel").textContent =
    online ? "Audit engine online" : "Engine unavailable";
}

async function checkHealth() {
  try {
    const data = await requestJSON("/api/health");
    setConnection(data.status === "UP");
  } catch {
    setConnection(false);
  }
}

function updateSummary(report) {
  const s = report.summary || {};
  $("#totalTransactions").textContent =
    Number(s.total_transactions || 0).toLocaleString("en-IN");
  $("#totalFindings").textContent =
    Number(s.total_findings || 0).toLocaleString("en-IN");
  $("#highRisk").textContent =
    Number(s.high_risk_findings || 0).toLocaleString("en-IN");

  const status = s.status || "COMPLETE";
  $("#auditStatus").textContent = status;
  $("#statusDescription").textContent =
    Number(s.total_findings || 0) > 0
      ? "Potential issues require review"
      : "No issues flagged by current checks";

  $("#summaryFile").textContent = latestFileName || "Uploaded file";
  $("#summaryFile").title = latestFileName;
  $("#summaryRecords").textContent = s.total_transactions ?? 0;
  $("#summaryFindings").textContent = s.total_findings ?? 0;
  $("#summaryMethod").textContent =
    report.detection_method || "Audit rules";

  $("#exportJson").disabled = false;
  $("#exportCsv").disabled = false;
}

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
  $("#chartTotal").textContent =
    `${total} finding${total === 1 ? "" : "s"}`;

  if (total === 0) {
    $("#riskChart").innerHTML =
      '<div class="empty-chart">No findings were raised by the current checks.</div>';
    return;
  }

  $("#riskChart").innerHTML = counts.map(item => {
    const percent = item.count / total * 100;
    return `
      <div class="risk-line">
        <span class="risk-name">${item.name}</span>
        <div class="risk-track" role="img"
             aria-label="${item.name}: ${item.count}">
          <div class="risk-fill ${item.cls}"
               style="width:${percent}%"></div>
        </div>
        <span class="risk-number">${item.count}</span>
      </div>`;
  }).join("");
}

function renderFindings() {
  if (!latestReport) return;

  const findings = latestReport.findings || [];
  const search = $("#searchFindings").value.trim().toLowerCase();
  const risk = $("#riskFilter").value;

  const filtered = findings.filter(f => {
    const text = [
      f.transaction_id, f.issue, f.details, f.row_number, f.risk
    ].join(" ").toLowerCase();

    return text.includes(search) &&
      (risk === "ALL" || f.risk === risk);
  });

  $("#findingsCount").textContent =
    `${filtered.length} finding${filtered.length === 1 ? "" : "s"}`;
  $("#tableStatus").textContent =
    `${filtered.length} shown · ${findings.length} total findings`;

  if (filtered.length === 0) {
    $("#findingRows").innerHTML = `
      <tr><td colspan="4" class="empty-cell">${
        findings.length
          ? "No findings match these filters."
          : "No audit flags were detected by the current checks."
      }</td></tr>`;
    return;
  }

  $("#findingRows").innerHTML = filtered.map(f => {
    const level = ["High", "Medium", "Low"].includes(f.risk)
      ? f.risk : "Medium";

    return `
      <tr>
        <td>
          <span class="row-id">${escapeHTML(f.transaction_id || "—")}</span>
          <span class="row-number">Row ${escapeHTML(f.row_number ?? "—")}</span>
        </td>
        <td><span class="issue-title">${escapeHTML(f.issue)}</span></td>
        <td><span class="risk-badge ${level.toLowerCase()}">${level}</span></td>
        <td class="evidence">${escapeHTML(f.details)}</td>
      </tr>`;
  }).join("");
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
  // Reduce formula injection when a report is opened in spreadsheet software.
  if (/^[\t\r=+\-@]/.test(text)) text = "'" + text;
  return `"${text.replace(/"/g, '""')}"`;
}

$("#auditFile").addEventListener("change", () => {
  const file = $("#auditFile").files[0];
  $("#fileLabel").textContent = file
    ? file.name : "Choose a financial data file";
  setMessage(file ? `Selected: ${file.name}` :
    "Ready to analyze your data.");
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

  const transfer = new DataTransfer();
  transfer.items.add(file);
  $("#auditFile").files = transfer.files;
  $("#fileLabel").textContent = file.name;
  setMessage(`Selected: ${file.name}`);
});

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
  button.innerHTML = "<span>Analyzing financial data…</span><span>◌</span>";
  setMessage("Uploading file to the Java audit engine…");

  const formData = new FormData();
  formData.append("file", file);

  try {
    const report = await requestJSON("/api/audit", {
      method: "POST",
      body: formData
    });

    if (!report.summary || !Array.isArray(report.findings)) {
      throw new Error("The server response did not contain audit findings.");
    }

    latestReport = report;
    latestFileName = file.name;

    updateSummary(report);
    renderRiskChart(report.findings);
    renderFindings();

    const total = report.summary.total_findings || 0;
    setMessage(
      `Analysis complete: ${report.summary.total_transactions} records checked, ${total} findings returned.`,
      "success"
    );
    setNotice(
      total
        ? `Audit complete. ${total} potential issue(s) require review.`
        : "Audit complete. No issues were flagged by the current checks."
    );

    $("#findings").scrollIntoView({ behavior: "smooth", block: "start" });
  } catch (error) {
    setMessage(error.message || "Audit failed. Check the server output.", "error");
    setNotice(error.message || "The audit request failed.", "error");
  } finally {
    button.disabled = false;
    button.innerHTML = "<span>Analyze financial data</span><span>→</span>";
  }
});

$("#searchFindings").addEventListener("input", renderFindings);
$("#riskFilter").addEventListener("change", renderFindings);

$("#newAuditButton").addEventListener("click", () => {
  $("#upload").scrollIntoView({ behavior: "smooth", block: "start" });
  $("#auditFile").click();
});

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

$("#exportCsv").addEventListener("click", () => {
  if (!latestReport) return;

  const findings = latestReport.findings || [];
  const columns = [
    "transaction_id", "row_number", "issue", "risk", "details"
  ];

  const csv = [
    columns.join(","),
    ...findings.map(f => columns.map(key => csvCell(f[key])).join(","))
  ].join("\r\n");

  downloadFile(
    "finiq-audit-findings.csv",
    "\uFEFF" + csv,
    "text/csv;charset=utf-8"
  );
});

checkHealth();
