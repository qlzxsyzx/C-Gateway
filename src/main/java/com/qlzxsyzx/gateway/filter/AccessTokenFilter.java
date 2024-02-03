package com.qlzxsyzx.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.qlzxsyzx.gateway.config.WhiteListProperties;
import com.qlzxsyzx.gateway.constant.TokenConstant;
import com.qlzxsyzx.gateway.utils.ResponseUtil;
import com.qlzxsyzx.gateway.utils.WhiteListUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

public class AccessTokenFilter implements GlobalFilter {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private WhiteListUtil whiteListUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取请求路径
        String path = exchange.getRequest().getPath().toString();
        // 判断请求路径是否在白名单中,白名单不用校验token信息
        if (whiteListUtil.isWhiteList(path)) {
            return chain.filter(exchange);
        }
        MultiValueMap<String, HttpCookie> cookies = exchange.getRequest().getCookies();
        if (!cookies.containsKey(TokenConstant.COOKIE_USER_ID) || !cookies.containsKey(TokenConstant.COOKIE_PLATFORM)) {
            // 用户登录凭证缺少，返回失败信息
            return ResponseUtil.handleIntercept(exchange, 401, "您的登录凭证已过期,请重新登录");
        }
        HttpCookie cimUserId = cookies.getFirst(TokenConstant.COOKIE_USER_ID);
        HttpCookie cimPlatform = cookies.getFirst(TokenConstant.COOKIE_PLATFORM);
        // 获取请求中的access_token
        if (cookies.containsKey(TokenConstant.ACCESS_TOKEN_NAME)) {
            // access_token cookie 未过期
            HttpCookie accessToken = cookies.getFirst(TokenConstant.ACCESS_TOKEN_NAME);
            // 进行单点登录判断 判断redis中token是否相同
            String redisToken = redisTemplate.opsForValue().get("token:" + cimUserId.getValue() + ":" + cimPlatform.getValue());
            if (accessToken.getValue().equals(redisToken)) {
                // 同端访问，将token放到请求头
                exchange.getRequest().mutate().header("Authorization", "Bearer " + accessToken.getValue()).build();
                return chain.filter(exchange);
            } else {
                // 已被顶下线
                return ResponseUtil.handleIntercept(exchange, 401, "您的登录凭证已过期,请重新登录");
            }
        } else {
            // access_token cookie过期，如果没有异端登录，redis token同时过期
            Object tokenObj = redisTemplate.opsForValue().get("token:" + cimUserId.getValue() + ":" + cimPlatform.getValue());
            if (tokenObj == null) {
                // 检查是否有refresh_token
                if (cookies.containsKey(TokenConstant.REFRESH_TOKEN_NAME)) {
                    // refresh_token 未过期，让oauth2刷新token
                    return chain.filter(exchange);
                } else {
                    // refresh_token 过期，返回未登录状态码
                    return ResponseUtil.handleIntercept(exchange, 401, "您的登录凭证已过期,请重新登录");
                }
            } else {
                // 异端登录
                return ResponseUtil.handleIntercept(exchange, 401, "您的登录凭证已过期,请重新登录");
            }
        }
    }
}
