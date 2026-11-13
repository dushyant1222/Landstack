package edu.iitp.landstack.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/** Builds bounded, whitelisted page requests (size <= 100; unknown sort fields rejected). */
public final class Paging {
    public static final int MAX_SIZE = 100;

    private Paging() {}

    public static Pageable of(int page, int size, String sort, Map<String, String> allowedSorts, String defaultSort) {
        if (page < 0) throw ApiException.field("page", "must be zero or greater");
        if (size < 1 || size > MAX_SIZE) throw ApiException.field("size", "must be between 1 and " + MAX_SIZE);
        String spec = (sort == null || sort.isBlank()) ? defaultSort : sort;
        String[] parts = spec.split(",");
        String property = allowedSorts.get(parts[0].trim());
        if (property == null) throw ApiException.field("sort", "unsupported sort field '" + parts[0].trim() + "'");
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, property).and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
