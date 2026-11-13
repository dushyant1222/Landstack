package edu.iitp.landstack.security;

import edu.iitp.landstack.config.LandStackProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window throttle on failed logins, keyed separately by account and by client IP.
 * In-memory: limits apply per backend instance (documented limitation).
 */
@Component
public class LoginRateLimiter {
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;

    public LoginRateLimiter(LandStackProperties props, Clock clock) {
        this.maxAttempts = props.security().loginMaxAttempts();
        this.window = props.security().loginWindow();
        this.clock = clock;
    }

    public boolean isBlocked(String account, String ip) {
        return count("acct:" + account) >= maxAttempts || count("ip:" + ip) >= maxAttempts * 4;
    }

    public void recordFailure(String account, String ip) {
        add("acct:" + account);
        add("ip:" + ip);
    }

    public void reset(String account) {
        failures.remove("acct:" + account);
    }

    private int count(String key) {
        Deque<Instant> q = failures.get(key);
        if (q == null) return 0;
        synchronized (q) {
            prune(q);
            return q.size();
        }
    }

    private void add(String key) {
        Deque<Instant> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(clock.instant());
        }
        if (failures.size() > 50_000) failures.clear(); // memory bound under abuse
    }

    private void prune(Deque<Instant> q) {
        Instant cutoff = clock.instant().minus(window);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
    }
}
