package org.example.ratelimiter;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
@Primary
public class FixedWindowRateLimiter implements RateLimiter{

    private final Map<String,Map<Long,Integer>> rateLimitMap=new ConcurrentHashMap<>();

    @Override
    public boolean isAllowed(String key, int maxLimit, int windowSize) {
         long windowSizeInMillis=windowSize*60*1000L;
         long currentTime = System.currentTimeMillis();
         long currWindow = ((System.currentTimeMillis()/windowSizeInMillis) * windowSizeInMillis)+windowSizeInMillis;
         Map<Long,Integer> windows=rateLimitMap.computeIfAbsent(key,(k)->new HashMap<>());
         synchronized (windows){
             windows.entrySet().removeIf(window -> window.getKey() < currentTime);
             rateLimitMap.putIfAbsent(key, windows);
             int count=windows.getOrDefault(currWindow,0);
             if(count<maxLimit) {
                 windows.put(currWindow, count + 1);
                 return false;
             }
         }
         return true;
    }

    @Scheduled(fixedDelay = 30*60*1000)
    public void clearOlderWindows(){
        long currentTime = System.currentTimeMillis();

        rateLimitMap.forEach((key, val) -> {
            Map<Long,Integer> windowsMap=rateLimitMap.get(key);
            synchronized (windowsMap){
                windowsMap.entrySet().removeIf(window -> window.getKey() < currentTime);
                if(windowsMap.isEmpty()){
                    rateLimitMap.remove(key);
                }
            }

        });

    }
}
