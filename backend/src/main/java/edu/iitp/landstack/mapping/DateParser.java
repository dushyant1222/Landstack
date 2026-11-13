package edu.iitp.landstack.mapping;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic date parsing (norm-1.0). Supported: yyyy-MM-dd (optionally with a time part),
 * dd-MM-yyyy, dd/MM/yyyy, dd.MM.yyyy. When both day and month are ≤ 12 and differ, the date is
 * ambiguous unless the operator declared the file day-first.
 */
public final class DateParser {
    private static final Pattern ISO = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[T ][0-9:.]+)?$");
    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})$");

    public enum Problem { NONE, INVALID, AMBIGUOUS, MONTH_FIRST, TWO_DIGIT_YEAR }

    public record Result(LocalDate date, Problem problem) {}

    private DateParser() {}

    public static Result parse(String value, boolean dayFirstDeclared) {
        Matcher m = ISO.matcher(value);
        if (m.matches()) return build(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        m = DMY.matcher(value);
        if (!m.matches()) return new Result(null, Problem.INVALID);
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        String y = m.group(3);
        if (y.length() != 4) return new Result(null, Problem.TWO_DIGIT_YEAR);
        int year = Integer.parseInt(y);
        if (a <= 12 && b > 12) return new Result(null, Problem.MONTH_FIRST);
        if (a <= 12 && b <= 12 && a != b && !dayFirstDeclared) return new Result(null, Problem.AMBIGUOUS);
        return build(year, b, a);
    }

    private static Result build(int y, int mo, int d) {
        try {
            return new Result(LocalDate.of(y, mo, d), Problem.NONE);
        } catch (DateTimeException e) {
            return new Result(null, Problem.INVALID);
        }
    }
}
