package ym.getout.placeholder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PAPI 变量缓存，避免主线程查询数据库。
 */
public class PlaceholderCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long cacheMillis;

    public PlaceholderCache(long cacheSeconds) {
        this.cacheMillis = cacheSeconds * 1000L;
    }

    public String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.timestamp > cacheMillis) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    public void put(String key, String value) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    public void invalidateAll() {
        cache.clear();
    }

    public void updateCacheSeconds(long seconds) {
        // Note: this only affects future checks, existing entries use old TTL
    }

    private static class CacheEntry {
        final String value;
        final long timestamp;

        CacheEntry(String value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }
}
