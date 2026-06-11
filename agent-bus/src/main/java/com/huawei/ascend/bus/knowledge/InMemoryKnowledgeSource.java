package com.huawei.ascend.bus.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reference {@link KnowledgeSource} over seeded in-memory documents.
 *
 * <p>HONESTY NOTE: this is a reference implementation for exercising the
 * retrieval seam, NOT a vector store. Scoring is naive case-insensitive
 * token overlap — the fraction of query tokens that appear in the document —
 * which is good enough to make merge ordering and topK observable in tests
 * and examples, and nothing more. Real deployments plug semantic retrieval
 * in through the {@link KnowledgeSource} SPI.
 *
 * <p>Seeds are keyed by tenant; retrieval reads only the querying tenant's
 * documents, so isolation is structural.
 */
public final class InMemoryKnowledgeSource implements KnowledgeSource {

    private final String sourceId;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SeededDocument>> documentsByTenant =
            new ConcurrentHashMap<>();

    public InMemoryKnowledgeSource(String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId is required");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        this.sourceId = sourceId;
    }

    public String sourceId() {
        return sourceId;
    }

    /** Seed one document for the tenant. */
    public void seed(String tenantId, String content) {
        seed(tenantId, content, Map.of());
    }

    /** Seed one document for the tenant with provenance carried into every fragment built from it. */
    public void seed(String tenantId, String content, Map<String, Object> provenance) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(content, "content is required");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        documentsByTenant
                .computeIfAbsent(tenantId, t -> new CopyOnWriteArrayList<>())
                .add(new SeededDocument(content, provenance == null ? Map.of() : Map.copyOf(provenance)));
    }

    @Override
    public List<KnowledgeFragment> retrieve(KnowledgeQuery query) {
        Objects.requireNonNull(query, "query is required");
        List<SeededDocument> documents = documentsByTenant.get(query.tenantId());
        Set<String> queryTokens = tokenize(query.query());
        if (documents == null || queryTokens.isEmpty()) {
            return List.of();
        }
        List<KnowledgeFragment> matches = new ArrayList<>();
        for (SeededDocument document : documents) {
            double score = overlapScore(queryTokens, tokenize(document.content()));
            if (score > 0.0) {
                matches.add(new KnowledgeFragment(sourceId, document.content(), score, document.provenance()));
            }
        }
        matches.sort(Comparator.comparingDouble(KnowledgeFragment::score).reversed());
        List<KnowledgeFragment> bounded =
                matches.size() > query.topK() ? matches.subList(0, query.topK()) : matches;
        return List.copyOf(bounded);
    }

    /** Fraction of query tokens present in the document — naive by design (see class javadoc). */
    private static double overlapScore(Set<String> queryTokens, Set<String> documentTokens) {
        int hits = 0;
        for (String token : queryTokens) {
            if (documentTokens.contains(token)) {
                hits++;
            }
        }
        return (double) hits / queryTokens.size();
    }

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private record SeededDocument(String content, Map<String, Object> provenance) {
    }
}
