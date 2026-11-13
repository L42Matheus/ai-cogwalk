package br.edu.ifpb.aicogwalk.nav;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.config.JsonSupport;
import br.edu.ifpb.aicogwalk.domain.RunStatus;
import br.edu.ifpb.aicogwalk.evaluation.Hashing;
import br.edu.ifpb.aicogwalk.evaluation.ResponseCache;
import br.edu.ifpb.aicogwalk.service.Storage;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Executa uma rodada de previsões sobre os estados de um conjunto e calcula as métricas. */
@Component
public class NavWorker {
    private static final Logger log = LoggerFactory.getLogger(NavWorker.class);

    private final NavRunRepository runs;
    private final NavStateRepository states;
    private final NavPredictionRepository predictions;
    private final NavAssets assets;
    private final Storage storage;
    private final AppProperties props;

    public NavWorker(NavRunRepository runs, NavStateRepository states, NavPredictionRepository predictions,
                     NavAssets assets, Storage storage, AppProperties props) {
        this.runs = runs;
        this.states = states;
        this.predictions = predictions;
        this.assets = assets;
        this.storage = storage;
        this.props = props;
    }

    /** Configuração congelada de uma rodada (gravada em config_json antes de começar). */
    public record RunConfig(NavMethod method, String model, String effort, int samples, int parallel,
                            int maxCalls, double coverageTau, Double tau, List<String> variants,
                            List<String> splits, String promptVersion, String cacheMode, String datasetRevision,
                            List<String> stateKeys) {}

    public void process(Long runId) {
        NavRun run = runs.findById(runId).orElseThrow();
        RunConfig cfg = JsonSupport.read(run.getConfigJson(), RunConfig.class);
        run.setStatus(RunStatus.AVALIANDO);
        run.setStartedAt(Instant.now());
        runs.save(run);
        try {
            List<NavState> todo = states.findByDatasetIdOrderByIdAsc(run.getDataset().getId()).stream()
                    .filter(s -> cfg.variants() == null || cfg.variants().isEmpty() || cfg.variants().contains(s.getVariant()))
                    .filter(s -> cfg.splits() == null || cfg.splits().isEmpty() || cfg.splits().contains(s.getSplit()))
                    .filter(s -> cfg.stateKeys() == null || cfg.stateKeys().isEmpty() || cfg.stateKeys().contains(s.getStateKey()))
                    .toList();
            run.setStatesTotal(todo.size());
            runs.save(run);
            Map<String, Object> extra = new LinkedHashMap<>();
            Set<Long> failedStates = new HashSet<>();
            Map<Long, Prediction> out = switch (cfg.method()) {
                case UNIFORM -> uniform(todo);
                case B0 -> b0(todo, cfg, extra);
                default -> llm(run, todo, cfg, extra, failedStates);
            };
            List<String> lines = new ArrayList<>();
            List<StateResult> results = new ArrayList<>();
            for (NavState s : todo) {
                Prediction p = out.get(s.getId());
                NavPrediction np = new NavPrediction(run, s, p);
                NavMetrics.StateMetrics m = null;
                boolean failed = failedStates.contains(s.getId());
                if (failed) np.setError("Sem resposta utilizável do modelo (recusa, truncamento ou JSON inválido em todas as tentativas).");
                if (s.getHumanN() > 0 && !failed) {
                    List<String> ids = s.getOptions().stream().map(NavOption::id).toList();
                    m = NavMetrics.compute(ids, s.getHumanCounts(), p.distribution(), cfg.coverageTau(), s.getId());
                    np.setMetricsJson(JsonSupport.write(m));
                }
                predictions.save(np);
                results.add(new StateResult(s, m));
                lines.add(s.getStateKey() + "|" + s.getInputHash() + "|" + JsonSupport.write(p.distribution()));
            }
            Collections.sort(lines);
            NavRun fresh = runs.findById(runId).orElseThrow();
            fresh.setFrozenHash(Hashing.sha256(String.join("\n", lines), fresh.getConfigJson()));
            Map<String, Object> summary = summarize(results);
            summary.put("statesWithoutPrediction", failedStates.size());
            summary.putAll(extra);
            fresh.setSummaryJson(JsonSupport.write(summary));
            fresh.setStatesDone(todo.size());
            fresh.setStatus(RunStatus.CONCLUIDA);
            fresh.setFinishedAt(Instant.now());
            runs.save(fresh);
        } catch (Exception e) {
            log.warn("Rodada NAV {} falhou", runId, e);
            NavRun fresh = runs.findById(runId).orElseThrow();
            fresh.setStatus(RunStatus.FALHOU);
            fresh.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            fresh.setFinishedAt(Instant.now());
            runs.save(fresh);
        }
    }

