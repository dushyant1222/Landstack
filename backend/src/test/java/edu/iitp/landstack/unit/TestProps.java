package edu.iitp.landstack.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.config.LandStackProperties.*;
import edu.iitp.landstack.mapping.NormalizationDictionaries;
import edu.iitp.landstack.mapping.Normalizer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/** Builds configuration objects for plain unit tests (no Spring context). */
public final class TestProps {
    private TestProps() {}

    public static LandStackProperties props() {
        return new LandStackProperties(
                new Storage(System.getProperty("java.io.tmpdir") + "/landstack-unit"),
                new Upload(1024 * 1024),
                new Parsing(100, 50, 4000, 20),
                new Processing(false, "http://localhost:8090", "", Duration.ofSeconds(1), Duration.ofSeconds(2)),
                new Security(List.of("http://localhost:5173"), 5, Duration.ofMinutes(15), false, "salt"),
                new Privacy(true),
                new Quality("rules-1.0", BigDecimal.ONE, BigDecimal.valueOf(10_000_000), new BigDecimal("0.01"), 8, BigDecimal.valueOf(3)),
                new Retention(7, 7, "0 30 2 * * *"),
                new Export(10000),
                new Bootstrap(null, null),
                new Seed(false, null));
    }

    public static NormalizationDictionaries dictionaries() {
        try {
            return new NormalizationDictionaries(new ObjectMapper());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Normalizer normalizer() {
        return new Normalizer(dictionaries(), props());
    }
}
