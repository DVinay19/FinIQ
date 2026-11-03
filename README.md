#  FinIQ — Intelligent Financial Audit & Risk Detection

<p align="center">
  <strong>Turning Financial Transactions into Actionable Audit Insights.</strong>
  <br/>
  An intelligent, Java-based financial audit platform for identifying transaction irregularities, detecting unusual payment patterns, and generating structured audit reports.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4-brightgreen?style=for-the-badge&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/MySQL-8-blue?style=for-the-badge&logo=mysql&logoColor=white" />
  <img src="https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" />
</p>

---

##  Overview

**FinIQ** is a financial audit analysis platform designed to simplify the review of transaction records and highlight potentially suspicious financial activities.

The application allows users to upload CSV and Excel transaction files, automatically validates the data, applies statistical and rule-based screening techniques, and generates evidence-backed findings for further investigation.

Rather than relying on manual transaction inspection, FinIQ provides a centralized workspace to analyze financial data, review risk indicators, compare audit runs, and export reports.

##  Key Features

| Feature | Description |
|---|---|
|  File Processing | Upload CSV, XLSX and XLSM transaction files |
|  Data Validation | Detect missing required fields and data-quality issues |
|  Duplicate Detection | Identify duplicate invoices using vendor and invoice references |
|  Payment Analysis | Screen repeated-payment patterns when transaction dates are available |
|  Vendor Analysis | Examine transaction frequency, exposure and average amounts |
|  Statistical Screening | Identify unusual amounts using IQR and modified MAD |
|  Risk Classification | Categorize findings with explanations and transaction row references |
|  Audit History | Persist audit runs and reopen previously saved reports |
|  Audit Comparison | Compare findings across two saved audit runs |
|  Report Generation | Export PDF, JSON and findings CSV reports |
|  Web Dashboard | Access audit operations through a responsive browser interface |

## 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| Programming Language | Java 21 |
| Backend Framework | Spring Boot 4 |
| Data Access | Spring Data JPA / Hibernate |
| Database | MySQL 8 |
| CSV Processing | Apache Commons CSV |
| Excel Processing | Apache POI |
| PDF Generation | Apache PDFBox |
| Frontend | HTML, CSS, JavaScript |
| Build Tool | Maven |

## ⚙️ How FinIQ Works

```text
        Transaction File
          CSV / Excel
               |
               ▼
       File Upload & Parsing
               |
               ▼
        Data Validation
               |
               ▼
       Transaction Analysis
               |
       ┌───────┼────────┐
       ▼       ▼        ▼
    Duplicate  Payment  Vendor &
    Detection  Patterns Exposure
       |       |        |
       └───────┼────────┘
               ▼
       Statistical Screening
          IQR / MAD
               |
               ▼
        Risk Classification
               |
               ▼
        Audit Findings
               |
        ┌──────┼──────┐
        ▼      ▼      ▼
       PDF    JSON   CSV
               |
               ▼
        MySQL Audit History
```

##  Input File Requirements

Every uploaded transaction file must contain the following columns:

| Column | Required | Purpose |
|---|---|---|
| `transaction_id` | Yes | Unique transaction reference |
| `invoice_id` | Yes | Invoice identification |
| `amount` | Yes | Transaction amount |
| `vendor` | Yes | Vendor identification |
| `transaction_type` | Yes | Transaction classification |
| `transaction_date` | Optional | Date-dependent analysis |

**Supported date column names:**
- `transaction_date`
- `transaction_datetime`
- `date`

Date-dependent payment-pattern and vendor-frequency checks are skipped when no supported date column is available.

A sample transaction file is included in the repository for testing.

##  Getting Started

### Prerequisites

Make sure the following are installed:

- Java 21
- MySQL 8
- Git
- Windows PowerShell or a compatible terminal

### 1. Clone the Repository

```bash
git clone https://github.com/DVinay19/FinIQ.git
```

Navigate into the project:

