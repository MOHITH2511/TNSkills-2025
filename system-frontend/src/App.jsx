"use client"

import { useState } from "react"

// Icon Components
const UploadIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" />
  </svg>
)

const CheckIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polyline points="20 6 9 17 4 12" />
  </svg>
)

const AlertIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="12" cy="12" r="10" />
    <line x1="12" y1="8" x2="12" y2="12" />
    <line x1="12" y1="16" x2="12.01" y2="16" />
  </svg>
)

const DatabaseIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <ellipse cx="12" cy="5" rx="9" ry="3" />
    <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
    <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
  </svg>
)

function App() {
  const [file, setFile] = useState(null)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const handleUpload = async () => {
    if (!file) {
      alert("Please select a CSV file")
      return
    }

    const formData = new FormData()
    formData.append("file", file)

    try {
      setLoading(true)
      setError(null)

      const response = await fetch("http://localhost:8080/belle_croissant/validate-order-items", {
        method: "POST",
        body: formData,
      })

      const result = await response.json()
      if (!response.ok) throw new Error(result.error || "Processing failed")
      setData(result)
    } catch (err) {
      setError(err.message)
      setData(null)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h1 style={styles.title}>Belle Croissant</h1>
        <p style={styles.subtitle}>Order Data Insights</p>
      </div>

      <div style={styles.uploadSection}>
        <label style={styles.uploadButton}>
          <UploadIcon />
          <span>{file ? file.name : "Choose CSV File"}</span>
          <input type="file" accept=".csv" onChange={(e) => setFile(e.target.files[0])} style={styles.fileInput} />
        </label>

        <button
          onClick={handleUpload}
          disabled={loading}
          style={{
            ...styles.analyzeButton,
            ...(loading ? styles.buttonDisabled : {}),
          }}
        >
          {loading ? "Processing..." : "Upload & Analyze"}
        </button>
      </div>

      {error && (
        <div style={styles.errorBox}>
          <AlertIcon />
          <span>{error}</span>
        </div>
      )}

      {data && (
        <>
          <div style={styles.cardGrid}>
            <Card title="Valid Rows" value={data.validCount} color="#10b981" icon={<CheckIcon />} />
            <Card title="Invalid Rows" value={data.invalidCount} color="#f59e0b" icon={<AlertIcon />} />
            <Card title="Inserted Rows" value={data.insertedCount} color="#3b82f6" icon={<DatabaseIcon />} />
          </div>

          <div style={styles.detailsGrid}>
            <DetailCard title="Data Quality Insights">
              <DetailRow label="Future date rows" value={data.futureDateCount} />
              <DetailRow label="Invalid order ID rows" value={data.invalidOrderIdCount} />
              <DetailRow label="Parse error rows" value={data.parseErrorCount} />
            </DetailCard>

            <DetailCard title="Business Insight">
              <DetailRow label="Items with price > 500" value={data.priceGt500Count} />
            </DetailCard>

            <DetailCard title="Automated Actions">
              <ActionItem text="Cleaned CSV generated" />
              <ActionItem text="Clean data inserted into database" />
              <ActionItem text="Task reports generated" />
            </DetailCard>
          </div>
        </>
      )}
    </div>
  )
}

function Card({ title, value, color, icon }) {
  return (
    <div style={{ ...styles.card, borderLeftColor: color }}>
      <div style={{ ...styles.cardIcon, color }}>{icon}</div>
      <div style={styles.cardContent}>
        <div style={styles.cardTitle}>{title}</div>
        <div style={styles.cardValue}>{value}</div>
      </div>
    </div>
  )
}

function DetailCard({ title, children }) {
  return (
    <div style={styles.detailCard}>
      <h3 style={styles.detailTitle}>{title}</h3>
      <div style={styles.detailContent}>{children}</div>
    </div>
  )
}

function DetailRow({ label, value }) {
  return (
    <div style={styles.detailRow}>
      <span style={styles.detailLabel}>{label}</span>
      <span style={styles.detailValue}>{value}</span>
    </div>
  )
}

function ActionItem({ text }) {
  return (
    <div style={styles.actionItem}>
      <CheckIcon />
      <span>{text}</span>
    </div>
  )
}

const styles = {
  container: { maxWidth: "1200px", margin: "0 auto", padding: "32px", fontFamily: "system-ui, sans-serif" },
  header: { textAlign: "center", marginBottom: "40px" },
  title: { fontSize: "32px", fontWeight: "700", color: "#1f2937", margin: "0 0 8px 0" },
  subtitle: { fontSize: "16px", color: "#6b7280", margin: 0 },
  uploadSection: { display: "flex", gap: "16px", justifyContent: "center", marginBottom: "24px" },
  uploadButton: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    padding: "12px 24px",
    backgroundColor: "#f3f4f6",
    border: "2px dashed #d1d5db",
    borderRadius: "12px",
    cursor: "pointer",
    fontWeight: "500",
    color: "#374151",
    transition: "all 0.2s",
  },
  fileInput: { display: "none" },
  analyzeButton: {
    padding: "12px 32px",
    backgroundColor: "#3b82f6",
    color: "white",
    border: "none",
    borderRadius: "12px",
    fontWeight: "600",
    cursor: "pointer",
    transition: "all 0.2s",
  },
  buttonDisabled: { backgroundColor: "#9ca3af", cursor: "not-allowed" },
  errorBox: {
    display: "flex",
    alignItems: "center",
    gap: "12px",
    padding: "16px",
    backgroundColor: "#fee2e2",
    color: "#991b1b",
    borderRadius: "12px",
    marginBottom: "24px",
  },
  cardGrid: {
    display: "grid",
    gridTemplateColumns: "repeat(auto-fit, minmax(250px, 1fr))",
    gap: "20px",
    marginBottom: "32px",
  },
  card: {
    display: "flex",
    alignItems: "center",
    gap: "16px",
    padding: "24px",
    backgroundColor: "white",
    borderRadius: "16px",
    boxShadow: "0 1px 3px rgba(0,0,0,0.1)",
    borderLeft: "4px solid",
    transition: "transform 0.2s, box-shadow 0.2s",
  },
  cardIcon: { fontSize: "24px" },
  cardContent: { flex: 1 },
  cardTitle: { fontSize: "14px", color: "#6b7280", marginBottom: "4px" },
  cardValue: { fontSize: "32px", fontWeight: "700", color: "#1f2937" },
  detailsGrid: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: "20px" },
  detailCard: {
    padding: "24px",
    backgroundColor: "white",
    borderRadius: "16px",
    boxShadow: "0 1px 3px rgba(0,0,0,0.1)",
  },
  detailTitle: { fontSize: "18px", fontWeight: "600", color: "#1f2937", marginTop: 0, marginBottom: "16px" },
  detailContent: { display: "flex", flexDirection: "column", gap: "12px" },
  detailRow: { display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 0" },
  detailLabel: { fontSize: "14px", color: "#6b7280" },
  detailValue: { fontSize: "16px", fontWeight: "600", color: "#1f2937" },
  actionItem: { display: "flex", alignItems: "center", gap: "12px", color: "#10b981", fontSize: "14px" },
}

export default App
