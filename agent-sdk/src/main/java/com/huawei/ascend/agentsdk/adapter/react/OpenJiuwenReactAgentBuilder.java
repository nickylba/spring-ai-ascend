package com.huawei.ascend.agentsdk.adapter.react;

import com.huawei.ascend.agentsdk.adapter.OpenJiuwenModelMapper;
import com.huawei.ascend.agentsdk.adapter.OpenJiuwenRailMapper;
import com.huawei.ascend.agentsdk.adapter.OpenJiuwenAgentSpecMapper;
import com.huawei.ascend.agentsdk.adapter.OpenJiuwenSkillMapper;
import com.huawei.ascend.agentsdk.adapter.OpenJiuwenToolMapper;
import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.huawei.ascend.agentsdk.spec.tool.ResolvedTool;
import com.huawei.ascend.agentsdk.spec.tool.ToolResolver;
import com.huawei.ascend.agentsdk.spec.tool.ToolSpec;
import com.huawei.ascend.agentsdk.spec.tool.UnsupportedToolRefException;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.List;
import java.util.Map;

public final class OpenJiuwenReactAgentBuilder {
    private final List<ToolResolver> toolResolvers;
    private final List<AgentRail> rails;
    private final OpenJiuwenToolMapper toolMapper = new OpenJiuwenToolMapper();
    private final OpenJiuwenSkillMapper skillMapper = new OpenJiuwenSkillMapper();
    private final OpenJiuwenAgentSpecMapper specMapper = new OpenJiuwenAgentSpecMapper();
    private final OpenJiuwenModelMapper modelMapper = new OpenJiuwenModelMapper();
    private final OpenJiuwenRailMapper railMapper = new OpenJiuwenRailMapper();

    public OpenJiuwenReactAgentBuilder(List<ToolResolver> toolResolvers) {
        this(toolResolvers, List.of());
    }

    public OpenJiuwenReactAgentBuilder(List<ToolResolver> toolResolvers, List<AgentRail> rails) {
        this.toolResolvers = List.copyOf(toolResolvers);
        this.rails = List.copyOf(rails);
    }

    public ReActAgent buildAgent(AgentSpec spec) {
        OpenJiuwenReactOptions options = OpenJiuwenReactOptions.from(spec.frameworkOptions(), spec.name());
        ReActAgent agent = new ReActAgent(specMapper.card(spec));
        ReActAgentConfig config = ReActAgentConfig.builder()
                .sysOperationId(options.sysOperationId())
                .build()
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", spec.promptSpec().system())))
                .configureMaxIterations(options.maxIterations())
                .configureModelClient(
                        spec.modelSpec().provider(),
                        spec.modelSpec().apiKey(),
                        spec.modelSpec().baseUrl(),
                        spec.modelSpec().name(),
                        spec.modelSpec().sslVerify(),
                        null,
                        spec.modelSpec().headers());
        config.setModelClientConfig(modelMapper.toModelClientConfig(spec.modelSpec()));
        config.setModelConfigObj(modelMapper.toModelRequestConfig(spec.modelSpec().requestSpec()));
        agent.configure(config);
        List<Tool> tools = spec.toolSpecs().stream()
                .map(this::resolveTool)
                .map(toolMapper::toTool)
                .toList();
        registerTools(agent, spec.name(), tools);
        for (String skillDirectory : skillMapper.toSkillDirectories(spec.skillSpecs())) {
            agent.registerSkill(skillDirectory);
        }
        spec.railSpecs().stream()
                .map(railMapper::toAgentRail)
                .forEach(agent::registerRail);
        rails.forEach(agent::registerRail);
        return agent;
    }

    private ResolvedTool resolveTool(ToolSpec toolSpec) {
        return toolResolvers.stream()
                .filter(resolver -> resolver.supports(toolSpec.ref().scheme()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedToolRefException(
                        "Unsupported tool ref scheme: " + toolSpec.ref().scheme()))
                .resolve(toolSpec);
    }

    private void registerTools(BaseAgent agent, String agentId, List<Tool> tools) {
        for (Tool tool : tools) {
            agent.getAbilityManager().add(tool.getCard());
            Runner.resourceMgr().addTool(tool, agentId);
        }
    }
}
