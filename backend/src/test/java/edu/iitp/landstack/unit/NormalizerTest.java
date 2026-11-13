package edu.iitp.landstack.unit;

import edu.iitp.landstack.mapping.CanonicalField;
import edu.iitp.landstack.mapping.MappingConfig;
import edu.iitp.landstack.mapping.Normalizer;
import edu.iitp.landstack.mapping.RowIssue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizerTest {
    private final Normalizer normalizer = TestProps.normalizer();

    private static MappingConfig cfg(boolean dayFirst, String declaredUnit) {
        Map<CanonicalField, String> m = new EnumMap<>(CanonicalField.class);
        m.put(CanonicalField.PARCEL_ID, "ULPIN");
        m.put(CanonicalField.SURVEY_KHASRA_NO, "Khasra No");
        m.put(CanonicalField.OWNER_NAME, "Owner");
        m.put(CanonicalField.LAND_AREA_VALUE, "Area");
        m.put(CanonicalField.LAND_AREA_UNIT, "Unit");
        m.put(CanonicalField.LAND_TYPE, "Type");
        m.put(CanonicalField.REGISTRATION_DATE, "Reg Date");
        m.put(CanonicalField.VILLAGE_LOCALITY, "Village");
        m.put(CanonicalField.DISTRICT, "District");
        m.put(CanonicalField.STATE, "State");
        m.put(CanonicalField.LATITUDE, "Lat");
        m.put(CanonicalField.LONGITUDE, "Lon");
        return new MappingConfig(m, dayFirst, declaredUnit, null, true, false);
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new HashMap<>(Map.of("ULPIN", "", "Khasra No", "0012", "Owner", "Asha  Devi", "Area", "1.5",
                "Unit", "acre", "Type", "Agricultural", "Reg Date", "2021-03-04", "Village", "Rampur", "District", "Patna", "State", "Bihar"));
        m.put("Lat", "25.594095");
        m.put("Lon", "85.137566");
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static boolean has(Normalizer.Result r, String rule) {
        return r.issues().stream().map(RowIssue::ruleId).anyMatch(rule::equals);
    }

    @Test
    void preservesLeadingZerosAndOriginalIdentifierText() {
        var r = normalizer.normalize(row("ULPIN", "  00BR0012 3456 "), cfg(false, null));
        assertThat(r.record().surveyKhasraNo()).isEqualTo("0012");
        assertThat(r.record().parcelId()).isEqualTo("00BR0012 3456");
    }

    @Test
    void convertsUniversalUnitAndRetainsSourceUnit() {
        var r = normalizer.normalize(row(), cfg(false, null));
        assertThat(r.record().landAreaValue()).isEqualByComparingTo("1.5");
        assertThat(r.record().landAreaUnitRaw()).isEqualTo("acre");
        assertThat(r.record().landAreaUnit()).isEqualTo("ACRE");
        assertThat(r.record().landAreaSqm()).isEqualByComparingTo("6070.2846");
        assertThat(r.blocked()).isFalse();
    }

    @Test
    void regionalUnitConvertsOnlyWithKnownStateFactor() {
        var bihar = normalizer.normalize(row("Area", "2", "Unit", "Bigha", "State", "Bihar"), cfg(false, null));
        assertThat(bihar.record().landAreaSqm()).isEqualByComparingTo("5057.6415");
        var noState = normalizer.normalize(row("Area", "2", "Unit", "Bigha", "State", ""), cfg(false, null));
        assertThat(noState.record().landAreaSqm()).isNull();
        assertThat(has(noState, "UNIT_NOT_CONVERTIBLE")).isTrue();
        var rajasthan = normalizer.normalize(row("Area", "2", "Unit", "bigha", "State", "Rajasthan"), cfg(false, null));
        assertThat(rajasthan.record().landAreaSqm()).isNull();
        assertThat(has(rajasthan, "UNIT_NOT_CONVERTIBLE")).isTrue();
    }

    @Test
    void negativeAndNonNumericAreasBlockTheRow() {
        var neg = normalizer.normalize(row("Area", "-3"), cfg(false, null));
        assertThat(has(neg, "AREA_NEGATIVE")).isTrue();
        assertThat(neg.blocked()).isTrue();
        assertThat(neg.record().landAreaValue()).isNull();
        var text = normalizer.normalize(row("Area", "about two"), cfg(false, null));
        assertThat(has(text, "AREA_NOT_NUMERIC")).isTrue();
    }

    @Test
    void unitEmbeddedInValueAndIndianGroupingAreHandled() {
        var r = normalizer.normalize(row("Area", "1,20,000 sq ft", "Unit", ""), cfg(false, null));
        assertThat(r.record().landAreaValue()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(r.record().landAreaUnit()).isEqualTo("SQFT");
    }

    @Test
    void declaredUnitUsedOnlyWhenRowHasNone() {
        var r = normalizer.normalize(row("Unit", ""), cfg(false, "hectare"));
        assertThat(r.record().landAreaUnit()).isEqualTo("HECTARE");
        assertThat(r.traces().get(CanonicalField.LAND_AREA_UNIT).transformation()).contains("declared by operator");
    }

    @Test
    void invalidCoordinatesAreRejectedNotGuessed() {
        var out = normalizer.normalize(row("Lat", "95.2"), cfg(false, null));
        assertThat(out.record().latitude()).isNull();
        assertThat(out.record().longitude()).isNull();
        assertThat(has(out, "COORD_OUT_OF_RANGE")).isTrue();
        assertThat(has(out, "COORD_PARTIAL")).isTrue();
        var zero = normalizer.normalize(row("Lat", "0", "Lon", "0"), cfg(false, null));
        assertThat(has(zero, "COORD_ZERO")).isTrue();
        assertThat(zero.record().latitude()).isNull();
        var text = normalizer.normalize(row("Lon", "east"), cfg(false, null));
        assertThat(has(text, "COORD_INVALID")).isTrue();
        var hemi = normalizer.normalize(row("Lat", "25.5 N", "Lon", "85.1 E"), cfg(false, null));
        assertThat(hemi.record().latitude()).isEqualByComparingTo("25.5");
    }

    @Test
    void ambiguousDatesNeedDeclarationOrReview() {
        var amb = normalizer.normalize(row("Reg Date", "04/03/2021"), cfg(false, null));
        assertThat(amb.record().registrationDate()).isNull();
        assertThat(has(amb, "DATE_AMBIGUOUS")).isTrue();
        var dayFirst = normalizer.normalize(row("Reg Date", "04/03/2021"), cfg(true, null));
        assertThat(dayFirst.record().registrationDate()).isEqualTo(LocalDate.of(2021, 3, 4));
        var clear = normalizer.normalize(row("Reg Date", "25.12.2020"), cfg(false, null));
        assertThat(clear.record().registrationDate()).isEqualTo(LocalDate.of(2020, 12, 25));
    }

    @Test
    void missingOptionalValuesStayNullAndPlaceholdersAreNotInvented() {
        var r = normalizer.normalize(row("Owner", "N/A", "Type", "-", "Village", ""), cfg(false, null));
        assertThat(r.record().ownerName()).isNull();
        assertThat(r.record().landTypeRaw()).isNull();
        assertThat(r.record().villageLocality()).isNull();
        assertThat(r.traces().get(CanonicalField.OWNER_NAME).raw()).isEqualTo("N/A");
    }

    @Test
    void missingIdentifierBlocksUnderDefaultProfile() {
        var r = normalizer.normalize(row("Khasra No", ""), cfg(false, null));
        assertThat(has(r, "ID_MISSING")).isTrue();
        assertThat(r.blocked()).isTrue();
    }

    @Test
    void ownerNameWhitespaceOnlyIsNormalized() {
        var r = normalizer.normalize(row(), cfg(false, null));
        assertThat(r.record().ownerName()).isEqualTo("Asha Devi");
    }

    @Test
    void landTypeMappedToCategoryKeepingOriginalLabel() {
        var r = normalizer.normalize(row("Type", "Banjar"), cfg(false, null));
        assertThat(r.record().landTypeRaw()).isEqualTo("Banjar");
        assertThat(r.record().landTypeCategory()).isEqualTo("BARREN");
        var unknown = normalizer.normalize(row("Type", "Zzz"), cfg(false, null));
        assertThat(unknown.record().landTypeCategory()).isNull();
        assertThat(has(unknown, "LAND_TYPE_UNMAPPED")).isTrue();
    }

    @Test
    void normalizationIsDeterministic() {
        var a = normalizer.normalize(row("Area", "2.123456", "Lat", "25.1234567"), cfg(false, null));
        var b = normalizer.normalize(row("Area", "2.123456", "Lat", "25.1234567"), cfg(false, null));
        assertThat(a.record()).isEqualTo(b.record());
        assertThat(a.issues()).isEqualTo(b.issues());
        assertThat(a.record().latitude()).isEqualByComparingTo("25.123457");
    }

    @Test
    void extremeAreaIsWarningNotLegalFinding() {
        var r = normalizer.normalize(row("Area", "500000", "Unit", "hectare"), cfg(false, null));
        assertThat(has(r, "AREA_EXTREME")).isTrue();
        assertThat(r.blocked()).isFalse();
    }
}
