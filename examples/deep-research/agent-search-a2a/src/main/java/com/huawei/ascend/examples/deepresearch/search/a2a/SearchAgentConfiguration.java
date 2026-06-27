package com.huawei.ascend.examples.deepresearch.search.a2a;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the YAML assembly path (selected via {@code search-agent.yaml-classpath},
 * which the {@code stub} Spring profile overrides) into a single
 * {@link SearchAgentHandler} bean. agent-runtime discovers the handler through
 * the {@link AgentRuntimeHandler} SPI and serves it on the port declared in
 * {@code application.yaml}.
 *
 * <p>Also publishes a filesystem-backed {@link SkillHubProvider} so the runtime
 * SkillHub auto-configuration activates (its condition is
 * {@code @ConditionalOnBean(SkillHubProvider.class)}). All directories under
 * {@code search-agent.skillhub.root} containing a {@code SKILL.md} file are
 * exposed as skills and installed into the ReActAgent on every execution.
 */
@Configuration(proxyBeanMethods = false)
public class SearchAgentConfiguration {

    @Bean
    Path searchAgentYamlPath(@Value("${search-agent.yaml-classpath}") String yamlClasspath) {
        return ClasspathYamlExtractor.extract(yamlClasspath);
    }

    @Bean
    AgentRuntimeHandler searchAgentHandler(Path searchAgentYamlPath) {
        return new SearchAgentHandler(searchAgentYamlPath);
    }

    @Bean
    SkillHubProvider searchAgentSkillHubProvider(
            @Value("${search-agent.skillhub.root:skills}") String skillRoot) {
        return new LocalDirectorySkillHubProvider(Path.of(skillRoot));
    }
}