package com.huawei.ascend.middleware.skill.impl;

import com.huawei.ascend.middleware.skill.spi.SkillSandbox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SkillSandboxAutoConfiguration {
    @Bean
    public SkillSandbox skillSandbox() {
        DefaultSkillSandbox sandbox = new DefaultSkillSandbox();
        sandbox.registerExecutor("calc-yield-sandbox-tool", new LocalSandboxExecutorImpl());
        return sandbox;
    }
}
