package com.qlzxsyzx.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.qlzxsyzx.gateway.utils.ResponseUtil;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.DefaultClientResponse;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;

@Deprecated
public class RefreshTokenResponseFilter implements GlobalFilter, Ordered {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 修改返回的响应体内容
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                String originalResponseContentType = exchange.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.add(HttpHeaders.CONTENT_TYPE, originalResponseContentType);
                ResponseAdapter responseAdapter = new ResponseAdapter(body, httpHeaders);
                DefaultClientResponse clientResponse = new DefaultClientResponse(responseAdapter, ExchangeStrategies.withDefaults());
                Mono<String> rawBody = clientResponse.bodyToMono(String.class).map(s -> s);
                BodyInserter<Mono<String>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(rawBody, String.class);
                CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, exchange.getResponse().getHeaders());
                return bodyInserter.insert(outputMessage, new BodyInserterContext())
                        .then(Mono.defer(() -> {
                            Flux<DataBuffer> messageBody = outputMessage.getBody();
                            Flux<DataBuffer> flux = messageBody.map(buffer -> {
                                CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer.asByteBuffer());
                                DataBufferUtils.release(buffer);
                                JsonNode jsonNode = readNode(charBuffer.toString());
                                JsonNode code = jsonNode.get("code");
                                if (code != null && code.asInt() == 402) {
                                    // refresh token成功，重新请求原url
                                    System.out.println(jsonNode);
                                }
                                return getDelegate().bufferFactory().wrap(jsonNode.toString().getBytes(StandardCharsets.UTF_8));
                            });
                            HttpHeaders headers = getDelegate().getHeaders();
                            if (!headers.containsKey(HttpHeaders.TRANSFER_ENCODING)) {
                                flux = flux.doOnNext(data -> headers.setContentLength(data.readableByteCount()));
                            }
                            return getDelegate().writeWith(flux);
                        }));
            }
        };
        // 拦截状态码为502401和5024012的响应，进行处理
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private JsonNode readNode(String in) {
        try {
            return objectMapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private class ResponseAdapter implements ClientHttpResponse {

        private final Flux<DataBuffer> flux;
        private final HttpHeaders headers;

        @SuppressWarnings("unchecked")
        private ResponseAdapter(Publisher<? extends DataBuffer> body, HttpHeaders headers) {
            this.headers = headers;
            if (body instanceof Flux) {
                flux = (Flux) body;
            } else {
                flux = ((Mono) body).flux();
            }
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return flux;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public HttpStatus getStatusCode() {
            return null;
        }

        @Override
        public int getRawStatusCode() {
            return 0;
        }

        @Override
        public MultiValueMap<String, ResponseCookie> getCookies() {
            return null;
        }
    }


    private ResponseCookie setCookie(String name, String value, int maxAge) {
        Duration duration = Duration.ofSeconds(maxAge);
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .maxAge(duration)
                .path("/")
                .httpOnly(true)
                .build();
        return cookie;
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
