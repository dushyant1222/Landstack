package edu.iitp.landstack.dashboard;

import edu.iitp.landstack.common.Text;
import edu.iitp.landstack.security.CurrentUser;
import edu.iitp.landstack.security.Perms;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Dashboard aggregates computed by SQL over persisted data only (no cached or hard-coded figures).
 * Definitions are documented in docs/api-contract.md#dashboard.
 */
@Service
public class DashboardService {
    private final NamedParameterJdbcTemplate jdbc;

    public DashboardService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Count(String key, long count) {}

    public record DayCount(LocalDate day, long count) {}

    public record RecentImport(UUID id, String filename, String status, Integer importedRows, Instant createdAt) {}

    public record RecentReview(UUID issueId, String action, String actorName, Instant at) {}

    public record Summary(long confirmedRecords, long archivedRecords, long mappableRecords, long notMappableRecords,
                          long totalImports, List<Count> importsByStatus, long openIssues, List<Count> openIssuesBySeverity,
                          List<Count> openIssuesByCategory, List<Count> issuesByStatus, List<Count> recordsByLandType,
                          List<Count> recordsByDistrict, List<DayCount> recordsAddedPerDay, List<RecentImport> recentImports,
                          List<RecentReview> recentReviews, boolean reviewActivityVisible, LocalDate from, LocalDate to,
                          String district, Instant generatedAt) {}

    @PreAuthorize(Perms.READ)
    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to, String district) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) { LocalDate t = start; start = end; end = t; }
        if (ChronoUnit.DAYS.between(start, end) > 366) start = end.minusDays(366);
        String d = Text.trimToNull(district);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("district", d == null ? null : d.toLowerCase())
                .addValue("from", Timestamp.from(start.atStartOfDay(ZoneOffset.UTC).toInstant()))
                .addValue("to", Timestamp.from(end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        String districtClause = d == null ? "" : " and lower(r.district) = :district";

        Map<String, Object> rec = jdbc.queryForMap("""
                select count(*) filter (where r.record_status = 'CONFIRMED') as confirmed,
                       count(*) filter (where r.record_status = 'ARCHIVED') as archived,
                       count(*) filter (where r.record_status = 'CONFIRMED' and r.mappable) as mappable,
                       count(*) filter (where r.record_status = 'CONFIRMED' and not r.mappable) as not_mappable
                from land_record r where true""" + districtClause, p);

        List<Count> importsByStatus = counts("select status as k, count(*) as c from import_job group by status order by c desc", p);
        long totalImports = importsByStatus.stream().mapToLong(Count::count).sum();

        String issueJoin = " from quality_issue q left join land_record r on r.id = q.land_record_id where true"
                + (d == null ? "" : " and lower(r.district) = :district");
        List<Count> bySeverity = counts("select q.severity as k, count(*) as c" + issueJoin + " and q.status = 'OPEN' group by q.severity order by c desc", p);
        List<Count> byCategory = counts("select q.category as k, count(*) as c" + issueJoin + " and q.status = 'OPEN' group by q.category order by c desc", p);
        List<Count> byStatus = counts("select q.status as k, count(*) as c" + issueJoin + " group by q.status order by c desc", p);
        long openIssues = bySeverity.stream().mapToLong(Count::count).sum();

        List<Count> byType = counts("select coalesce(r.land_type_category, 'UNCATEGORISED') as k, count(*) as c from land_record r "
                + "where r.record_status = 'CONFIRMED'" + districtClause + " group by 1 order by c desc limit 12", p);
        List<Count> byDistrict = counts("select coalesce(r.district, '(not supplied)') as k, count(*) as c from land_record r "
                + "where r.record_status = 'CONFIRMED'" + districtClause + " group by 1 order by c desc limit 10", p);

        Map<LocalDate, Long> perDay = new TreeMap<>();
        for (LocalDate x = start; !x.isAfter(end); x = x.plusDays(1)) perDay.put(x, 0L);
        jdbc.query("select (r.created_at at time zone 'UTC')::date as day, count(*) as c from land_record r "
                + "where r.created_at >= :from and r.created_at < :to" + districtClause + " group by 1", p,
                rs -> { perDay.put(rs.getDate("day").toLocalDate(), rs.getLong("c")); });
        List<DayCount> series = perDay.entrySet().stream().map(e -> new DayCount(e.getKey(), e.getValue())).toList();

        List<RecentImport> recent = jdbc.query("""
                select j.id, f.original_filename, j.status, j.imported_rows, j.created_at
                from import_job j join source_file f on f.id = j.source_file_id
                order by j.created_at desc limit 6""", p, (rs, i) -> new RecentImport(rs.getObject("id", UUID.class),
                rs.getString("original_filename"), rs.getString("status"), (Integer) rs.getObject("imported_rows"),
                rs.getTimestamp("created_at").toInstant()));

        boolean canSeeReviews = CurrentUser.canOperate();
        List<RecentReview> reviews = !canSeeReviews ? List.of() : jdbc.query("""
                select a.target_id, a.action, u.display_name, a.occurred_at
                from audit_event a left join app_user u on u.id = a.actor_id
                where a.action like 'ISSUE\\_%' escape '\\'
                order by a.occurred_at desc limit 8""", p, (rs, i) -> new RecentReview(rs.getObject("target_id", UUID.class),
                rs.getString("action"), rs.getString("display_name"), rs.getTimestamp("occurred_at").toInstant()));

        return new Summary(num(rec.get("confirmed")), num(rec.get("archived")), num(rec.get("mappable")), num(rec.get("not_mappable")),
                totalImports, importsByStatus, openIssues, bySeverity, byCategory, byStatus, byType, byDistrict, series,
                recent, reviews, canSeeReviews, start, end, d, Instant.now());
    }

    private List<Count> counts(String sql, MapSqlParameterSource p) {
        return jdbc.query(sql, p, (rs, i) -> new Count(rs.getString("k"), rs.getLong("c")));
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
