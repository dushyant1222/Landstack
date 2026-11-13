package edu.iitp.landstack.security;

/** SpEL expressions for {@code @PreAuthorize}. One place to read the permission model. */
public final class Perms {
    private Perms() {}

    public static final String ADMIN = "hasRole('ADMINISTRATOR')";
    /** Upload, parse, map, confirm, correct records, review quality issues, export. */
    public static final String OPERATE = "hasAnyRole('ADMINISTRATOR','DATA_OPERATOR')";
    /** Any signed-in role, including READ_ONLY_REVIEWER. */
    public static final String READ = "hasAnyRole('ADMINISTRATOR','DATA_OPERATOR','READ_ONLY_REVIEWER')";
}