    record StateResult(NavState state, NavMetrics.StateMetrics m) {}

    private Map<Long, Prediction> uniform(List<NavState> todo) {
        Map<Long, Prediction> out = new HashMap<>();
        for (NavState s : todo) {
            List<String> ids = s.getOptions().stream().map(NavOption::id).toList();
            out.put(s.getId(), Prediction.of(NavMetrics.normalize(ids, Map.of()), null));
        }
        return out;
    }

    // ---------------------------------------------------------------- B0

    private Map<Long, Prediction> b0(List<NavState> todo, RunConfig cfg, Map<String, Object> extra) {
        MiniLmEmbedder emb = assets.embedder();
        Map<String, float[]> cache = new HashMap<>();
        Map<Long, Map<String, Double>> sims = new HashMap<>();
        for (NavState s : todo) {
            float[] t = cache.computeIfAbsent(s.getTaskText(), emb::embed);
            Map<String, Double> m = new LinkedHashMap<>();
            for (NavOption o : s.getOptions()) {
                String label = o.label() == null ? "" : o.label();
                float[] l = cache.computeIfAbsent(label, emb::embed);
                m.put(o.id(), MiniLmEmbedder.cosine(t, l));
            }
            sims.put(s.getId(), m);
        }
        double tau;
        String tauSource;
        List<NavState> dev = todo.stream().filter(s -> NavState.DEV.equals(s.getSplit()) && s.getHumanN() > 0).toList();
        if (cfg.tau() != null) {
            tau = cfg.tau();
            tauSource = "informada na rodada";
        } else if (!dev.isEmpty()) {
            tau = fitTau(dev, sims);
            tauSource = "ajustada nos estados DEV (" + dev.size() + "), minimizando a JSD média";
        } else {
            tau = 0.05;
            tauSource = "padrão (sem estados DEV com dados humanos)";
        }
        extra.put("tau", tau);
        extra.put("tauSource", tauSource);
        extra.put("embeddingModel", "all-MiniLM-L6-v2 (ONNX)");
        extra.put("embeddingModelSha256", assets.embedderSha());
        Map<Long, Prediction> out = new HashMap<>();
        for (NavState s : todo) {
            Map<String, Double> sim = sims.get(s.getId());
            out.put(s.getId(), Prediction.of(softmax(sim, tau), Map.of("similarity", sim, "tau", tau)));
        }
        return out;
    }

    static Map<String, Double> softmax(Map<String, Double> sim, double tau) {
        double max = sim.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        Map<String, Double> e = new LinkedHashMap<>();
        double z = 0;
        for (var en : sim.entrySet()) {
            double v = Math.exp((en.getValue() - max) / tau);
            e.put(en.getKey(), v);
            z += v;
        }
        for (var k : e.keySet()) e.put(k, e.get(k) / z);
        return e;
    }

    static double fitTau(List<NavState> dev, Map<Long, Map<String, Double>> sims) {
        double best = 0.05, bestJsd = Double.MAX_VALUE;
        for (double logT = Math.log(0.005); logT <= Math.log(2.0) + 1e-9; logT += 0.05) {
            double t = Math.exp(logT);
            double s = 0;
            for (NavState st : dev) {
                List<String> ids = st.getOptions().stream().map(NavOption::id).toList();
                s += NavMetrics.jsd(NavMetrics.normalize(ids, st.getHumanCounts()), softmax(sims.get(st.getId()), t));
            }
            s /= dev.size();
            if (s < bestJsd) { bestJsd = s; best = t; }
        }
        return best;
    }

    // ---------------------------------------------------------------- B1

