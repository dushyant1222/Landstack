package edu.iitp.landstack.parsing;

/** A parse failure with a stable code and a message that is safe to show to users. */
public class ParseFailure extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public ParseFailure(String code, String safeMessage, boolean retryable) {
        super(safeMessage);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() { return code; }
    public boolean retryable() { return retryable; }
}
