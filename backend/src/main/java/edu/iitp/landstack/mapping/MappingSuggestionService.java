package edu.iitp.landstack.mapping;

import edu.iitp.landstack.mapping.MappingDtos.FieldSuggestion;
import edu.iitp.landstack.mapping.MappingDtos.SuggestionStatus;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Cautious, exact-alias mapping suggestions. A suggestion is never applied automatically; ambiguous
 * matches (several columns for one field, or one column for several fields) are not suggested at all.
 */
@Component
public class MappingSuggestionService {

    public List<FieldSuggestion> suggest(List<String> sourceColumns) {
        Map<CanonicalField, List<String>> matches = new EnumMap<>(CanonicalField.class);
        Map<String, List<CanonicalField>> byColumn = new HashMap<>();
        for (String col : sourceColumns) {
            String norm = CanonicalField.normalizeHeader(col);
            for (CanonicalField f : CanonicalField.values()) {
                if (f.aliases().contains(norm) || CanonicalField.normalizeHeader(f.label()).equals(norm)
                        || CanonicalField.normalizeHeader(f.key()).equals(norm)) {
                    matches.computeIfAbsent(f, k -> new ArrayList<>()).add(col);
                    byColumn.computeIfAbsent(col, k -> new ArrayList<>()).add(f);
                }
            }
        }
        List<FieldSuggestion> out = new ArrayList<>();
        for (CanonicalField f : CanonicalField.values()) {
            List<String> cols = matches.getOrDefault(f, List.of());
            if (cols.isEmpty()) {
                out.add(new FieldSuggestion(f.key(), f.label(), f.kind(), SuggestionStatus.NONE, null, List.of()));
            } else if (cols.size() == 1 && byColumn.get(cols.getFirst()).size() == 1) {
                out.add(new FieldSuggestion(f.key(), f.label(), f.kind(), SuggestionStatus.SUGGESTED, cols.getFirst(), cols));
            } else {
                out.add(new FieldSuggestion(f.key(), f.label(), f.kind(), SuggestionStatus.AMBIGUOUS, null, cols));
            }
        }
        return out;
    }
}
