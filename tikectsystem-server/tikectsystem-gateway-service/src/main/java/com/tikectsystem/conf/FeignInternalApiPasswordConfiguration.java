package com.tikectsystem.conf;

import com.tikectsystem.util.StringUtil;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.tikectsystem.constant.Constant.API_PASSWORD;

@Configuration
public class FeignInternalApiPasswordConfiguration {

    @Value("${manage.api-password:${manage.apiPassword:}}")
    private String apiPassword;

    @Bean
    public RequestInterceptor internalApiPasswordRequestInterceptor() {
        return template -> {
            if (StringUtil.isNotEmpty(apiPassword)) {
                template.header(API_PASSWORD, apiPassword);
            }
        };
    }
}
