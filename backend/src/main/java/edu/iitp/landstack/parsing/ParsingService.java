package edu.iitp.landstack.parsing;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.files.DetectedType;
import edu.iitp.landstack.files.FileStorageService;
import edu.iitp.landstack.files.SourceFile;
import edu.iitp.landstack.files.SourceFileRepository;
import edu.iitp.landstack.imports.ImportEvents;
import edu.iitp.landstack.imports.ImportJob;
import edu.iitp.landstack.imports.ImportJobRepository;
import edu.iitp.landstack.imports.ImportStatus;
import edu.iitp.landstack.mapping.ImportMappingRepository;
import edu.iitp.landstack.processing.PdfExtraction;
import edu.iitp.landstack.processing.PdfTabularMapper;
import edu.iitp.landstack.processing.ProcessingServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.*;

/**
 * Runs parsing/extraction in the background after the upload (or retry/sheet selection) commits.
 * Failures are isolated to the job: they set FAILED with a safe, actionable message.
 */
@Service
public class ParsingService {
    private static final Logger log = LoggerFactory.getLogger(ParsingService.class);
    public static final List<String> PARSE_STAGES = List.of("PARSE", "EXTRACT");

    private final ImportJobRepository jobs;
    private final SourceFileRepository files;
    private final FileStorageService storage;
    private final ProcessingAttemptRepository attempts;
    private final ImportRowRepository rows;
    private final ImportMappingRepository mappings;
    private final CsvParser csv;
    private final XlsxParser xlsx;
    private final ProcessingServiceClient processing;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final TabularBuilder.Limits limits;
    private final int maxPdfPages;

