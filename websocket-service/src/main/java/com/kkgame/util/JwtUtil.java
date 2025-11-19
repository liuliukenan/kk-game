package com.kkgame.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT工具类，用于生成和验证JWT令牌
 * JWT (JSON Web Token) 是一种开放标准(RFC 7519)，用于在各方之间安全地传输声明
 */
@Component
public class JwtUtil {

    // 用于签名JWT令牌的密钥，从配置文件中读取
    @Value("${jwt.secret:websocket_service_secret_key}")
    private String SECRET_KEY;

    /**
     * 从JWT令牌中提取用户名
     * @param token JWT令牌
     * @return 用户名
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 从JWT令牌中提取用户ID
     * @param token JWT令牌
     * @return 用户ID
     */
    public String extractUserId(String token) {
        return extractClaim(token, claims -> {
            Object userIdObj = claims.get("userId");
            if (userIdObj != null) {
                return userIdObj.toString();
            }
            throw new IllegalArgumentException("无法解析用户ID: " + userIdObj);
        });
    }

    /**
     * 从JWT令牌中提取过期时间
     * @param token JWT令牌
     * @return 过期时间
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * 从JWT令牌中提取指定声明
     * @param token JWT令牌
     * @param claimsResolver 声明解析器函数
     * @param <T> 声明类型
     * @return 声明值
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 解析JWT令牌并提取所有声明
     * @param token JWT令牌
     * @return 所有声明
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            // 即使令牌过期，也返回声明，以便可以获取用户信息
            return e.getClaims();
        }
    }

    /**
     * 检查JWT令牌是否已过期
     * @param token JWT令牌
     * @return 如果已过期返回true，否则返回false
     */
    private Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException e) {
            // 如果解析时抛出过期异常，则令牌已过期
            return true;
        }
    }

    /**
     * 为指定用户ID生成JWT令牌
     * @param userId 用户ID
     * @return JWT令牌
     */
    public String generateToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId); // 使用String存储避免精度问题
        return createToken(claims);
    }

    /**
     * 创建JWT令牌
     *
     * @param claims 声明
     * @return JWT令牌
     */
    private String createToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject("websocket-user")
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 30)) // 缩短为30分钟过期
                .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
                .compact();
    }

    /**
     * 验证JWT令牌是否有效
     * @param token JWT令牌
     * @param userId 用户ID
     * @return 如果令牌有效返回true，否则返回false
     */
    public Boolean validateToken(String token, String userId) {
        try {
            String extractedUserId = extractUserId(token);
            return extractedUserId.equals(userId) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

}
