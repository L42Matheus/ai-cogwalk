package br.edu.ifpb.aicogwalk.nav;

import br.edu.ifpb.aicogwalk.config.JsonSupport;
import br.edu.ifpb.aicogwalk.service.NotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas do módulo NAV, montadas como mapas JSON para o painel e para exportação. */
@Service
@Transactional(readOnly = true)
public class NavQuery {
    private final NavDatasetRepository datasets;
    private final NavStateRepository states;
    private final NavRunRepository runs;
    private final NavPredictionRepository predictions;

    public NavQuery(NavDatasetRepository datasets, NavStateRepository states, NavRunRepository runs,
                    NavPredictionRepository predictions) {
        this.datasets = datasets;
        this.states = states;
        this.runs = runs;
        this.predictions = predictions;
    }

    public List<Map<String, Object>> datasets() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (NavDataset d : datasets.findAll()) {
            Map<String, Object> m = dataset(d);
            m.put("states", states.countByDatasetId(d.getId()));
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> dataset(Long id) {
        NavDataset d = datasets.findById(id).orElseThrow(() -> new NotFoundException("Conjunto", id));
        Map<String, Object> m = dataset(d);
        m.put("stateList", states.findByDatasetIdOrderByIdAsc(id).stream().map(NavQuery::state).toList());
        m.put("runs", runs.findByDatasetIdOrderByIdDesc(id).stream().map(NavQuery::run).toList());
        return m;
    }

    public List<Map<String, Object>> runs() {
        return runs.findAllByOrderByIdDesc().stream().map(NavQuery::run).toList();
    }

    public Map<String, Object> runDetail(Long id) {
        NavRun r = runs.findById(id).orElseThrow(() -> new NotFoundException("Rodada", id));
        Map<String, Object> m = run(r);
        m.put("datasetName", r.getDataset().getName());
        m.put("datasetCode", r.getDataset().getCode());
        m.put("config", r.getConfigJson() == null ? null : JsonSupport.read(r.getConfigJson(), Map.class));
        m.put("summary", r.getSummaryJson() == null ? null : JsonSupport.read(r.getSummaryJson(), Map.class));
        List<Map<String, Object>> preds = new ArrayList<>();
        for (NavPrediction p : predictions.findByRunIdOrderByIdAsc(id)) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.getId());
            pm.put("state", state(p.getState()));
            pm.put("distribution", p.getDistribution());
            pm.put("samples", p.getSamplesJson() == null ? null : JsonSupport.read(p.getSamplesJson(), List.class));
            pm.put("metrics", p.getMetricsJson() == null ? null : JsonSupport.read(p.getMetricsJson(), Map.class));
            pm.put("promptSha", p.getPromptSha());
            pm.put("inputTokens", p.getInputTokens());
            pm.put("outputTokens", p.getOutputTokens());
            pm.put("cacheHits", p.getCacheHits());
            pm.put("error", p.getError());
            preds.add(pm);
        }
        m.put("predictions", preds);
        return m;
    }

    public Map<String, Object> prediction(Long id) {
        NavPrediction p = predictions.findById(id).orElseThrow(() -> new NotFoundException("Previsão", id));
        Map<String, Object> pm = new LinkedHashMap<>();
        pm.put("id", p.getId());
        pm.put("runId", p.getRun().getId());
        pm.put("state", state(p.getState()));
        pm.put("distribution", p.getDistribution());
        pm.put("promptSystem", p.getPromptSystem());
        pm.put("promptUser", p.getPromptUser());
        pm.put("detail", p.getDetailJson() == null ? null : JsonSupport.read(p.getDetailJson(), Object.class));
        return pm;
    }

    /** Exportação das previsões congeladas (sem os prompts completos, que estão no detalhe de cada previsão). */
    public Map<String, Object> export(Long id) {
        Map<String, Object> m = runDetail(id);
        m.put("description", "Previsões congeladas do AI-CogWalk-NAV. O hash cobre, por estado, a chave, o hash da entrada "
                + "e a distribuição prevista, além da configuração da rodada.");
        return m;
    }

    public String exportCsv(Long id) {
        StringBuilder sb = new StringBuilder("state_key,variant,modality,split,task,option_id,option_label,predicted,human_count,human_n\n");
        for (NavPrediction p : predictions.findByRunIdOrderByIdAsc(id)) {
            NavState s = p.getState();
            Map<String, Double> dist = p.getDistribution();
            Map<String, Integer> counts = s.getHumanCounts();
            for (NavOption o : s.getOptions()) {
                sb.append(csv(s.getStateKey())).append(',').append(csv(s.getVariant())).append(',').append(s.getModality())
                  .append(',').append(s.getSplit()).append(',').append(csv(s.getTaskKey())).append(',').append(csv(o.id()))
                  .append(',').append(csv(o.label())).append(',').append(String.format(Locale.ROOT, "%.6f", dist.getOrDefault(o.id(), 0.0)))
                  .append(',').append(counts.getOrDefault(o.id(), 0)).append(',').append(s.getHumanN()).append('\n');
            }
        }
        return sb.toString();
    }

    private static String csv(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    static Map<String, Object> dataset(NavDataset d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("code", d.getCode());
        m.put("name", d.getName());
        m.put("sourceUrl", d.getSourceUrl());
        m.put("revision", d.getRevision());
        m.put("license", d.getLicense());
        m.put("notes", d.getNotes());
        m.put("createdAt", d.getCreatedAt());
        return m;
    }

    static Map<String, Object> state(NavState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("key", s.getStateKey());
        m.put("variant", s.getVariant());
        m.put("modality", s.getModality());
        m.put("task", s.getTaskKey());
        m.put("taskText", s.getTaskText());
        m.put("split", s.getSplit());
        m.put("options", s.getOptions());
        m.put("humanCounts", s.getHumanCounts());
        m.put("humanN", s.getHumanN());
        m.put("screenshot", s.getScreenshotPath());
        m.put("viewport", s.getViewport());
        m.put("inputHash", s.getInputHash());
        return m;
    }

    static Map<String, Object> run(NavRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("datasetId", r.getDataset().getId());
        m.put("method", r.getMethod());
        m.put("model", r.getModel());
        m.put("status", r.getStatus());
        m.put("error", r.getError());
        m.put("statesTotal", r.getStatesTotal());
        m.put("statesDone", r.getStatesDone());
        m.put("frozenHash", r.getFrozenHash());
        m.put("inputTokens", r.getInputTokens());
        m.put("outputTokens", r.getOutputTokens());
        m.put("llmCalls", r.getLlmCalls());
        m.put("createdAt", r.getCreatedAt());
        m.put("finishedAt", r.getFinishedAt());
        if (r.getSummaryJson() != null) {
            Map<String, Object> s = JsonSupport.read(r.getSummaryJson(), new TypeReference<Map<String, Object>>() {});
            Object groups = s.get("groups");
            if (groups instanceof Map<?, ?> g) {
                Object head = g.containsKey("TEST|ALL") ? g.get("TEST|ALL") : g.get("ALL|ALL");
                m.put("headline", head);
            }
        }
        return m;
    }
}
