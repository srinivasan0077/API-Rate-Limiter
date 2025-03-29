package org.example;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.ratelimiter.FixedWindowRateLimiter;
import org.example.ratelimiter.SlidingWindowLogRateLimiter;
import org.example.ratelimiter.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

@Component
@Aspect
public class RateLimiterAspect {

    private static final List<String> IP_HEADERS = Arrays.asList(
            "X-Forwarded-For",    // Standard proxy header
            "X-Real-IP",          // Set by some proxies like Nginx
            "CF-Connecting-IP",   // Cloudflare-specific
            "True-Client-IP",     // Akamai/AWS ELB
            "Proxy-Client-IP",    // Legacy proxies
            "WL-Proxy-Client-IP"  // WebLogic proxies
    );
    private static final String UNKNOWN = "unknown";
    public static final String USERID ="X-USER-ID";

    @Autowired
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Autowired
    @Qualifier("slidingWindowLogRateLimiter")
    private SlidingWindowLogRateLimiter slidingWindowLogRateLimiter;

    @Autowired
    @Qualifier("slidingWindowRateLimiter")
    private SlidingWindowRateLimiter slidingWindowRateLimiter;

    private String getKey(Keys[] keys,HttpServletRequest request,Method method){
        StringBuilder stringBuilder=new StringBuilder();
        for(Keys key : keys){
            switch (key){
                case URL :
                    stringBuilder.append(getURLRegex(method));
                    break;
                case USER_IP:
                    stringBuilder.append(getClientIp(request));
                    break;
                case USER_ID:
                    stringBuilder.append(getUserId(request));
                    break;

            }
        }
        return stringBuilder.toString();
    }

    private String getUserId(HttpServletRequest request){
        String userId=null;
        HttpSession session=request.getSession();
        if(session!=null){
            userId=(String) session.getAttribute(USERID);
        }
        if (userId==null){
            userId=request.getHeader(USERID);
        }
        return userId==null?getClientIp(request):userId;
    }

    private String getClientIp(HttpServletRequest request){
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String getURLRegex(Method method){
        StringBuilder stringBuilder=new StringBuilder();
        if(method.getDeclaringClass().isAnnotationPresent(RequestMapping.class)){
            RequestMapping requestMapping=method.getDeclaringClass().getAnnotation(RequestMapping.class);
            if(requestMapping.value().length>0){
                stringBuilder.append(requestMapping.value()[0]);
            }
        }

        if(method.isAnnotationPresent(RequestMapping.class)){
            RequestMapping requestMapping=method.getAnnotation(RequestMapping.class);
            if(requestMapping.value().length>0){
                stringBuilder.append(requestMapping.value()[0]);
            }
        }else if(method.isAnnotationPresent(GetMapping.class)){
            GetMapping getMapping=method.getAnnotation(GetMapping.class);
            if(getMapping.value().length>0){
                stringBuilder.append(getMapping.value()[0]);
            }
        }else if(method.isAnnotationPresent(PostMapping.class)){
            PostMapping postMapping=method.getAnnotation(PostMapping.class);
            if(postMapping.value().length>0){
                stringBuilder.append(postMapping.value()[0]);
            }
        }

        return stringBuilder.toString();
    }

    @Around("@annotation(rateLimit)")
    public Object rateLimitRequest(ProceedingJoinPoint joinPoint,RateLimit rateLimit) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if(attributes!=null) {
            String key = getKey(rateLimit.keys(), attributes.getRequest(),((MethodSignature)joinPoint.getSignature()).getMethod());
            switch (rateLimit.rateLimitMethod()){
                case FIXED_WINDOW :
                    if(!fixedWindowRateLimiter.isAllowed(key, rateLimit.maxRequests(), rateLimit.windowSize())){
                        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
                    }
                    break;
                case SLIDING_WINDOW_LOG:
                    if(!slidingWindowLogRateLimiter.isAllowed(key, rateLimit.maxRequests(), rateLimit.windowSize())){
                        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
                    }
                    break;
                case SLIDING_WINDOW:
                    if(!slidingWindowRateLimiter.isAllowed(key, rateLimit.maxRequests(),rateLimit.windowSize())){
                        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
                    }
                    break;
            }
        }
        return joinPoint.proceed();
    }
}
