package org.example.ratelimiter;

public interface RateLimiter {
    boolean isAllowed(String key,int maxLimit,int windowSize);
}