    public ParsingService(ImportJobRepository jobs, SourceFileRepository files, FileStorageService storage,
                          ProcessingAttemptRepository attempts, ImportRowRepository rows, ImportMappingRepository mappings,
                          CsvParser csv, XlsxParser xlsx, ProcessingServiceClient processing, AuditService audit,
                          PlatformTransactionManager txm, LandStackProperties props) {
        this.jobs = jobs;
        this.files = files;
        this.storage = storage;
        this.attempts = attempts;
        this.rows = rows;
        this.mappings = mappings;
        this.csv = csv;
        this.xlsx = xlsx;
        this.processing = processing;
        this.audit = audit;
        this.tx = new TransactionTemplate(txm);
        var p = props.parsing();
        this.limits = new TabularBuilder.Limits(p.maxRows(), p.maxColumns(), p.maxCellLength());
        this.maxPdfPages = p.maxPdfPages();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onParseRequested(ImportEvents.ParseRequested event) {
        run(event.jobId());
    }

    private record Start(UUID attemptId, DetectedType type, String storageKey, String sheet, Integer headerRow) {}

    /** Synchronous entry point (also used by tests). */
    public void run(UUID jobId) {
        Start start = tx.execute(s -> {
            ImportJob job = jobs.findById(jobId).orElse(null);
            if (job == null || job.getStatus() != ImportStatus.PARSING) return null;
            SourceFile sf = files.findById(job.getSourceFileId()).orElseThrow();
            String processor = switch (sf.getDetectedType()) {
                case CSV -> "JAVA_CSV";
                case XLSX -> "JAVA_XLSX";
                case PDF -> "PY_PDF";
            };
            String version = switch (sf.getDetectedType()) {
                case CSV -> CsvParser.VERSION;
                case XLSX -> XlsxParser.VERSION;
                case PDF -> null;
            };
            ProcessingAttempt a = attempts.save(new ProcessingAttempt(jobId,
                    sf.getDetectedType() == DetectedType.PDF ? "EXTRACT" : "PARSE", processor, version));
            return new Start(a.getId(), sf.getDetectedType(), sf.getStorageKey(), job.getSelectedSheet(), job.getHeaderRow());
        });
        if (start == null) return;

        try {
            Path path = storage.resolve(start.storageKey());
            List<String> sheetNames = null;
            String sheet = start.sheet();
            String processorVersion = null;
            TabularResult result;
            switch (start.type()) {
                case CSV -> result = csv.parse(path, start.headerRow(), limits);
                case XLSX -> {
                    sheetNames = xlsx.sheetNames(path);
                    if (sheet == null && sheetNames.size() > 1) {
                        awaitSheet(jobId, start.attemptId(), sheetNames);
                        return;
                    }
                    if (sheet == null) sheet = sheetNames.getFirst();
                    result = xlsx.parse(path, sheet, start.headerRow(), limits);
                }
                case PDF -> {
                    PdfExtraction x = processing.extractPdf(path, maxPdfPages);
                    processorVersion = x.processor();
                    result = PdfTabularMapper.toTabular(x, limits);
                }
                default -> throw new IllegalStateException();
            }
            stage(jobId, start, result, sheetNames, sheet, processorVersion);
        } catch (ParseFailure pf) {
            fail(jobId, start.attemptId(), pf);
        } catch (Exception e) {
            log.error("Unexpected parser error for import {}: {}", jobId, e.getClass().getSimpleName());
            fail(jobId, start.attemptId(), new ParseFailure("PARSER_ERROR",
                    "An unexpected error occurred while reading the file. You can retry; if it persists, check the file format.", true));
        }
    }

    private void awaitSheet(UUID jobId, UUID attemptId, List<String> sheetNames) {
        tx.executeWithoutResult(s -> {
            ImportJob job = jobs.findByIdForUpdate(jobId).orElseThrow();
            ProcessingAttempt a = attempts.findById(attemptId).orElseThrow();
            if (job.getStatus() != ImportStatus.PARSING) {
                a.finish(ProcessingAttempt.Outcome.FAILED, Map.of("superseded", true));
                return;
            }
            job.setSheetNames(sheetNames);
            job.transitionTo(ImportStatus.AWAITING_SHEET_SELECTION);
            a.finish(ProcessingAttempt.Outcome.SUCCEEDED, Map.of("messages", List.of(Diagnostic.info("SHEETS",
                    sheetNames.size() + " worksheets found; select one to continue.").toMap()), "sheetNames", sheetNames));
            audit.recordAs(null, "SYSTEM", "IMPORT_AWAITING_SHEET", "IMPORT_JOB", jobId, Map.of("sheetCount", sheetNames.size()));
        });
    }

    private void stage(UUID jobId, Start start, TabularResult result, List<String> sheetNames, String sheet, String processorVersion) {
        tx.executeWithoutResult(s -> {
            ImportJob job = jobs.findByIdForUpdate(jobId).orElseThrow();
            ProcessingAttempt a = attempts.findById(start.attemptId()).orElseThrow();
            boolean superseded = job.getStatus() != ImportStatus.PARSING
                    || !Objects.equals(job.getSelectedSheet(), start.sheet())
                    || !Objects.equals(job.getHeaderRow(), start.headerRow());
            if (superseded) {
                a.finish(ProcessingAttempt.Outcome.FAILED, Map.of("superseded", true));
                return;
            }
            List<ImportRow> staged = new ArrayList<>(result.rows().size());
            for (TabularResult.Row r : result.rows()) {
                staged.add(new ImportRow(jobId, a.getId(), r.rowNumber(), r.values(), r.page(), r.method(), r.confidence()));
            }
            rows.saveAll(staged);
            rows.flush();
            rows.deleteSuperseded(jobId, a.getId());
            mappings.deleteById(jobId);
            if (sheetNames != null) job.setSheetNames(sheetNames);
            if (sheet != null) job.setSelectedSheet(sheet);
            job.setSourceColumns(result.headers());
            job.setCounts(staged.size(), 0, 0, 0);
            job.transitionTo(ImportStatus.AWAITING_MAPPING);
            if (processorVersion != null) a.setProcessorVersion(processorVersion);
            a.finish(ProcessingAttempt.Outcome.SUCCEEDED, diagnostics(result));
            audit.recordAs(null, "SYSTEM", "IMPORT_PARSED", "IMPORT_JOB", jobId,
                    Map.of("rows", staged.size(), "columns", result.headers().size(), "headerRow", result.headerRow()));
        });
    }

    private void fail(UUID jobId, UUID attemptId, ParseFailure pf) {
        tx.executeWithoutResult(s -> {
            ImportJob job = jobs.findByIdForUpdate(jobId).orElseThrow();
            ProcessingAttempt a = attempts.findById(attemptId).orElseThrow();
            ProcessingAttempt.Outcome outcome = switch (pf.code()) {
                case ProcessingServiceClient.UNAVAILABLE -> ProcessingAttempt.Outcome.UNAVAILABLE;
                case "PROCESSING_TIMEOUT" -> ProcessingAttempt.Outcome.TIMED_OUT;
                default -> ProcessingAttempt.Outcome.FAILED;
            };
            a.finish(outcome, Map.of("messages", List.of(new Diagnostic(Diagnostic.Level.ERROR, pf.code(), pf.getMessage(), null, null).toMap())));
            if (job.getStatus() == ImportStatus.PARSING) {
                rows.deleteByImportJobId(jobId);
                job.fail(pf.code(), pf.getMessage(), pf.retryable());
                audit.recordAs(null, "SYSTEM", "IMPORT_PARSE_FAILED", "IMPORT_JOB", jobId,
                        Map.of("code", pf.code(), "retryable", pf.retryable()));
            }
        });
    }

    private static Map<String, Object> diagnostics(TabularResult r) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("messages", r.diagnostics().stream().map(Diagnostic::toMap).toList());
        d.put("headerRow", r.headerRow());
        d.put("headerCertain", r.headerCertain());
        d.put("columns", r.headers());
        if (r.extra() != null) d.putAll(r.extra());
        return d;
    }
}