    private Map<Long, Prediction> llm(NavRun run, List<NavState> todo, RunConfig cfg, Map<String, Object> extra,
                                      Set<Long> failedStates) {
        if (!NavLlmClient.configured(cfg.model())) {
            throw new IllegalStateException("Chave da API ausente para o modelo " + cfg.model()
                    + (NavLlmClient.isOpenAi(cfg.model()) ? " (OPENAI_API_KEY)." : " (ANTHROPIC_API_KEY)."));
        }
        ResponseCache cache = new ResponseCache(Path.of(props.llm().cacheDir()), cfg.cacheMode());
        NavLlmClient client = new NavLlmClient(cfg.model(), cfg.effort(), props.nav().maxTokens(), cache);
        int perState = cfg.method().sampled ? cfg.samples() : 1;
        int planned = perState * todo.size();
        if (planned > cfg.maxCalls()) {
            throw new IllegalStateException("A rodada exigiria " + planned + " chamadas, acima do teto de "
                    + cfg.maxCalls() + " (AICOGWALK_NAV_MAX_CALLS).");
        }
        extra.put("plannedCalls", planned);
        AtomicInteger invalid = new AtomicInteger();
        AtomicInteger refusals = new AtomicInteger();
        AtomicInteger truncated = new AtomicInteger();
        Map<Long, Prediction> out = new HashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, cfg.parallel()));
        try {
            int done = 0;
            for (NavState s : todo) {
                byte[] png = s.getScreenshotPath() != null && "VISUAL".equals(s.getModality())
                        ? storage.read(s.getScreenshotPath()) : null;
                NavInput in = NavInput.of(s, png);
                String system = NavPrompts.system(cfg.method());
                String user = NavPrompts.user(in);
                List<String> ids = in.options().stream().map(NavOption::id).toList();
                Map<String, Object> schema = NavPrompts.schema(cfg.method(), ids);
                List<Future<NavLlmClient.Result>> fs = new ArrayList<>();
                for (int k = 0; k < perState; k++) {
                    final int idx = k;
                    fs.add(pool.submit(() -> client.call(system, user, png, schema, idx)));
                }
                long tin = 0, tout = 0, ms = 0, billedIn = 0, billedOut = 0;
                int hits = 0;
                List<String> samples = new ArrayList<>();
                List<Object> details = new ArrayList<>();
                Map<String, Double> dist = null;
                for (Future<NavLlmClient.Result> f : fs) {
                    NavLlmClient.Result r = get(f);
                    tin += r.inputTokens();
                    tout += r.outputTokens();
                    ms += r.millis();
                    if (r.cacheHit()) hits++;
                    else { billedIn += r.inputTokens(); billedOut += r.outputTokens(); }
                    if (r.failed()) {
                        // Recusa ou truncamento após todas as tentativas: a amostra é descartada e contada.
                        ("refusal".equals(r.failure()) ? refusals : truncated).incrementAndGet();
                        details.add(Map.of("failure", r.failure()));
                        continue;
                    }
                    Map<String, Object> parsed;
                    try {
                        parsed = JsonSupport.read(r.raw(), new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        invalid.incrementAndGet();
                        details.add(Map.of("invalid", r.raw()));
                        continue;
                    }
                    details.add(parsed);
                    if (cfg.method().sampled) {
                        String c = String.valueOf(parsed.get("choice"));
                        if (ids.contains(c)) samples.add(c); else invalid.incrementAndGet();
                    } else {
                        dist = parseDistribution(parsed, ids);
                    }
                }
                if (cfg.method().sampled) {
                    Map<String, Integer> counts = new HashMap<>();
                    for (String c : samples) counts.merge(c, 1, Integer::sum);
                    dist = NavMetrics.normalize(ids, counts);
                }
                boolean usable = cfg.method().sampled ? !samples.isEmpty() : dist != null;
                if (dist == null) dist = NavMetrics.normalize(ids, Map.of());
                if (!usable) failedStates.add(s.getId());
                out.put(s.getId(), new Prediction(dist, cfg.method().sampled ? samples : null, details, system, user,
                        Hashing.sha256(system, user, png == null ? "" : Hashing.sha256(png), JsonSupport.write(schema)),
                        tin, tout, ms, hits));
                NavRun fresh = runs.findById(run.getId()).orElseThrow();
                fresh.addUsage(billedIn, billedOut, perState - hits, ms); // só o que foi efetivamente cobrado (sem cache)
                fresh.setStatesDone(++done);
                runs.save(fresh);
            }
        } finally {
            pool.shutdownNow();
        }
        extra.put("invalidResponses", invalid.get());
        extra.put("refusals", refusals.get());
        extra.put("truncated", truncated.get());
        extra.put("attemptsPerSample", NavLlmClient.ATTEMPTS);
        NavRun totals = runs.findById(run.getId()).orElseThrow();
        double[] price = NavLlmClient.pricePerMTok(cfg.model());
        if (price != null) {
            extra.put("pricePerMTokUsd", Map.of("input", price[0], "output", price[1]));
            extra.put("estimatedCostUsd", (totals.getInputTokens() * price[0] + totals.getOutputTokens() * price[1]) / 1e6);
            extra.put("priceNote", NavLlmClient.PRICE_NOTE);
        }
        extra.put("provider", client.provider());
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Double> parseDistribution(Map<String, Object> parsed, List<String> ids) {
        Map<String, Double> raw = new HashMap<>();
        Object d = parsed.get("distribution");
        if (d instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Object id = m.get("optionId");
                    Object p = m.get("probability");
                    if (id != null && p instanceof Number n && ids.contains(String.valueOf(id))) {
                        raw.merge(String.valueOf(id), n.doubleValue(), Double::sum);
                    }
                }
            }
        }
        return NavMetrics.normalize(ids, raw);
    }

    private static <T> T get(Future<T> f) {
        try {
            return f.get();
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            throw new IllegalStateException(c);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- resumo

    static Map<String, Object> summarize(List<StateResult> results) {
        Map<String, Object> groups = new LinkedHashMap<>();
        List<String> splits = List.of("TEST", "DEV", "ALL");
        List<String> modalities = List.of("ALL", "TEXT", "VISUAL");
        for (String sp : splits) {
            for (String mo : modalities) {
                List<StateResult> sel = results.stream().filter(r -> r.m() != null)
                        .filter(r -> sp.equals("ALL") || sp.equals(r.state().getSplit()))
                        .filter(r -> mo.equals("ALL") || mo.equals(r.state().getModality()))
                        .toList();
                if (sel.isEmpty()) continue;
                groups.put(sp + "|" + mo, aggregate(sel));
            }
        }
        Map<String, Object> byVariant = new LinkedHashMap<>();
        results.stream().filter(r -> r.m() != null).map(r -> r.state().getVariant()).distinct().forEach(v ->
                byVariant.put(v, aggregate(results.stream().filter(r -> r.m() != null && v.equals(r.state().getVariant())).toList())));
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("groups", groups);
        s.put("byVariant", byVariant);
        s.put("statesWithHumans", results.stream().filter(r -> r.m() != null).count());
        s.put("statesWithoutHumans", results.stream().filter(r -> r.m() == null).count());
        return s;
    }

    static Map<String, Object> aggregate(List<StateResult> sel) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsd", agg(sel, x -> x.jsd()));
        m.put("coverage", agg(sel, x -> x.coverage()));
        m.put("top1", agg(sel, NavMetrics.StateMetrics::top1));
        m.put("omitted", agg(sel, x -> (double) x.omitted()));
        m.put("entropyPred", agg(sel, x -> x.entropyPred()));
        m.put("entropyHuman", agg(sel, x -> x.entropyHuman()));
        m.put("logLoss", agg(sel, x -> x.logLoss()));
        m.put("brier", agg(sel, x -> x.brier()));
        m.put("samplingFloorJsd", agg(sel, x -> x.samplingFloorJsd()));
        return m;
    }

    interface Pick { double of(NavMetrics.StateMetrics m); }

    static NavMetrics.Agg agg(List<StateResult> sel, Pick pick) {
        Map<String, List<Double>> byTask = new TreeMap<>();
        for (StateResult r : sel) byTask.computeIfAbsent(r.state().getTaskKey(), k -> new ArrayList<>()).add(pick.of(r.m()));
        return NavMetrics.clusterBootstrap(byTask, 20261113L);
    }
}
