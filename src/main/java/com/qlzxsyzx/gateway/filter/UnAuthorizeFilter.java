package com.qlzxsyzx.gateway.filter;

import com.qlzxsyzx.gateway.constant.TokenConstant;
import com.qlzxsyzx.gateway.utils.ResponseUtil;
import com.qlzxsyzx.gateway.utils.WhiteListUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class UnAuthorizeFilter implements GlobalFilter {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private WhiteListUtil whiteListUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // 获取请求路径
            String path = exchange.getRequest().getPath().toString();
            // 判断请求路径是否在白名单中,白名单不用校验token信息
            if (whiteListUtil.isWhiteList(path)) {
                return;
            }
            // 从response的header中取到SetCookie,取出access_token
            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();
            if (headers.containsKey("Set-Cookie")) {
                List<String> strings = headers.get("Set-Cookie");
                for (String string : strings) {
                    if (string.contains(TokenConstant.ACCESS_TOKEN_NAME)) {
                        // 解析出access_token
                        String[] split = string.split(";");
                        String[] accessToken = split[0].split("=");
                        // 设置redis
                        ServerHttpRequest request = exchange.getRequest();
                        HttpCookie cimUserId = request.getCookies().getFirst(TokenConstant.COOKIE_USER_ID);
                        HttpCookie cimPlatform = request.getCookies().getFirst(TokenConstant.COOKIE_PLATFORM);
                        redisTemplate.opsForValue().set("token:" + cimUserId.getValue() + ":" + cimPlatform.getValue(), accessToken[1], TokenConstant.ACCESS_TOKEN_EXPIRE, TimeUnit.SECONDS);
                    }
                }
            }
            // 401 拦截
            if (response.getRawStatusCode() == 401) {
                Mono<Void> voidMono = ResponseUtil.handleIntercept(exchange, 401, "您的登录凭证已过期，请重新登录");
                voidMono.subscribe();
            }
        }));
    }
}
