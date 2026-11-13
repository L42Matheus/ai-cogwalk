package br.edu.ifpb.aicogwalk.service;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.config.JsonSupport;
import br.edu.ifpb.aicogwalk.domain.Alert;
import br.edu.ifpb.aicogwalk.domain.AlertState;
import br.edu.ifpb.aicogwalk.domain.PromptStrategy;
import br.edu.ifpb.aicogwalk.domain.Run;
import br.edu.ifpb.aicogwalk.domain.RunStatus;
import br.edu.ifpb.aicogwalk.domain.RunStep;
import br.edu.ifpb.aicogwalk.domain.TaskDef;
import br.edu.ifpb.aicogwalk.domain.TaskStep;
import br.edu.ifpb.aicogwalk.evaluation.CognitiveEvaluator;
import br.edu.ifpb.aicogwalk.evaluation.EvaluationInput;
import br.edu.ifpb.aicogwalk.evaluation.EvaluationResult;
import br.edu.ifpb.aicogwalk.evaluation.EvaluatorFactory;
import br.edu.ifpb.aicogwalk.evaluation.ExampleBank;
import br.edu.ifpb.aicogwalk.evaluation.FewShotExample;
import br.edu.ifpb.aicogwalk.evaluation.ProposedAlert;
import br.edu.ifpb.aicogwalk.evaluation.VerificationVerdict;
import br.edu.ifpb.aicogwalk.navigation.Capture;
import br.edu.ifpb.aicogwalk.navigation.NavigationException;
import br.edu.ifpb.aicogwalk.navigation.NavigationPolicy;
import br.edu.ifpb.aicogwalk.navigation.NavigationService;
import br.edu.ifpb.aicogwalk.navigation.UiElement;
import br.edu.ifpb.aicogwalk.repo.AlertRepository;
import br.edu.ifpb.aicogwalk.repo.RunRepository;
import br.edu.ifpb.aicogwalk.repo.RunStepRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Processa uma execução: navegação (ou reaproveitamento de capturas), avaliação Q1–Q4 por etapa,
 * geração de alertas e, em C3, verificação estruturada. Cada passo grava seu resultado.
 */
