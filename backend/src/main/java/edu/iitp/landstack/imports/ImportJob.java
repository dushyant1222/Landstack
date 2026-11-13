package edu.iitp.landstack.imports;

import edu.iitp.landstack.common.AbstractEntity;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "import_job")
public class ImportJob extends AbstractEntity {
    @Column(name = "source_file_id", nullable = false)
    private UUID sourceFileId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status = ImportStatus.RECEIVED;

    @Column(name = "status_code")
    private String statusCode;

    @Column(name = "status_message")
    private String statusMessage;

    @Column(nullable = false)
    private boolean retryable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sheet_names", columnDefinition = "jsonb")
    private List<String> sheetNames;

    @Column(name = "selected_sheet")
    private String selectedSheet;

    @Column(name = "header_row")
    private Integer headerRow;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_columns", columnDefinition = "jsonb")
    private List<String> sourceColumns;

    @Column(name = "duplicate_of_job_id")
    private UUID duplicateOfJobId;

    @Column(name = "total_rows") private Integer totalRows;
    @Column(name = "valid_rows") private Integer validRows;
    @Column(name = "warning_rows") private Integer warningRows;
    @Column(name = "rejected_rows") private Integer rejectedRows;
    @Column(name = "imported_rows") private Integer importedRows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_summary", columnDefinition = "jsonb")
    private Map<String, Object> resultSummary;

    @Column(name = "confirmation_key", unique = true)
    private UUID confirmationKey;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    private long version;

    protected ImportJob() {}

    public ImportJob(UUID sourceFileId, UUID createdBy) {
        this.sourceFileId = sourceFileId;
        this.createdBy = createdBy;
    }

    /** The single guarded way to change state. */
    public void transitionTo(ImportStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ApiException(ErrorCode.IMPORT_STATE_CONFLICT,
                    "This import cannot move from " + status + " to " + next + ".");
        }
        this.status = next;
        if (next != ImportStatus.FAILED) {
            this.statusCode = null;
            this.statusMessage = null;
            this.retryable = false;
        }
    }

    public void fail(String code, String safeMessage, boolean retryable) {
        transitionTo(ImportStatus.FAILED);
        this.statusCode = code;
        this.statusMessage = safeMessage;
        this.retryable = retryable;
    }

    /** Sets a non-fatal note (e.g. failed confirmation attempt) while keeping the status. */
    public void note(String code, String safeMessage) {
        this.statusCode = code;
        this.statusMessage = safeMessage;
    }

    public void setCounts(int total, int valid, int warning, int rejected) {
        this.totalRows = total;
        this.validRows = valid;
        this.warningRows = warning;
        this.rejectedRows = rejected;
    }

    public void markConfirmed(UUID key, UUID by, int imported, Map<String, Object> summary) {
        this.confirmationKey = key;
        this.confirmedBy = by;
        this.confirmedAt = Instant.now();
        this.importedRows = imported;
        this.resultSummary = summary;
    }

    public UUID getSourceFileId() { return sourceFileId; }
    public UUID getCreatedBy() { return createdBy; }
    public ImportStatus getStatus() { return status; }
    public String getStatusCode() { return statusCode; }
    public String getStatusMessage() { return statusMessage; }
    public boolean isRetryable() { return retryable; }
    public List<String> getSheetNames() { return sheetNames; }
    public void setSheetNames(List<String> sheetNames) { this.sheetNames = sheetNames; }
    public String getSelectedSheet() { return selectedSheet; }
    public void setSelectedSheet(String selectedSheet) { this.selectedSheet = selectedSheet; }
    public Integer getHeaderRow() { return headerRow; }
    public void setHeaderRow(Integer headerRow) { this.headerRow = headerRow; }
    public List<String> getSourceColumns() { return sourceColumns; }
    public void setSourceColumns(List<String> sourceColumns) { this.sourceColumns = sourceColumns; }
    public UUID getDuplicateOfJobId() { return duplicateOfJobId; }
    public void setDuplicateOfJobId(UUID duplicateOfJobId) { this.duplicateOfJobId = duplicateOfJobId; }
    public Integer getTotalRows() { return totalRows; }
    public Integer getValidRows() { return validRows; }
    public Integer getWarningRows() { return warningRows; }
    public Integer getRejectedRows() { return rejectedRows; }
    public Integer getImportedRows() { return importedRows; }
    public Map<String, Object> getResultSummary() { return resultSummary; }
    public UUID getConfirmationKey() { return confirmationKey; }
    public UUID getConfirmedBy() { return confirmedBy; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public long getVersion() { return version; }
}
