package edu.iitp.landstack.imports;

import java.util.UUID;

public final class ImportEvents {
    private ImportEvents() {}

    /** Published inside the upload/retry/sheet-selection transaction; parsing starts after commit. */
    public record ParseRequested(UUID jobId) {}
}
