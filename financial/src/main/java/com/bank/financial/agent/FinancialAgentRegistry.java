package com.bank.financial.agent;

import com.bank.financial.agent.FinancialAdvisorAgentConfiguration.FinancialAdvisorAgentHandler;
import com.bank.financial.kit.AbstractFinancialAgentHandler;
import com.bank.financial.kit.DeclarativeFinancialAgentHandler;
import com.bank.financial.kit.ModelConnection;
import com.bank.financial.kit.spec.AgentDefinition;
import com.bank.financial.kit.spec.AgentDefinitionLoader;
import com.bank.financial.templates.AmlScreeningAgent;
import com.bank.financial.templates.CreditCardServicingAgent;
import com.bank.financial.templates.DepositAdvisorAgent;
import com.bank.financial.templates.LoanIntakeAgent;
import com.bank.financial.templates.PrivateBankingRmCopilotAgent;
import com.bank.financial.templates.RetailWealthAdvisorAgent;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Single source of truth mapping an agent id to a handler — used by both the
 * served runtime ({@code FinancialAgentServerConfiguration}) and the playground.
 *
 * <p>The platform hosts exactly one agent per runtime instance, so serving picks
 * ONE id; the playground can build any of these on demand. Java agents are
 * registered here (one line each); a YAML file under {@code agents/} is resolved
 * by id as a fallback.
 */
public final class FinancialAgentRegistry {

    private static final Map<String, Supplier<AbstractFinancialAgentHandler>> JAVA = new LinkedHashMap<>();

    static {
        JAVA.put("financial-advisor-agent",
                () -> new FinancialAdvisorAgentHandler(ModelConnection.fromEnv()));
        JAVA.put(CreditCardServicingAgent.ID, () -> new CreditCardServicingAgent(ModelConnection.fromEnv()));
        JAVA.put(LoanIntakeAgent.ID, () -> new LoanIntakeAgent(ModelConnection.fromEnv()));
        JAVA.put(AmlScreeningAgent.ID, () -> new AmlScreeningAgent(ModelConnection.fromEnv()));
        JAVA.put(RetailWealthAdvisorAgent.ID, RetailWealthAdvisorAgent::create);
        JAVA.put(PrivateBankingRmCopilotAgent.ID, PrivateBankingRmCopilotAgent::create);
        JAVA.put(DepositAdvisorAgent.ID, DepositAdvisorAgent::create);
    }

    private FinancialAgentRegistry() {
    }

    /** Build the handler for an id (Java agent, else a classpath {@code agents/<id>.yaml}). */
    public static AbstractFinancialAgentHandler create(String id) {
        Supplier<AbstractFinancialAgentHandler> java = JAVA.get(id);
        if (java != null) {
            return java.get();
        }
        AgentDefinition def = loadYaml(id);
        if (def != null) {
            return new DeclarativeFinancialAgentHandler(def);
        }
        throw new IllegalArgumentException("unknown financial agent: '" + id + "'  (known: " + javaIds() + ")");
    }

    public static Set<String> javaIds() {
        return new LinkedHashSet<>(JAVA.keySet());
    }

    public static boolean has(String id) {
        return JAVA.containsKey(id) || loadYaml(id) != null;
    }

    static AgentDefinition loadYaml(String id) {
        if (id == null) {
            return null;
        }
        String cp = "agents/" + (id.endsWith(".yaml") ? id : id + ".yaml");
        InputStream in = FinancialAgentRegistry.class.getClassLoader().getResourceAsStream(cp);
        return in == null ? null : AgentDefinitionLoader.loadStream(in);
    }
}
