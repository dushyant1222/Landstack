package edu.iitp.landstack.landrecords;

import edu.iitp.landstack.common.PageResponse;
import edu.iitp.landstack.landrecords.LandRecordDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/land-records")
@Tag(name = "Land records")
public class LandRecordController {
    private final LandRecordService service;

    public LandRecordController(LandRecordService service) {
        this.service = service;
    }

    /** Server-side search. Filter parameters: q, parcelId, surveyKhasraNo, village, tehsil, district, state, landType,
     *  importJobId, sourceFileId, status (CONFIRMED|ARCHIVED|ALL), mappable, hasOpenIssues, minLat/minLon/maxLat/maxLon. */
    @GetMapping
    public PageResponse<LandRecordListItem> search(LandRecordFilter filter,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size,
                                                   @RequestParam(required = false) String sort) {
        return service.search(filter, page, size, sort);
    }

    @GetMapping("/facets")
    public Facets facets() {
        return service.facets();
    }

    @GetMapping("/{id}")
    public LandRecordDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public LandRecordDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/archive")
    public LandRecordDetail archive(@PathVariable UUID id, @Valid @RequestBody ArchiveRequest req) {
        return service.archive(id, req.reason());
    }

    @PostMapping("/{id}/restore")
    public LandRecordDetail restore(@PathVariable UUID id) {
        return service.restore(id);
    }
}
