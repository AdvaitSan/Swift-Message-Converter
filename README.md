# SWIFT Message Converter (MT564 to ISO 20022 seev.031)

A Java-based parser and converter that transforms **SWIFT MT564 (Corporate Action Notification)** messages into XSD-compliant **ISO 20022 (seev.031.001.12)** corporate action notification messages. The project is equipped with standard-compliant tag mappings, sequential state-machine block extraction, validation against rules established by the **Securities Market Practice Group (SMPG)**, and a JUnit 5 test suite.

Additionally, a Python verification script runs the converter and compiles a visual validation report (PDF) comparing raw MT564 inputs directly with parsed ISO 20022 XML outputs.

---

## 🚀 Key Features

- **Complete Mapping**: Maps MT564 tags (e.g., `:20C::CORP`, `:23G`, `:22F::CAEV`, `:35B`, `:98A`, `:19B`, `:92A`) to structured `seev.031` XML.
- **SMPG Compliance Checks**: Enforces rules (e.g., `PROC//ENTL` validation, mandatory `PREV` references for replacement (`REPL`) messages, and warnings for preliminary payment advice `ADDB//CAPA`).
- **Sequential State Machine Parsing**: Extends Prowide APIs to support custom sequence blocks (`CAOPTN`, `CASHMVT`, `SECMVT`) with precise loop parsing.
- **Robust Validation**: Detects missing fields, malformed FIN blocks, and XSD violations, returning detailed warning and error logs.
- **JUnit 5 / AssertJ Testing**: Clean unit and integration test coverage across happy paths, SMPG rules, edge cases, and regression baselines.
- **Visual PDF Reporting**: Python integration that compiles raw-vs-processed comparison reports.

---

## 🛠️ Tech Stack & Dependencies

### Java Core Application
- **Runtime**: Java 17+
- **Build System**: Maven 3.x
- **Libraries**:
  - **Prowide Core** (`pw-swift-core:SRU2025-10.3.14`): For parsing standard MT messages.
  - **Prowide ISO 20022** (`pw-iso20022:SRU2025-10.3.9`): For compiling target MX XML models.
  - **JAXB API & Glassfish Runtime** (`2.3.1`): For XML serialization.
  - **JUnit 5 & AssertJ**: For testing.

### Python Reporting Tool
- **Libraries**: `reportlab` (for PDF generation).

---

## 📦 Getting Started

### Prerequisites
Make sure you have Java 17, Maven, and Python installed on your system.

### Build the Application
Compile the source code and build the shaded executable JAR using Maven:
```bash
mvn clean package
```
This produces the artifact `target/swift-converter-1.0-SNAPSHOT.jar`.

### Run the Java Converter
Run the converter from the command line by passing the path to a text file containing one or more SWIFT MT564 messages:
```bash
java -jar target/swift-converter-1.0-SNAPSHOT.jar <path_to_mt564_file>
```

*Example with the provided sample:*
```bash
java -jar target/swift-converter-1.0-SNAPSHOT.jar mt564_samples.txt
```

### Run Tests
Execute the JUnit test suite to verify code changes:
```bash
mvn test
```

---

## 📊 Report Generation (PDF Verification)
You can run the Python script to execute the converter against real-world SMPG test samples and generate a PDF comparison report (`MT564_Conversion_Report.pdf`):

1. **Install Python dependencies:**
   ```bash
   pip install reportlab
   ```
2. **Run the script:**
   ```bash
   python generate_report.py
   ```
3. Open the generated file `MT564_Conversion_Report.pdf` to review side-by-side MT564 input and XML outputs.

---

## 📁 Repository Structure

```
.
├── pom.xml                      # Maven project configuration
├── generate_report.py           # Python script to generate comparison PDF reports
├── mt564_samples.txt            # Test samples including happy paths & edge cases
├── smpg_real_samples.txt        # Realistic Securities Market Practice Group samples
├── src
│   ├── main
│   │   └── java
│   │       └── com
│   │           └── converter
│   │               ├── Main.java                    # Entry point CLI
│   │               ├── CorpActnNtfctnConverter.java # Core conversion logic
│   │               ├── ConversionResult.java        # Response data model (xml, errors, warnings)
│   │               ├── MT564MissingFieldException.java # Specialized validation exception
│   │               └── Reflector.java               # Helper for Prowide structure inspection
│   └── test
│       └── java
│           └── com
│               └── converter
│                   └── CorpActnNtfctnConverterTest.java # Comprehensive JUnit 5 suite
└── MT564_Conversion_Report.pdf  # Generated PDF validation report (after running Python script)
```
