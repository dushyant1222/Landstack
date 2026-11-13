package edu.iitp.landstack.mapping;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Canonical LandStack fields that source columns can be mapped to. Aliases are used only to
 * <em>suggest</em> a mapping; the operator must confirm every suggestion.
 */
public enum CanonicalField {
    PARCEL_ID("parcel_id", "Parcel ID / ULPIN", Kind.IDENTIFIER,
            "parcel id", "parcelid", "parcel no", "parcel number", "ulpin", "ulpin no", "ulpin number", "bhu aadhaar",
            "bhuaadhaar", "bhu-aadhaar", "unique land parcel id", "land parcel id", "plot id", "property id"),
    SURVEY_KHASRA_NO("survey_khasra_no", "Survey / Khasra No.", Kind.IDENTIFIER,
            "khasra", "khasra no", "khasra number", "khasra no.", "survey no", "survey number", "survey no.", "sy no",
            "s no", "khasra/survey no", "survey/khasra no", "khasra survey no", "dag no", "dag number", "plot no", "gat no", "gut no"),
    OWNER_NAME("owner_name", "Owner name", Kind.TEXT,
            "owner", "owner name", "owners", "owner(s)", "khatedar", "khatedar name", "khatadar", "pattadar", "landholder",
            "land holder", "holder name", "raiyat", "bhumiswami", "name of owner"),
    LAND_AREA_VALUE("land_area_value", "Land area", Kind.DECIMAL,
            "area", "land area", "total area", "area value", "plot area", "extent", "rakba", "kshetrafal"),
    LAND_AREA_UNIT("land_area_unit", "Area unit", Kind.UNIT,
            "unit", "area unit", "units", "uom", "unit of area", "area uom"),
    LAND_TYPE("land_type", "Land type", Kind.CATEGORY,
            "land type", "landtype", "land use", "land use type", "type", "category", "land category", "land class",
            "classification", "bhumi prakar", "nature of land"),
    REGISTRATION_REF("registration_ref", "Registration ref.", Kind.IDENTIFIER,
            "registration ref", "registration no", "registration number", "reg no", "reg. no", "registry no",
            "deed no", "document no", "mutation no", "dakhil kharij no"),
    REGISTRATION_DATE("registration_date", "Registration date", Kind.DATE,
            "registration date", "reg date", "date of registration", "deed date", "mutation date", "date"),
    VILLAGE_LOCALITY("village_locality", "Village / locality", Kind.TEXT,
            "village", "village name", "locality", "mauza", "mouza", "gram", "ward", "village/locality", "revenue village"),
    TEHSIL("tehsil", "Tehsil", Kind.TEXT, "tehsil", "tahsil", "taluk", "taluka", "mandal", "circle", "block", "sub division", "subdivision", "anchal"),
    DISTRICT("district", "District", Kind.TEXT, "district", "district name", "zila", "jila", "dist"),
    STATE("state", "State", Kind.TEXT, "state", "state name", "state/ut", "rajya"),
    LATITUDE("latitude", "Latitude", Kind.LATITUDE, "latitude", "lat", "lat.", "y", "lat (wgs84)", "latitude (dd)"),
    LONGITUDE("longitude", "Longitude", Kind.LONGITUDE, "longitude", "lon", "lng", "long", "long.", "x", "lon (wgs84)", "longitude (dd)"),
    GEOMETRY("geometry", "Geometry (GeoJSON)", Kind.GEOMETRY, "geometry", "geojson", "geom", "boundary", "polygon", "shape");

    public enum Kind { IDENTIFIER, TEXT, DECIMAL, UNIT, CATEGORY, DATE, LATITUDE, LONGITUDE, GEOMETRY }

    private final String key;
    private final String label;
    private final Kind kind;
    private final List<String> aliases;

    CanonicalField(String key, String label, Kind kind, String... aliases) {
        this.key = key;
        this.label = label;
        this.kind = kind;
        this.aliases = Arrays.stream(aliases).map(CanonicalField::normalizeHeader).toList();
    }

    public String key() { return key; }
    public String label() { return label; }
    public Kind kind() { return kind; }
    public List<String> aliases() { return aliases; }

    public static Optional<CanonicalField> byKey(String key) {
        return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
    }

    /** Header comparison form: lower-case, punctuation/underscores treated as spaces, collapsed. */
    public static String normalizeHeader(String h) {
        if (h == null) return "";
        return h.toLowerCase().replaceAll("[_\\-.:#()/\\\\]+", " ").replaceAll("\\s+", " ").strip();
    }
}