@Component
public class RunWorker {
    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);
    private static final TypeReference<List<UiElement>> ELEMENTS = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final RunRepository runs;
    private final RunStepRepository steps;
    private final AlertRepository alerts;
    private final NavigationService navigation;
    private final EvaluatorFactory evaluators;
    private final ExampleBank bank;
    private final Storage storage;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public RunWorker(RunRepository runs, RunStepRepository steps, AlertRepository alerts,
                     NavigationService navigation, EvaluatorFactory evaluators, ExampleBank bank,
                     Storage storage, AppProperties props, TransactionTemplate tx) {
        this.runs = runs;
        this.steps = steps;
        this.alerts = alerts;
        this.navigation = navigation;
        this.evaluators = evaluators;
        this.bank = bank;
        this.storage = storage;
        this.props = props;
        this.tx = tx;
    }

    /** Dados da tarefa lidos numa transação curta (a execução não mantém transação aberta). */
    private record Job(Run run, String startUrl, String allowedHosts, String goal, String profile,
                       String knowledge, List<TaskStep> actions) {}

    public void process(Long runId) {
        Job job = tx.execute(s -> {
            Run r = runs.findById(runId).orElseThrow();
            TaskDef t = r.getTask();
            return new Job(r, t.getStartUrl(), t.getProject().getAllowedHosts(), t.getGoal(), t.getUserProfile(),
                    t.getExpectedKnowledge(), t.getSteps());
        });
        try {
            update(runId, r -> { r.setStatus(RunStatus.NAVEGANDO); r.setStartedAt(Instant.now()); });
            List<Capture> captures = job.run().getSourceRunId() == null
                    ? navigate(runId, job)
                    : reuse(runId, job.run().getSourceRunId());
            update(runId, r -> r.setStatus(RunStatus.AVALIANDO));
            evaluate(runId, job, captures);
            update(runId, r -> { r.setStatus(RunStatus.CONCLUIDA); r.setFinishedAt(Instant.now()); });
        } catch (Exception e) {
            log.warn("Execução {} falhou: {}", runId, e.getMessage(), e);
            update(runId, r -> {
                r.setStatus(RunStatus.FALHOU);
                r.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                r.setFinishedAt(Instant.now());
            });
        }
    }

    private List<Capture> navigate(Long runId, Job job) {
        String rel = "runs/" + runId;
        try {
            List<Capture> caps = navigation.walk(job.startUrl(), job.actions(), new NavigationPolicy(job.allowedHosts()),
                    storage.resolve(rel), rel);
            saveCaptures(runId, caps);
            return caps;
        } catch (NavigationException e) {
            saveCaptures(runId, e.getPartial());
            throw e;
        }
    }

    /** Reconstrói as capturas de uma execução anterior: as mesmas telas e elementos são reavaliados. */
    private List<Capture> reuse(Long runId, Long sourceRunId) {
        List<Capture> caps = tx.execute(s -> steps.findByRunIdOrderByStepIndexAsc(sourceRunId).stream()
                .map(RunWorker::toCapture).toList());
        if (caps == null || caps.isEmpty()) throw new IllegalStateException("A execução de origem não tem capturas.");
        saveCaptures(runId, caps);
        return caps;
    }

    static Capture toCapture(RunStep st) {
        TaskStep action = st.getActionJson() == null ? null : JsonSupport.read(st.getActionJson(), TaskStep.class);
        List<UiElement> els = st.getElementsJson() == null ? List.of() : JsonSupport.read(st.getElementsJson(), ELEMENTS);
        List<String> heads = st.getHeadingsJson() == null ? List.of() : JsonSupport.read(st.getHeadingsJson(), STRINGS);
        return new Capture(st.getStepIndex(), st.getUrl(), st.getTitle(), action, st.getTargetElementId(),
                st.getTargetCandidates(), st.getScreenshotPath(), st.getFullpagePath(), st.getHtmlPath(),
                els, heads, st.getCapturedAt());
    }

    private void saveCaptures(Long runId, List<Capture> caps) {
        tx.executeWithoutResult(s -> {
            Run run = runs.getReferenceById(runId);
            for (Capture c : caps) {
                RunStep st = new RunStep(run, c.index());
                st.setUrl(c.url());
                st.setTitle(c.title());
                st.setActionJson(c.action() == null ? null : JsonSupport.write(c.action()));
                st.setTargetElementId(c.targetElementId());
                st.setTargetCandidates(c.targetCandidates());
                st.setScreenshotPath(c.screenshotPath());
                st.setFullpagePath(c.fullpagePath());
                st.setHtmlPath(c.htmlPath());
                st.setElementsJson(JsonSupport.write(c.elements()));
                st.setHeadingsJson(JsonSupport.write(c.headings()));
                st.setCapturedAt(c.capturedAt());
                steps.save(st);
            }
        });
    }

    private void evaluate(Long runId, Job job, List<Capture> caps) {
        Run run = job.run();
        CognitiveEvaluator ev = evaluators.get(run.getEvaluator());
        List<FewShotExample> examples = run.getPromptStrategy() == PromptStrategy.FEW_SHOT
                ? bank.select(job.allowedHosts()).examples()
                : List.of();
        int total = caps.size() - 1;
        int seq = 0;
        for (int i = 0; i < total; i++) {
            Capture cur = caps.get(i);
            Capture after = caps.get(i + 1);
            List<Capture> history = run.getCondition().history ? List.copyOf(caps.subList(0, i)) : List.of();
            EvaluationInput in = new EvaluationInput(job.goal(), job.profile(), job.knowledge(), run.getCondition(),
                    i, total, cur, after, history, storage.read(cur.screenshotPath()),
                    props.browser().maxElements(), examples, run.getPromptStrategy());
            final int idx = i;
            EvaluationResult res;
            try {
                res = ev.evaluate(in);
            } catch (RuntimeException e) {
                saveStep(runId, idx, st -> st.setEvalError(e.getMessage()));
                throw new IllegalStateException("Falha ao avaliar a etapa " + (i + 1) + ": " + e.getMessage(), e);
            }
            saveStep(runId, idx, st -> {
                st.setAssessmentJson(JsonSupport.write(res.assessment()));
                st.setPromptSystem(res.systemPrompt());
                st.setPromptUser(res.userPrompt());
                st.setPromptSha(res.promptSha());
                st.setRawResponse(res.rawResponse());
                st.setInputTokens(res.inputTokens());
                st.setOutputTokens(res.outputTokens());
                st.setLatencyMs(res.latencyMs());
                st.setCacheHit(res.cacheHit());
            });
            update(runId, r -> r.addUsage(res.inputTokens(), res.outputTokens(), res.latencyMs()));

            List<ProposedAlert> proposed = res.assessment().alerts() == null ? List.of() : res.assessment().alerts();
            for (ProposedAlert pa : proposed) {
                seq++;
                String code = String.format("ACW-%d-%03d", runId, seq);
                AlertState state = AlertState.SUSPEITA;
                String note = null;
                if (run.getCondition().verification) {
                    Check check = verify(ev, pa, in, cur);
                    state = check.state();
                    note = check.note();
                    update(runId, r -> r.addUsage(check.in(), check.out(), check.millis()));
                }
                final AlertState fs = state;
                final String fn = note;
                tx.executeWithoutResult(s -> {
                    Alert a = new Alert(runs.getReferenceById(runId), code, idx, pa.criterion());
                    a.setVisualEvidence(pa.visualEvidence());
                    a.setDiagnosis(pa.diagnosis());
                    a.setCitedElements(pa.citedElementIds() == null ? "" : String.join(",", pa.citedElementIds()));
                    a.setRecommendation(pa.recommendation());
                    a.setModelConfidence(pa.confidence());
                    a.setState(fs);
                    a.setVerificationNote(fn);
                    alerts.save(a);
                });
            }
        }
    }

    private record Check(AlertState state, String note, long in, long out, long millis) {}

    /**
     * Verificação estruturada (C3): (1) checagem determinística de que os elementos citados existem
     * na captura; (2) verificação semântica independente, quando o avaliador a oferece.
     */
    private Check verify(CognitiveEvaluator ev, ProposedAlert pa, EvaluationInput in, Capture cur) {
        List<String> notes = new ArrayList<>();
        boolean ok = true;
        Set<String> ids = new HashSet<>();
        cur.elements().forEach(e -> ids.add(e.id()));
        List<String> cited = pa.citedElementIds() == null ? List.of() : pa.citedElementIds();
        List<String> missing = cited.stream().filter(c -> !ids.contains(c)).toList();
        if (!missing.isEmpty()) {
            ok = false;
            notes.add("Elementos citados inexistentes na captura: " + String.join(", ", missing) + ".");
        } else if (cited.isEmpty()) {
            notes.add("Nenhum elemento citado; evidência apenas visual.");
        } else {
            notes.add("Elementos citados existem na captura.");
        }
        long tin = 0, tout = 0, tms = 0;
        Optional<VerificationVerdict> v = ok ? ev.verify(pa, in) : Optional.empty();
        if (v.isPresent()) {
            VerificationVerdict vd = v.get();
            tin = vd.inputTokens(); tout = vd.outputTokens(); tms = vd.latencyMs();
            if (!vd.evidenceHolds()) { ok = false; notes.add("Verificador: evidência não confere com a tela."); }
            else if (!vd.consequential()) { ok = false; notes.add("Verificador: evidência existe, mas não implica dificuldade."); }
            else notes.add("Verificador: evidência confere e é consequente.");
            if (vd.reason() != null) notes.add(vd.reason());
        } else if (ok) {
            notes.add("Verificação semântica indisponível para o avaliador " + ev.name() + ".");
        }
        return new Check(ok ? AlertState.SUSPEITA : AlertState.NAO_CONFIRMADA, String.join(" ", notes), tin, tout, tms);
    }

    private void saveStep(Long runId, int index, Consumer<RunStep> change) {
        tx.executeWithoutResult(s -> steps.findByRunIdOrderByStepIndexAsc(runId).stream()
                .filter(st -> st.getStepIndex() == index).findFirst().ifPresent(change));
    }

    private void update(Long runId, Consumer<Run> change) {
        tx.executeWithoutResult(s -> change.accept(runs.findById(runId).orElseThrow()));
    }
}
