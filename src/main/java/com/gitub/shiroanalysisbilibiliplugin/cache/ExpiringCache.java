package com.gitub.shiroanalysisbilibiliplugin.cache;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 简单的过期缓存：保存一组字符串（url 等），在 expireSeconds 后自动移除。
 * 如果 expireSeconds <= 0，则每次 set() 都会先清空（与 Python 版本行为一致）。
 */
public class ExpiringCache {
    private final Set<String> cache = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final long expireSeconds;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public ExpiringCache(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public void set(String value) {
        if (cache.contains(value)) {
            return;
        }
        if (expireSeconds <= 0) {
            cache.clear();
        }
        cache.add(value);
        if (expireSeconds > 0) {
            scheduler.schedule(() -> {
                cache.remove(value);
            }, expireSeconds, TimeUnit.SECONDS);
        }
    }

    public boolean get(String value) {
        return cache.contains(value);
    }

    @Override
    public String toString() {
        return cache.toString();
    }
}
