package org.example.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalToolCallbackConfig {

    @Bean
    public ToolCallbackProvider localToolCallbackProvider() {
        return ToolCallbackProvider.from();
    }
}

