package org.example;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    Keys[] keys() default {Keys.URL,Keys.USER_IP};
    int maxRequests() default 50;
    int windowSize() default 1;//window size in minutes
    RateLimitMethod rateLimitMethod() default RateLimitMethod.FIXED_WINDOW;
}
