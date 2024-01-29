package com.qlzxsyzx.gateway.config;

import com.qlzxsyzx.gateway.filter.AccessTokenFilter;
import com.qlzxsyzx.gateway.filter.RefreshTokenResponseFilter;
import com.qlzxsyzx.gateway.filter.UnAuthorizeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfiguration {

    @Bean
    public AccessTokenFilter accessTokenFilter() {
        return new AccessTokenFilter();
    }

    @Bean
    public UnAuthorizeFilter unAuthorizeFilter() {
        return new UnAuthorizeFilter();
    }
}
