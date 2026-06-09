package com.tikectsystem.config;

import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * 先解析 ShardingSphere YAML 中的 Spring 占位符，再创建数据源。
 */
@Configuration
public class UserShardingSphereDataSourceConfiguration {
    
    private static final String DEFAULT_PROFILE = "local";
    
    private final Environment environment;
    
    private final ResourceLoader resourceLoader;
    
    public UserShardingSphereDataSourceConfiguration(final Environment environment, final ResourceLoader resourceLoader) {
        this.environment = environment;
        this.resourceLoader = resourceLoader;
    }
    
    @Primary
    @Bean
    public DataSource dataSource() throws SQLException, IOException {
        String activeProfile = environment.getProperty("spring.profiles.active", DEFAULT_PROFILE);
        String defaultLocation = "classpath:shardingsphere-user-" + activeProfile + ".yaml";
        String location = environment.resolvePlaceholders(environment.getProperty("user.shardingsphere.config-location", defaultLocation));
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("ShardingSphere config not found: " + location);
        }
        String configContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        String resolvedConfigContent = environment.resolvePlaceholders(configContent);
        return YamlShardingSphereDataSourceFactory.createDataSource(resolvedConfigContent.getBytes(StandardCharsets.UTF_8));
    }
}
