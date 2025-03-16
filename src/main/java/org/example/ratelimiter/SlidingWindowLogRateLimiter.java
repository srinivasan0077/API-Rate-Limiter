package org.example.ratelimiter;

import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class SlidingWindowLogRateLimiter implements RateLimiter{
    private final Map<String, Map<Long,Queue<Long>>> rateLimitMap=new ConcurrentHashMap<>();

    @Override
    public boolean isAllowed(String key, int maxLimit, int windowSize) {
        long windowSizeInMillis=windowSize*60*1000L;
        long currentTime = System.currentTimeMillis();
        long currWindow = ((System.currentTimeMillis()/windowSizeInMillis) * windowSizeInMillis)+windowSizeInMillis;
        Map<Long,Queue<Long>> windows=rateLimitMap.computeIfAbsent(key,(k)->new HashMap<>());
        synchronized (windows){
            windows.entrySet().removeIf(window -> window.getKey() < currentTime);
            Queue<Long> queue=windows.computeIfAbsent(currWindow,(k)->new LinkedList<>());
            while(!queue.isEmpty() && queue.peek()<(currWindow-windowSizeInMillis)){
                queue.poll();
            }

            if(queue.size()==maxLimit){
                return false;
            }
            queue.add(currentTime);
        }
        return true;
    }

    @Scheduled(fixedDelay = 30*60*1000)
    public void clearOlderWindows(){
        long currentTime = System.currentTimeMillis();

        rateLimitMap.forEach((key, val) -> {
            Map<Long,Queue<Long>> windowsMap=rateLimitMap.get(key);
            synchronized (windowsMap){
                windowsMap.entrySet().removeIf(window -> window.getKey() < currentTime);
                if(windowsMap.isEmpty()){
                    rateLimitMap.remove(key);
                }
            }

        });

    }
}
