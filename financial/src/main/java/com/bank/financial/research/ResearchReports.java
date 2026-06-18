package com.bank.financial.research;

import com.bank.financial.kit.ModelConnection;
import com.bank.financial.research.bond.BondReportEngine;
import com.bank.financial.research.data.stub.StubBondDataSource;
import com.bank.financial.research.data.stub.StubFundDataSource;
import com.bank.financial.research.data.stub.StubThematicDataSource;
import com.bank.financial.research.fund.FundReportEngine;
import com.bank.financial.research.model.OpenJiuwenReportModel;
import com.bank.financial.research.model.ReportModel;
import com.bank.financial.research.model.RetryReportModel;
import com.bank.financial.research.model.ScriptedReportModel;
import com.bank.financial.research.model.TimeoutReportModel;
import com.bank.financial.research.thematic.ThematicReportEngine;
import com.huawei.ascend.a2a.memory.obs.CompositeMemoryObserver;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.obs.MicrometerMemoryObserver;
import com.huawei.ascend.a2a.memory.obs.Slf4jMemoryObserver;
import java.time.Duration;

/**
 * Wiring factory for the research-report engines. The bank's product set is
 * fund / FOF, fixed income (bonds), and sector-strategy (thematic) — there is no
 * single-stock equity coverage. Offline presets use stub data + the scripted
 * model (deterministic, no network, no key); the live model is env-driven
 * (BANK_LLM_*) and enabled per call.
 */
public final class ResearchReports {

    private ResearchReports() {
    }

    // ── Thematic / sector-strategy engine ─────────────────────────────────────

    /** Fully offline thematic engine (scenario stub + scripted model). */
    public static ThematicReportEngine thematicOffline(long asOfEpochMs) {
        return new ThematicReportEngine(
                new StubThematicDataSource(asOfEpochMs), new ScriptedReportModel(), null, MemoryObserver.NOOP, null);
    }

    /** Thematic engine with the env-driven live model (data still from the scenario stub). */
    public static ThematicReportEngine thematicFromEnv(long asOfEpochMs) {
        ReportModel model = liveModel() ? liveModel(ModelConnection.forTier("smart")) : new ScriptedReportModel();
        return new ThematicReportEngine(
                new StubThematicDataSource(asOfEpochMs), model, null,
                CompositeMemoryObserver.of(new Slf4jMemoryObserver(false), new MicrometerMemoryObserver()), null);
    }

    // ── Fund / FOF engine ─────────────────────────────────────────────────────

    /** Fully offline fund engine (synthetic NAV stub + scripted model). */
    public static FundReportEngine fundOffline(long asOfEpochMs) {
        return new FundReportEngine(
                new StubFundDataSource(asOfEpochMs), new ScriptedReportModel(), null, MemoryObserver.NOOP, null);
    }

    // ── Bond / fixed-income engine ────────────────────────────────────────────

    /** Fully offline bond engine (synthetic credit-issue stub + scripted model). */
    public static BondReportEngine bondOffline(long asOfEpochMs) {
        return new BondReportEngine(
                new StubBondDataSource(asOfEpochMs), new ScriptedReportModel(), null, MemoryObserver.NOOP, null);
    }

    // ── Model wiring ──────────────────────────────────────────────────────────

    /** Production live model: retry(timeout(openJiuwen)) — bounded latency + transient-failure backoff. */
    private static ReportModel liveModel(ModelConnection conn) {
        return new RetryReportModel(
                new TimeoutReportModel(new OpenJiuwenReportModel(conn),
                        Duration.ofSeconds(envInt("RESEARCH_MODEL_TIMEOUT_S", 60))),
                envInt("RESEARCH_MODEL_RETRIES", 3),
                envInt("RESEARCH_MODEL_BACKOFF_MS", 1500));
    }

    static boolean liveModel() {
        return "true".equalsIgnoreCase(System.getenv("RESEARCH_REPORT_LIVE_MODEL"));
    }

    // ── Web playground model selection ────────────────────────────────────────

    /**
     * The model the web playground should use: the wrapped live model (retry +
     * timeout around the real LLM) when {@code live} is requested AND a real
     * endpoint is configured, otherwise the deterministic scripted model. Lets the
     * page offer a "脚本 / glm" toggle while still running with no key.
     */
    public static ReportModel webModel(boolean live) {
        return (live && glmConfigured()) ? liveModel(ModelConnection.forTier("smart")) : new ScriptedReportModel();
    }

    /** True when a real LLM endpoint is wired (BANK_LLM_* set to something non-placeholder). */
    public static boolean glmConfigured() {
        String base = System.getenv("BANK_LLM_API_BASE");
        String key = System.getenv("BANK_LLM_API_KEY");
        return base != null && !base.isBlank() && !base.contains("localhost")
                && key != null && !key.isBlank() && !"sk-local-placeholder".equals(key);
    }

    private static int envInt(String key, int def) {
        try {
            String v = System.getenv(key);
            return v == null || v.isBlank() ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
