package edu.iitp.landstack.geospatial;

import edu.iitp.landstack.landrecords.LandRecord;
import edu.iitp.landstack.landrecords.LandRecordFilter;
import edu.iitp.landstack.landrecords.LandRecordRepository;
import edu.iitp.landstack.security.Perms;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Map data: only records whose own source-supplied coordinates passed validation. No geocoding, no
 * centroids, no default locations. Owner names are never sent to the map.
 */
@RestController
@RequestMapping("/api/v1/map")
@Tag(name = "Map")
public class MapController {
    public static final int MAX_POINTS = 5000;

    private final LandRecordRepository records;

    public MapController(LandRecordRepository records) {
        this.records = records;
    }

    public record MapPoint(UUID id, BigDecimal lat, BigDecimal lon, String parcelId, String surveyKhasraNo, String village,
                           String district, String landTypeCategory, String landTypeRaw, Map<String, Object> geometry) {}

    public record MapResponse(long mappableCount, long notMappableCount, int returned, boolean truncated, List<MapPoint> points) {}

    @GetMapping("/points")
    @PreAuthorize(Perms.READ)
    @Transactional(readOnly = true)
    public MapResponse points(LandRecordFilter filter, @RequestParam(defaultValue = "false") boolean includeGeometry) {
        LandRecordFilter base = filter.withMappable(null);
        long notMappable = records.count(base.withoutBbox().withMappable(false).toSpec(false));
        long mappable = records.count(base.withoutBbox().withMappable(true).toSpec(false));
        Page<LandRecord> page = records.findAll(base.withMappable(true).toSpec(false), PageRequest.of(0, MAX_POINTS, Sort.by("id")));
        List<MapPoint> points = page.getContent().stream()
                .filter(r -> CoordinateValidator.validLatitude(r.getLatitude()) && CoordinateValidator.validLongitude(r.getLongitude()))
                .map(r -> new MapPoint(r.getId(), r.getLatitude(), r.getLongitude(), r.getParcelId(), r.getSurveyKhasraNo(),
                        r.getVillageLocality(), r.getDistrict(), r.getLandTypeCategory(), r.getLandTypeRaw(),
                        includeGeometry ? r.getGeometry() : null))
                .toList();
        return new MapResponse(mappable, notMappable, points.size(), page.getTotalElements() > points.size(), points);
    }
}
