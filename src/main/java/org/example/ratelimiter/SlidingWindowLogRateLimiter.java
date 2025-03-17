package org.example.ratelimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class SlidingWindowLogRateLimiter implements RateLimiter{
    private static final Logger logger = LoggerFactory.getLogger(SlidingWindowLogRateLimiter.class);
    private final Map<String, QueueElement> rateLimitMap=new ConcurrentHashMap<>();

    private static class QueueElement{
        long windowSize;
        Queue<Long> queue;
        QueueElement(long windowSize,Queue<Long> queue){
            this.windowSize=windowSize;
            this.queue=queue;
        }
    }

    @Override
    public boolean isAllowed(String key, int maxLimit, int windowSize) {
        long windowSizeInMillis=windowSize*60*1000L;
        long currentTime = System.currentTimeMillis();

        QueueElement queueElement=rateLimitMap.computeIfAbsent(key,(k)->new QueueElement(windowSizeInMillis,new LinkedList<>()));
        synchronized (queueElement){
            rateLimitMap.putIfAbsent(key,queueElement);
            Queue<Long> queue=queueElement.queue;
            while(!queue.isEmpty() && queue.peek()<(currentTime-windowSizeInMillis)){
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
        logger.info("*********SlidingWindowLogRateLimiter Cleanup Process************");
        long currentTime = System.currentTimeMillis();

        rateLimitMap.forEach((key, val) -> {
            QueueElement queueElement=rateLimitMap.get(key);
            synchronized (queueElement){
                Queue<Long> queue=queueElement.queue;
                while(!queue.isEmpty() && queue.peek()<(currentTime - queueElement.windowSize)){
                    queue.poll();
                }
                if(queue.isEmpty()){
                    rateLimitMap.remove(key);
                }
            }

        });

    }
}
