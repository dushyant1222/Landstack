package edu.iitp.landstack.imports;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/** Import job states and the only allowed transitions (see docs/import-workflow.md). */
public enum ImportStatus {
    RECEIVED, VALIDATING, PARSING, AWAITING_SHEET_SELECTION, AWAITING_MAPPING, PREVIEW_READY, CONFIRMING,
    COMPLETED, COMPLETED_WITH_WARNINGS, FAILED, EXPIRED, CANCELLED, REMOVED;

    private static final Map<ImportStatus, Set<ImportStatus>> ALLOWED = Map.ofEntries(
            entry(RECEIVED, EnumSet.of(VALIDATING, FAILED, CANCELLED)),
            entry(VALIDATING, EnumSet.of(PARSING, FAILED, CANCELLED)),
            entry(PARSING, EnumSet.of(AWAITING_SHEET_SELECTION, AWAITING_MAPPING, FAILED, CANCELLED)),
            entry(AWAITING_SHEET_SELECTION, EnumSet.of(PARSING, CANCELLED, EXPIRED)),
            entry(AWAITING_MAPPING, EnumSet.of(PARSING, PREVIEW_READY, CANCELLED, EXPIRED)),
            entry(PREVIEW_READY, EnumSet.of(PREVIEW_READY, PARSING, CONFIRMING, CANCELLED, EXPIRED)),
            entry(CONFIRMING, EnumSet.of(COMPLETED, COMPLETED_WITH_WARNINGS, PREVIEW_READY)),
            entry(COMPLETED, EnumSet.of(REMOVED)),
            entry(COMPLETED_WITH_WARNINGS, EnumSet.of(REMOVED)),
            entry(FAILED, EnumSet.of(PARSING, REMOVED)),
            entry(EXPIRED, EnumSet.of(REMOVED)),
            entry(CANCELLED, EnumSet.of(REMOVED)),
            entry(REMOVED, EnumSet.noneOf(ImportStatus.class)));

    public boolean canTransitionTo(ImportStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_WARNINGS || this == FAILED || this == EXPIRED
                || this == CANCELLED || this == REMOVED;
    }

    public boolean isConfirmed() {
        return this == COMPLETED || this == COMPLETED_WITH_WARNINGS;
    }

    /** States in which an operator may still change sheet/header/mapping/corrections. */
    public boolean isEditable() {
        return this == AWAITING_SHEET_SELECTION || this == AWAITING_MAPPING || this == PREVIEW_READY;
    }
}
