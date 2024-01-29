package com.qlzxsyzx.gateway.utils;

import com.qlzxsyzx.gateway.config.WhiteListProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WhiteListUtil {
    @Autowired
    private WhiteListProperties whiteListProperties;

    public boolean isWhiteList(String path) {
        List<String> whiteList = whiteListProperties.getPaths();
        if (whiteList == null) {
            return false;
        }
        for (String pattern : whiteList) {
            if (path.matches(convertToRegex(pattern))) {
                return true;
            }
        }
        return false;
    }

    private String convertToRegex(String pattern) {
        return "^" + pattern.replaceAll("\\*", ".*") + "$";
    }
}
