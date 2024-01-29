package com.qlzxsyzx.gateway.utils;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class ResponseUtil {

    public static Mono<Void> handleIntercept(ServerWebExchange exchange, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        JSONObject result = new JSONObject();
        result.put("code", code);
        result.put("msg", message);
        DataBuffer buffer = response.bufferFactory().wrap(result.toJSONString().getBytes());
        return response.writeWith(Mono.just(buffer));
    }
}
