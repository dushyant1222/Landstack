package edu.iitp.landstack.geospatial;

import java.math.BigDecimal;

public final class CoordinateValidator {
    public static final BigDecimal LAT_MAX = BigDecimal.valueOf(90);
    public static final BigDecimal LON_MAX = BigDecimal.valueOf(180);

    private CoordinateValidator() {}

    public static boolean validLatitude(BigDecimal v) {
        return v != null && v.abs().compareTo(LAT_MAX) <= 0;
    }

    public static boolean validLongitude(BigDecimal v) {
        return v != null && v.abs().compareTo(LON_MAX) <= 0;
    }

    public static boolean validLatitude(double v) {
        return !Double.isNaN(v) && v >= -90 && v <= 90;
    }

    public static boolean validLongitude(double v) {
        return !Double.isNaN(v) && v >= -180 && v <= 180;
    }
}
