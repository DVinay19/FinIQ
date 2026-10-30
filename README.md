
# FinIQ — Intelligent Financial Audit & Risk Detection

FinIQ is a Java-based financial audit analysis platform that examines uploaded transaction files, identifies potential data-quality and payment irregularities, and generates evidence-backed audit reports.

## Features

- CSV and Excel (.xlsx/.xlsm) transaction uploads
- Required-column and data-quality validation
- Duplicate invoice detection by vendor and invoice reference
- Repeated-payment pattern screening when transaction dates are supplied
- Vendor transaction-frequency screening when dates are supplied
- Vendor exposure and average transaction analysis
- Unusual amount screening using IQR and modified MAD statistics
- Risk-labelled findings with row references and explanations
- MySQL persistence for audit runs and complete report data
- Reopening previously saved audit reports
- Audit-run comparison
- PDF, JSON and findings CSV exports
- Responsive browser-based audit workspace

## Technology stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Backend | Spring Boot 4 |
| Data access | Spring Data JPA / Hibernate |
| Database | MySQL 8 |
| CSV processing | Apache Commons CSV |
| Excel processing | Apache POI |
| PDF generation | Apache PDFBox |
| Frontend | HTML, CSS and JavaScript |
| Build | Maven |

## Required input columns

Every uploaded transaction file must contain:

- `transaction_id`
- `invoice_id`
- `amount`
- `vendor`
- `transaction_type`

An optional date column may be named `transaction_date`, `transaction_datetime`, or `date`.

Date-dependent payment-pattern and vendor-frequency checks are skipped when no date column exists.

## Run locally

### Prerequisites

- Java 21
- MySQL 8
- Windows PowerShell or a compatible terminal

### 1. Create the database

In MySQL:

```sql
CREATE DATABASE IF NOT EXISTS finiq_db;
```

### 2. Configure the database password

From the project root in PowerShell:

```powershell
$env:DB_PASSWORD = Read-Host "Enter your MySQL root password"
```

### 3. Run automated tests

```powershell
.\mvnw.cmd clean test
```

### 4. Start FinIQ

```powershell
.\mvnw.cmd spring-boot:run
```

### 5. Open the dashboard

Visit:

`http://localhost:8080`

Keep the application terminal open while using the dashboard.

## API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Application health |
| POST | `/api/audit` | Upload and analyze a file |
| GET | `/api/audit/history` | List saved audits |
| GET | `/api/audit/history/{id}` | Retrieve a saved report |
| GET | `/api/audit/history/{id}/pdf` | Download a saved report as PDF |
| GET | `/api/audit/compare?firstId=1&secondId=2` | Compare two saved audits |

## Docker deployment

Create a local `.env` file using `.env.example` as a starting point.

Set strong local values for:

- `MYSQL_PASSWORD`
- `MYSQL_ROOT_PASSWORD`

Then run:

```powershell
docker compose up --build
```

The dashboard will be available at `http://localhost:8080`.

Do not commit the local `.env` file.

## Project structure

```text
FinIQ/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── .gitignore
├── README.md
├── sample_transactions.csv
└── src/
    ├── main/
    │   ├── java/com/finiq/
    │   │   ├── FiniqApplication.java
    │   │   ├── AuditController.java
    │   │   ├── AuditRun.java
    │   │   ├── AuditRunRepository.java
    │   │   ├── AuditService.java
    │   │   └── PdfReportService.java
    │   └── resources/
    │       ├── application.properties
    │       └── static/
    │           ├── index.html
    │           ├── styles.css
    │           └── app.js
    └── test/
        └── java/com/finiq/
            ├── AuditServiceTest.java
            └── FiniqApplicationTests.java
```

## Important limitations

FinIQ is a portfolio and learning project, not a production-certified audit system.

Rule-based and statistical findings are risk indicators for human review, not proof of fraud. IQR and MAD screening are not a trained fraud-detection model.

The application currently does not provide authentication, role-based access, an immutable audit trail, formal retention controls, or production-grade secrets management. These are required before using the platform with confidential company financial records.

## Project purpose

- Automate selected financial transaction review tasks
- Improve visibility into potential irregularities
- Reduce repetitive manual review work
- Provide structured findings and downloadable audit reports
- Demonstrate Java, Spring Boot, SQL and full-stack development skills

## License

No license has been specified yet. Add a LICENSE file if you intend to distribute this project under an open-source license.
