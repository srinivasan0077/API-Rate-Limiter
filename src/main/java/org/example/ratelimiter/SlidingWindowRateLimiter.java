package org.example.ratelimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This rate limiter works by adding the current window count and the multiplication of the percentage left in current window and previous
 * window count.This consumes less memory like fixed window and also eliminates surge in request at the boundary unlike fixed window rate limiter.
 * The only disadvantage here is it is less accurate compared to SlidingWindowLog.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class SlidingWindowRateLimiter implements RateLimiter{

    private static final Logger logger = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);
    private final Map<String,QueueElement> rateLimitMap=new ConcurrentHashMap<>();

    private static class QueueElement{
        long windowSize;
        Map<Long,Integer> windowMap;
        QueueElement(long windowSize,Map<Long,Integer> windowMap){
            this.windowSize=windowSize;
            this.windowMap=windowMap;
        }
    }

    @Override
    public boolean isAllowed(String key, int maxLimit, int windowSize) {
        long windowSizeInMillis=windowSize*60*1000L;
        long currentTime = System.currentTimeMillis();
        long currWindow = ((System.currentTimeMillis()/windowSizeInMillis) * windowSizeInMillis)+windowSizeInMillis;
        long prevWindow=currWindow-windowSizeInMillis;
        QueueElement queueElement=rateLimitMap.computeIfAbsent(key,(k)->new SlidingWindowRateLimiter.QueueElement(windowSizeInMillis,new HashMap<>()));
        synchronized (queueElement){
            rateLimitMap.putIfAbsent(key,queueElement);
            Map<Long,Integer> windows=queueElement.windowMap;
            windows.entrySet().removeIf(window -> window.getKey() < prevWindow);

            double percentageInDecimal=1-((currentTime%windowSizeInMillis)/(double)windowSizeInMillis);
            int prevWindowCount= (int) Math.round(((double)windows.getOrDefault(prevWindow,0)*percentageInDecimal));
            int count=windows.getOrDefault(currWindow,0);

            if(count+prevWindowCount>=maxLimit) {
                return false;
            }
            windows.put(currWindow, count + 1);
        }
        return true;
    }

    @Scheduled(fixedDelay = 30*60*1000)
    public void clearOlderWindows(){
        logger.info("*********SlidingWindowRateLimiter Cleanup Process************");

        rateLimitMap.forEach((key, val) -> {
            QueueElement queueElement=rateLimitMap.get(key);
            long prevWindow=(System.currentTimeMillis()/ queueElement.windowSize) * queueElement.windowSize;
            synchronized (queueElement){
                queueElement.windowMap.entrySet().removeIf(window -> window.getKey() < prevWindow);
                if(queueElement.windowMap.isEmpty()){
                    rateLimitMap.remove(key);
                }
            }

        });

    }
}
