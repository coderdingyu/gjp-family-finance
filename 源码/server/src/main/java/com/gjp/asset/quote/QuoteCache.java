package com.gjp.asset.quote;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * In-memory cache. Default 10 minutes.
 * Listing keys can use shorter TTL on failure so captcha blips don't stick for 10 min.
 */
@Component
public class QuoteCache {

    private static final long DEFAULT_TTL_MS = 10 * 60 * 1000L;
    /** Successful hangpai / house listing. */
    public static final long LISTING_OK_TTL_MS = 30 * 60 * 1000L;
    /** Failed listing fetch — retry soon when WAF cools down. */
    public static final long LISTING_FAIL_TTL_MS = 90 * 1000L;

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public <T> T get(String key, Supplier<T> loader) {
        return get(key, loader, v -> DEFAULT_TTL_MS);
    }

    /**
     * @param ttlOf returns TTL for this value (e.g. success 30m, failure 90s)
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader, Function<T, Long> ttlOf) {
        long now = System.currentTimeMillis();
        Entry hit = map.get(key);
        if (hit != null && now - hit.at < hit.ttlMs) {
            return (T) hit.val;
        }
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            now = System.currentTimeMillis();
            hit = map.get(key);
            if (hit != null && now - hit.at < hit.ttlMs) {
                return (T) hit.val;
            }
            T val = loader.get();
            long ttl = DEFAULT_TTL_MS;
            if (ttlOf != null) {
                Long t = ttlOf.apply(val);
                if (t != null && t > 0) {
                    ttl = t;
                }
            }
            map.put(key, new Entry(val, now, ttl));
            return val;
        }
    }

    /** Success hangpai stays; captcha/empty fails expire quickly. */
    public static long listingTtl(ListingEstimate.Listing listing) {
        if (listing != null && listing.estimate != null) {
            return LISTING_OK_TTL_MS;
        }
        return LISTING_FAIL_TTL_MS;
    }

    private static final class Entry {
        final Object val;
        final long at;
        final long ttlMs;

        Entry(Object val, long at, long ttlMs) {
            this.val = val;
            this.at = at;
            this.ttlMs = ttlMs;
        }
    }
}