```bash
cd FinIQ
```

### 2. Create the MySQL Database

Open MySQL and execute:

```sql
CREATE DATABASE IF NOT EXISTS finiq_db;
```

### 3. Configure Database Credentials

From the project root in PowerShell:

```powershell
$env:DB_PASSWORD = Read-Host "Enter your MySQL root password"
```

### 4. Run Automated Tests

```powershell
.\mvnw.cmd clean test
```

### 5. Start the Application

```powershell
.\mvnw.cmd spring-boot:run
```

### 6. Open FinIQ

Visit the following URL in your browser:

**http://localhost:8080**

Keep the application terminal running while using the dashboard.

## 🔌 API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/health` | Check application health |
| POST | `/api/audit` | Upload and analyze transactions |
| GET | `/api/audit/history` | Retrieve saved audit runs |
| GET | `/api/audit/history/{id}` | Retrieve a previously saved report |
| GET | `/api/audit/history/{id}/pdf` | Download an audit report |
| GET | `/api/audit/compare?firstId=1&secondId=2` | Compare two audit runs |

# Docker Deployment

FinIQ also supports Docker-based deployment.

### 1. Configure Environment Variables

Create a local `.env` file using `.env.example` as a reference.

Configure:

```env
MYSQL_PASSWORD=your_database_password
MYSQL_ROOT_PASSWORD=your_root_password
```

Use strong local credentials.

### 2. Build and Start

```powershell
docker compose up --build
```

Open:

```text
http://localhost:8080
```

**Important:** Never commit your local `.env` file or database credentials to GitHub.

##  Project Structure

```text
FinIQ/
│
├── pom.xml
├── mvnw
├── mvnw.cmd
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── .gitignore
├── README.md
├── sample_transactions.csv
│
└── src/
    ├── main/
    │   ├── java/com/finiq/
    │   │   ├── FiniqApplication.java
    │   │   ├── AuditController.java
    │   │   ├── AuditRun.java
    │   │   ├── AuditRunRepository.java
    │   │   ├── AuditService.java
    │   │   └── PdfReportService.java
    │   │
    │   └── resources/
    │       ├── application.properties
    │       └── static/
    │           ├── index.html
    │           ├── styles.css
    │           └── app.js
    │
    └── test/
        └── java/com/finiq/
            ├── AuditServiceTest.java
            └── FiniqApplicationTests.java
```

##  Security & Project Limitations

FinIQ is a portfolio and learning project. It is not a production-certified financial auditing system.

The current implementation has the following limitations:

- Statistical and rule-based findings are risk indicators, not proof of fraud.
- IQR and MAD are statistical screening techniques, not trained machine-learning fraud detection models.
- Authentication and role-based access control are not currently implemented.
- An immutable audit trail and formal data-retention controls are not provided.
- Production-grade secrets management is not included.

Additional security controls and validation would be necessary before handling confidential financial records.

##  Project Objectives

- Automate selected financial transaction review activities.
- Identify potential irregularities in financial records.
- Improve visibility into vendor and payment patterns.
- Reduce repetitive manual transaction screening.
- Generate structured, evidence-backed audit reports.
- Demonstrate practical Java, Spring Boot, SQL and full-stack development skills.

##  Future Enhancements

Potential improvements include:

- Machine-learning-based anomaly detection.
- Advanced vendor risk scoring.
- Interactive financial analytics and visualizations.
- Authentication and role-based access control.
- Immutable audit logging.
- Advanced audit rule configuration.
- Scheduled transaction analysis.

##  License

No open-source license has been specified yet. A license can be added if the project is intended for open-source distribution.

---

<p align="center">
  <strong>FinIQ — Making Financial Auditing More Insightful.</strong>
  <br/>
  Built with Java, Spring Boot and MySQL.
</p>

<p align="center">
  <a href="https://github.com/DVinay19/FinIQ">GitHub Repository</a>
</p>
