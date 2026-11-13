package edu.iitp.landstack.quality;

import edu.iitp.landstack.common.PageResponse;
import edu.iitp.landstack.quality.QualityService.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quality")
@Tag(name = "Data quality", description = "Potential issues for human review - never legal determinations")
public class QualityController {
    private final QualityService service;

    public QualityController(QualityService service) {
        this.service = service;
    }

    @GetMapping("/issues")
    public PageResponse<IssueDto> list(@RequestParam(required = false) IssueStatus status,
                                       @RequestParam(required = false) Severity severity,
                                       @RequestParam(required = false) String ruleId,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String district,
                                       @RequestParam(required = false) UUID importJobId,
                                       @RequestParam(required = false) UUID landRecordId,
                                       @RequestParam(required = false) QualityIssue.Source source,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size,
                                       @RequestParam(required = false) String sort) {
        return service.list(status, severity, ruleId, category, district, importJobId, landRecordId, source, page, size, sort);
    }

    @GetMapping("/issues/{id}")
    public IssueDetailDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/issues/{id}/review")
    public IssueDto review(@PathVariable UUID id, @RequestBody ReviewRequest req) {
        return service.review(id, req);
    }

    @PostMapping("/runs")
    public RunResult rerun(@RequestBody(required = false) Map<String, UUID> body) {
        return service.rerun(body == null ? null : body.get("importJobId"));
    }

    @PostMapping("/statistical-runs")
    public StatisticalAnomalyService.RunResult statistical() {
        return service.statisticalRun();
    }

    @GetMapping("/rules")
    public Object rules() {
        return QualityRuleCatalogue.RULES;
    }
}
