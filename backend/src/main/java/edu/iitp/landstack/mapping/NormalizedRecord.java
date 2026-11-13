package edu.iitp.landstack.mapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Typed output of normalization for one row (all fields optional; nothing invented). */
public record NormalizedRecord(String parcelId, String surveyKhasraNo, String ownerName,
                               BigDecimal landAreaValue, String landAreaUnitRaw, String landAreaUnit, BigDecimal landAreaSqm,
                               String landTypeRaw, String landTypeCategory, String registrationRef, LocalDate registrationDate,
                               String villageLocality, String tehsil, String district, String state,
                               BigDecimal latitude, BigDecimal longitude, Map<String, Object> geometry) {}
