package br.edu.ifpb.aicogwalk.nav;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NavUnitTests {

    private static final List<String> IDS = List.of("1", "2", "3", "4");

    @Test
    void jsdIsZeroForEqualAndOneForDisjointDistributions() {
        Map<String, Double> p = NavMetrics.normalize(IDS, Map.of("1", 1, "2", 1));
        Map<String, Double> q = NavMetrics.normalize(IDS, Map.of("3", 5, "4", 5));
        assertThat(NavMetrics.jsd(p, p)).isCloseTo(0, within(1e-12));
        assertThat(NavMetrics.jsd(p, q)).isCloseTo(1, within(1e-12));
        // Valor conhecido: P=(1,0), Q=(0.5,0.5) -> JSD base 2 = 0.311278...
        Map<String, Double> a = new LinkedHashMap<>(Map.of("1", 1.0, "2", 0.0));
        Map<String, Double> b = new LinkedHashMap<>(Map.of("1", 0.5, "2", 0.5));
        assertThat(NavMetrics.jsd(a, b)).isCloseTo(0.3112781244591328, within(1e-9));
        assertThat(NavMetrics.jsd(a, b)).isCloseTo(NavMetrics.jsd(b, a), within(1e-12));
    }

    @Test
    void normalizeFallsBackToUniformAndIgnoresUnknownOrNegative() {
        Map<String, Double> u = NavMetrics.normalize(IDS, Map.of("x", 3, "1", -2));
        assertThat(u.values()).allSatisfy(v -> assertThat(v).isCloseTo(0.25, within(1e-12)));
        assertThat(NavMetrics.entropyNorm(u)).isCloseTo(1, within(1e-12));
    }

    @Test
    void coverageTop1AndOmissionsFollowTheDefinitions() {
        Map<String, Integer> counts = Map.of("1", 6, "2", 3, "3", 1);
        Map<String, Double> pred = Map.of("1", 0.9, "2", 0.04, "3", 0.06);
        NavMetrics.StateMetrics m = NavMetrics.compute(IDS, counts, pred, 0.05, 1);
        assertThat(m.humanN()).isEqualTo(10);
        assertThat(m.top1()).isEqualTo(1.0);
        // previsão uniforme não ganha o top-1 por empate: 1 das 4 opções empatadas é a moda humana
        assertThat(NavMetrics.compute(IDS, counts, Map.of(), 0.05, 1).top1()).isCloseTo(0.25, within(1e-12));
        // opções com P̂ >= 0.05: 1 e 3 -> cobrem 0.6 + 0.1 da massa humana
        assertThat(m.coverage()).isCloseTo(0.7, within(1e-12));
        // opção 2 tem 30% das pessoas e P̂ < τ -> omitida
        assertThat(m.omitted()).isEqualTo(1);
        assertThat(m.logLoss()).isPositive();
        assertThat(m.samplingFloorJsd()).isBetween(0.0, 1.0);
    }

    @Test
    void perfectPredictionReachesZeroJsdButNotBelowTheSamplingFloorInExpectation() {
        Map<String, Integer> counts = Map.of("1", 5, "2", 5);
        NavMetrics.StateMetrics m = NavMetrics.compute(IDS, counts, Map.of("1", 0.5, "2", 0.5), 0.05, 7);
        assertThat(m.jsd()).isCloseTo(0, within(1e-12));
        assertThat(m.coverage()).isCloseTo(1, within(1e-12));
        assertThat(m.samplingFloorJsd()).isPositive();
    }

    @Test
    void clusterBootstrapIsDeterministicForAFixedSeed() {
        Map<String, List<Double>> c = new LinkedHashMap<>();
        c.put("t1", List.of(0.1, 0.2));
        c.put("t3", List.of(0.4));
        c.put("t5", List.of(0.3, 0.5, Double.NaN));
        NavMetrics.Agg a = NavMetrics.clusterBootstrap(c, 20261113L);
        NavMetrics.Agg b = NavMetrics.clusterBootstrap(c, 20261113L);
        assertThat(a).isEqualTo(b);
        assertThat(a.states()).isEqualTo(5);
        assertThat(a.clusters()).isEqualTo(3);
        assertThat(a.mean()).isCloseTo(0.3, within(1e-12));
        assertThat(a.lo()).isLessThanOrEqualTo(a.mean());
        assertThat(a.hi()).isGreaterThanOrEqualTo(a.mean());
    }

    @Test
    void softmaxTemperatureControlsSharpness() {
        Map<String, Double> sim = new LinkedHashMap<>(Map.of("1", 0.6, "2", 0.3, "3", 0.1));
        Map<String, Double> sharp = NavWorker.softmax(sim, 0.01);
        Map<String, Double> flat = NavWorker.softmax(sim, 100);
        assertThat(sharp.values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1, within(1e-12));
        assertThat(sharp.get("1")).isGreaterThan(0.99);
        assertThat(flat.get("1")).isCloseTo(1.0 / 3, within(0.01));
    }

    @Test
    void parseDistributionKeepsOnlyKnownOptionsAndRenormalizes() {
        Map<String, Object> parsed = Map.of("distribution", List.of(
                Map.of("optionId", "1", "probability", 0.5),
                Map.of("optionId", "zz", "probability", 0.4),
                Map.of("optionId", "2", "probability", 0.5)));
        Map<String, Double> d = NavWorker.parseDistribution(parsed, IDS);
        assertThat(d.get("1")).isCloseTo(0.5, within(1e-12));
        assertThat(d.get("3")).isZero();
    }

    @Test
    void csvParserHandlesQuotesCommasAndNewlinesInsideFields() {
        List<List<String>> rows = KuricImporter.parse("a,b,c\r\n\"x, y\",\"he said \"\"hi\"\"\",\"l1\nl2\"\n1,,3");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)).containsExactly("x, y", "he said \"hi\"", "l1\nl2");
        assertThat(rows.get(2)).containsExactly("1", "", "3");
    }

    /** Garantia estrutural: o preditor não tem como receber a rota nem a resposta correta. */
    @Test
    void predictorInputHasNoFieldForTheCorrectActionOrRoute() {
        List<String> fields = Arrays.stream(NavInput.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertThat(fields).containsExactly("taskText", "options", "screenshotPng", "viewport");
        NavInput in = new NavInput("Find running shoes", List.of(new NavOption("1", "Sports"), new NavOption("2", "Home")), null, "1366x768");
        String user = NavPrompts.user(in);
        assertThat(user).contains("Find running shoes").contains("Sports").contains("Home");
        for (NavMethod m : NavMethod.values()) {
            if (!m.llm) continue;
            String sys = NavPrompts.system(m).toLowerCase(Locale.ROOT);
            assertThat(sys).doesNotContain("correct answer").doesNotContain("target");
            assertThat(NavPrompts.schema(m, List.of("1", "2")).toString()).contains("1").contains("2");
        }
    }

    @Test
    void cognitiveVariantsAskQ1toQ3AndNeverQ4() {
        for (NavMethod m : NavMethod.values()) {
            if (!m.llm) continue;
            String schema = NavPrompts.schema(m, List.of("1", "2")).toString();
            if (m.cognitive) assertThat(schema).contains("q1").contains("q2").contains("q3");
            else assertThat(schema).doesNotContain("q1");
            assertThat(schema).doesNotContain("q4");
        }
    }

    @Test
    void controlConditionAnalysesEachOptionWithoutCognitiveWalkthroughVocabulary() {
        String schema = br.edu.ifpb.aicogwalk.config.JsonSupport.write(NavPrompts.schema(NavMethod.B1_R, List.of("1", "2")));
        assertThat(schema).contains("\"required\":[\"analysis\",\"distribution\",\"rationale\"]").doesNotContain("q1");
        String extra = NavPrompts.system(NavMethod.B1_R).substring(NavPrompts.SYSTEM_DIRECT.length()).toLowerCase();
        for (String w : List.of("walkthrough", "q1", "q2", "q3", "goal", "visib", "noticeable", "associat", "vocabulary")) {
            assertThat(extra).doesNotContain(w);
        }
        assertThat(NavPrompts.system(NavMethod.B1_R)).startsWith(NavPrompts.SYSTEM_DIRECT);
        assertThat(NavPrompts.system(NavMethod.B1_CW)).startsWith(NavPrompts.SYSTEM_DIRECT);
    }

    @Test
    void schemasSerializeInAStableOrderSoCacheKeysAndPromptHashesAreReproducible() {
        String a = br.edu.ifpb.aicogwalk.config.JsonSupport.write(NavPrompts.schema(NavMethod.B1_S, List.of("1", "2")));
        assertThat(a).isEqualTo("{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"reason\",\"choice\"],"
                + "\"properties\":{\"reason\":{\"type\":\"string\"},\"choice\":{\"type\":\"string\",\"enum\":[\"1\",\"2\"]}}}");
        String b = br.edu.ifpb.aicogwalk.config.JsonSupport.write(NavPrompts.schema(NavMethod.B1_CW, List.of("1", "2")));
        assertThat(b).contains("\"required\":[\"walkthrough\",\"distribution\",\"rationale\"]");
    }

    /**
     * Confere o tokenizador e o embedder em Java contra a referência em Python (tokenizers + onnxruntime).
     * Roda só quando o modelo e o arquivo de referência existem localmente.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "AICOGWALK_TEST_MINILM_DIR", matches = ".+")
    void tokenizerAndEmbeddingsMatchPythonReference() throws Exception {
        Path dir = Path.of(System.getenv("AICOGWALK_TEST_MINILM_DIR"));
        JsonNode ref = new ObjectMapper().readTree(Path.of(System.getenv("AICOGWALK_TEST_MINILM_REF")).toFile());
        WordPieceTokenizer tok = new WordPieceTokenizer(dir.resolve("vocab.txt"), 256);
        try (MiniLmEmbedder emb = new MiniLmEmbedder(dir)) {
            for (JsonNode r : ref) {
                String text = r.get("text").asText();
                long[] expected = new long[r.get("ids").size()];
                for (int i = 0; i < expected.length; i++) expected[i] = r.get("ids").get(i).asLong();
                assertThat(tok.encode(text)).as(text).containsExactly(expected);
                float[] v = emb.embed(text);
                for (int i = 0; i < v.length; i++) {
                    assertThat((double) v[i]).as(text + "[" + i + "]").isCloseTo(r.get("emb").get(i).asDouble(), within(1e-4));
                }
            }
        }
    }
}
