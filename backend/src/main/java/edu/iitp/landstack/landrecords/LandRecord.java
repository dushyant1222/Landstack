package edu.iitp.landstack.landrecords;

import edu.iitp.landstack.common.AbstractEntity;
import edu.iitp.landstack.mapping.NormalizedRecord;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** A confirmed, normalized land record with mandatory source lineage (source file, import job, row). */
@Entity
@Table(name = "land_record")
public class LandRecord extends AbstractEntity {
    public enum Status { CONFIRMED, ARCHIVED }

    @Column(name = "parcel_id") private String parcelId;
    @Column(name = "survey_khasra_no") private String surveyKhasraNo;
    @Column(name = "owner_name") private String ownerName;
    @Column(name = "land_area_value") private BigDecimal landAreaValue;
    @Column(name = "land_area_unit_raw") private String landAreaUnitRaw;
    @Column(name = "land_area_unit") private String landAreaUnit;
    @Column(name = "land_area_sqm") private BigDecimal landAreaSqm;
    @Column(name = "land_type_raw") private String landTypeRaw;
    @Column(name = "land_type_category") private String landTypeCategory;
    @Column(name = "registration_ref") private String registrationRef;
    @Column(name = "registration_date") private LocalDate registrationDate;
    @Column(name = "village_locality") private String villageLocality;
    private String tehsil;
    private String district;
    private String state;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> geometry;

    @Column(nullable = false)
    private boolean mappable;

    // ---- lineage: set once at confirmation, never editable ----
    @Column(name = "source_file_id", nullable = false, updatable = false) private UUID sourceFileId;
    @Column(name = "import_job_id", nullable = false, updatable = false) private UUID importJobId;
    @Column(name = "source_row_number", nullable = false, updatable = false) private int sourceRowNumber;
    @Column(name = "source_page", updatable = false) private Integer sourcePage;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_status", nullable = false)
    private Status recordStatus = Status.CONFIRMED;

    @Column(name = "archived_reason")
    private String archivedReason;

    @Version
    private long version;

    protected LandRecord() {}

    public LandRecord(NormalizedRecord n, UUID sourceFileId, UUID importJobId, int sourceRowNumber, Integer sourcePage) {
        apply(n);
        this.sourceFileId = sourceFileId;
        this.importJobId = importJobId;
        this.sourceRowNumber = sourceRowNumber;
        this.sourcePage = sourcePage;
    }

    /** Applies normalized values (used at confirmation and for operator corrections). Lineage is untouched. */
    public void apply(NormalizedRecord n) {
        this.parcelId = n.parcelId();
        this.surveyKhasraNo = n.surveyKhasraNo();
        this.ownerName = n.ownerName();
        this.landAreaValue = n.landAreaValue();
        this.landAreaUnitRaw = n.landAreaUnitRaw();
        this.landAreaUnit = n.landAreaUnit();
        this.landAreaSqm = n.landAreaSqm();
        this.landTypeRaw = n.landTypeRaw();
        this.landTypeCategory = n.landTypeCategory();
        this.registrationRef = n.registrationRef();
        this.registrationDate = n.registrationDate();
        this.villageLocality = n.villageLocality();
        this.tehsil = n.tehsil();
        this.district = n.district();
        this.state = n.state();
        this.latitude = n.latitude();
        this.longitude = n.longitude();
        if (n.geometry() != null || this.geometry == null) this.geometry = n.geometry();
        this.mappable = latitude != null && longitude != null;
    }

    public NormalizedRecord toNormalized() {
        return new NormalizedRecord(parcelId, surveyKhasraNo, ownerName, landAreaValue, landAreaUnitRaw, landAreaUnit, landAreaSqm,
                landTypeRaw, landTypeCategory, registrationRef, registrationDate, villageLocality, tehsil, district, state,
                latitude, longitude, geometry);
    }

    public void archive(String reason) { this.recordStatus = Status.ARCHIVED; this.archivedReason = reason; }
    public void restore() { this.recordStatus = Status.CONFIRMED; this.archivedReason = null; }

    public String getParcelId() { return parcelId; }
    public String getSurveyKhasraNo() { return surveyKhasraNo; }
    public String getOwnerName() { return ownerName; }
    public BigDecimal getLandAreaValue() { return landAreaValue; }
    public String getLandAreaUnitRaw() { return landAreaUnitRaw; }
    public String getLandAreaUnit() { return landAreaUnit; }
    public BigDecimal getLandAreaSqm() { return landAreaSqm; }
    public String getLandTypeRaw() { return landTypeRaw; }
    public String getLandTypeCategory() { return landTypeCategory; }
    public String getRegistrationRef() { return registrationRef; }
    public LocalDate getRegistrationDate() { return registrationDate; }
    public String getVillageLocality() { return villageLocality; }
    public String getTehsil() { return tehsil; }
    public String getDistrict() { return district; }
    public String getState() { return state; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public Map<String, Object> getGeometry() { return geometry; }
    public boolean isMappable() { return mappable; }
    public UUID getSourceFileId() { return sourceFileId; }
    public UUID getImportJobId() { return importJobId; }
    public int getSourceRowNumber() { return sourceRowNumber; }
    public Integer getSourcePage() { return sourcePage; }
    public Status getRecordStatus() { return recordStatus; }
    public String getArchivedReason() { return archivedReason; }
    public long getVersion() { return version; }
}
