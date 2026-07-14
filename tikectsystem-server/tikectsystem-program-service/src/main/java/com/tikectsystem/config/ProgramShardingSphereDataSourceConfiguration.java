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
 * 节目服务 ShardingSphere 数据源配置。
 * <p>
 * 先解析 ShardingSphere YAML 中的 Spring 占位符，再创建数据源，避免生产库密码写死在配置文件中。
 */
@Configuration
public class ProgramShardingSphereDataSourceConfiguration {

    private static final String DEFAULT_PROFILE = "local";

    private static final String REQUIRED_PLACEHOLDER_PREFIX = "${PROGRAM_";

    private final Environment environment;

    private final ResourceLoader resourceLoader;

    public ProgramShardingSphereDataSourceConfiguration(final Environment environment, final ResourceLoader resourceLoader) {
        this.environment = environment;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 创建节目服务分库分表数据源。
     *
     * @return 已完成占位符解析的 ShardingSphere 数据源
     */
    @Primary
    @Bean
    public DataSource dataSource() throws SQLException, IOException {
        String activeProfile = environment.getProperty("spring.profiles.active", DEFAULT_PROFILE);
        String defaultLocation = "classpath:shardingsphere-program-" + activeProfile + ".yaml";
        String location = environment.resolvePlaceholders(environment.getProperty("program.shardingsphere.config-location", defaultLocation));
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("ShardingSphere config not found: " + location);
        }
        String configContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        String resolvedConfigContent = environment.resolvePlaceholders(configContent);
        validateRequiredPlaceholdersResolved(resolvedConfigContent);
        return YamlShardingSphereDataSourceFactory.createDataSource(resolvedConfigContent.getBytes(StandardCharsets.UTF_8));
    }

    private void validateRequiredPlaceholdersResolved(String resolvedConfigContent) {
        if (resolvedConfigContent.contains(REQUIRED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException("Program ShardingSphere datasource environment variables are not fully configured");
        }
    }
}
