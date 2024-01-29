package com.qlzxsyzx.gateway.constant;

public class TokenConstant {
    public static final Integer ACCESS_TOKEN_EXPIRE = 3600;// 3600
    public static final Integer REFRESH_TOKEN_EXPIRE = 3600 * 24 * 7;// 7
    public static final String ACCESS_TOKEN_NAME = "cim_access_token";
    public static final String REFRESH_TOKEN_NAME = "cim_refresh_token";
    public static final String COOKIE_PLATFORM = "cim_platform";;
    public static final String COOKIE_USER_ID = "cim_user_id";
}
