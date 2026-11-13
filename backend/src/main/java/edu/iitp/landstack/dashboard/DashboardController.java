package edu.iitp.landstack.dashboard;

import edu.iitp.landstack.processing.ProcessingServiceClient;
import edu.iitp.landstack.security.Perms;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Dashboard")
public class DashboardController {
    private final DashboardService service;
    private final ProcessingServiceClient processing;

    public DashboardController(DashboardService service, ProcessingServiceClient processing) {
        this.service = service;
        this.processing = processing;
    }

    @GetMapping("/dashboard/summary")
    public DashboardService.Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(required = false) String district) {
        return service.summary(from, to, district);
    }

    /** Whether the optional PDF processing service is reachable (CSV/XLSX never depend on it). */
    @GetMapping("/system/processing-status")
    @PreAuthorize(Perms.OPERATE)
    public Map<String, Object> processingStatus() {
        return processing.health();
    }
}
