package org.example.controller;

import org.example.RateLimit;
import org.example.RateLimitMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/ping")
    @RateLimit(maxRequests = 5,rateLimitMethod = RateLimitMethod.SLIDING_WINDOW_LOG)
    public String pingServer(){
        return "Success";
    }

    @GetMapping("/ping2")
    @RateLimit(maxRequests = 5,rateLimitMethod = RateLimitMethod.FIXED_WINDOW)
    public String pingServer2(){
        return "Success";
    }
}
