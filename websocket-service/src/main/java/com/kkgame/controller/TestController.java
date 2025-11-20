package com.kkgame.controller;

import com.kkgame.service.WebSocketService;
import com.kkgame.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
public class TestController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private WebSocketService webSocketService;

    @GetMapping("/generate-token")
    public String generateToken(@RequestParam("userId") String userId) {
        String s = jwtUtil.generateToken(userId);
        log.info("generate-token: {}", s);
        return s;
    }

    @GetMapping("/generate-token1")
    public String generateToken1(@RequestParam("userId") String userId) {
        String s = jwtUtil.generateToken(userId);
        log.info("generate-token1: {}", s);
        return s;
    }

    @GetMapping("/ws-stats")
    public Map<String, Object> getWebSocketStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConnections", webSocketService.getTotalConnectionCount());
        return stats;
    }
}
