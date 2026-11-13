package edu.iitp.landstack.exports;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.landrecords.LandRecord;
import edu.iitp.landstack.landrecords.LandRecordFilter;
import edu.iitp.landstack.landrecords.LandRecordRepository;
import edu.iitp.landstack.security.Perms;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ExportService {
    static final String[] HEADER = {"record_id", "parcel_id", "survey_khasra_no", "owner_name", "land_area_value",
            "land_area_unit_source", "land_area_unit", "land_area_sqm", "land_type_source", "land_type_category",
            "registration_ref", "registration_date", "village_locality", "tehsil", "district", "state", "latitude", "longitude",
            "mappable", "record_status", "import_job_id", "source_file_id", "source_row_number", "confirmed_at_utc"};

    private final LandRecordRepository records;
    private final AuditService audit;
    private final int maxRows;

    public ExportService(LandRecordRepository records, AuditService audit, LandStackProperties props) {
        this.records = records;
        this.audit = audit;
        this.maxRows = props.export().maxRows();
    }

    /** Builds the CSV (bounded by landstack.export.max-rows) for the same filter as the record search. */
    @PreAuthorize(Perms.OPERATE)
    @Transactional(readOnly = true)
    public byte[] landRecordsCsv(LandRecordFilter filter) {
        var spec = filter.toSpec(true);
        long total = records.count(spec);
        if (total > maxRows) {
            throw new ApiException(ErrorCode.EXPORT_TOO_LARGE, "The filter matches " + total + " records; exports are limited to "
                    + maxRows + ". Narrow the filters and try again.");
        }
        List<LandRecord> list = total == 0 ? List.of() : records.findAll(spec, PageRequest.of(0, maxRows, Sort.by("createdAt", "id"))).getContent();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // UTF-8 BOM for spreadsheet compatibility
            try (CSVPrinter p = new CSVPrinter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder().setQuoteMode(QuoteMode.ALL).setRecordSeparator("\r\n").build())) {
                p.printRecord((Object[]) HEADER);
                for (LandRecord r : list) {
                    p.printRecord(row(r).stream().map(CsvSafe::cell).toList());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("CSV generation failed", e);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rows", list.size());
        summary.put("filters", activeFilters(filter));
        audit.record("EXPORT_CREATED", "LAND_RECORD", null, summary);
        return out.toByteArray();
    }

    static List<String> row(LandRecord r) {
        return Arrays.asList(r.getId().toString(), r.getParcelId(), r.getSurveyKhasraNo(), r.getOwnerName(), plain(r.getLandAreaValue()),
                r.getLandAreaUnitRaw(), r.getLandAreaUnit(), plain(r.getLandAreaSqm()), r.getLandTypeRaw(), r.getLandTypeCategory(),
                r.getRegistrationRef(), r.getRegistrationDate() == null ? null : r.getRegistrationDate().toString(),
                r.getVillageLocality(), r.getTehsil(), r.getDistrict(), r.getState(), plain(r.getLatitude()), plain(r.getLongitude()),
                String.valueOf(r.isMappable()), r.getRecordStatus().name(), r.getImportJobId().toString(), r.getSourceFileId().toString(),
                String.valueOf(r.getSourceRowNumber()), r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
    }

    private static String plain(BigDecimal d) {
        return d == null ? null : d.toPlainString();
    }

    private static String activeFilters(LandRecordFilter f) {
        List<String> keys = new ArrayList<>();
        if (f.q() != null && !f.q().isBlank()) keys.add("q");
        if (f.parcelId() != null) keys.add("parcelId");
        if (f.surveyKhasraNo() != null) keys.add("surveyKhasraNo");
        if (f.village() != null) keys.add("village");
        if (f.tehsil() != null) keys.add("tehsil");
        if (f.district() != null) keys.add("district");
        if (f.state() != null) keys.add("state");
        if (f.landType() != null) keys.add("landType");
        if (f.importJobId() != null) keys.add("importJobId");
        if (f.sourceFileId() != null) keys.add("sourceFileId");
        if (f.status() != null) keys.add("status=" + f.status());
        if (f.mappable() != null) keys.add("mappable=" + f.mappable());
        if (f.hasOpenIssues() != null) keys.add("hasOpenIssues=" + f.hasOpenIssues());
        return String.join(",", keys);
    }
}
