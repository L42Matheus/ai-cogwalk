package br.edu.ifpb.aicogwalk.service;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.config.JsonSupport;
import br.edu.ifpb.aicogwalk.domain.Condition;
import br.edu.ifpb.aicogwalk.domain.PromptStrategy;
import br.edu.ifpb.aicogwalk.evaluation.ExampleBank;
import br.edu.ifpb.aicogwalk.domain.Run;
import br.edu.ifpb.aicogwalk.domain.RunStatus;
import br.edu.ifpb.aicogwalk.domain.TaskDef;
import br.edu.ifpb.aicogwalk.evaluation.CognitiveEvaluator;
import br.edu.ifpb.aicogwalk.evaluation.EvaluatorFactory;
import br.edu.ifpb.aicogwalk.repo.RunRepository;
import br.edu.ifpb.aicogwalk.repo.TaskDefRepository;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cria execuções e as entrega ao executor assíncrono (uma por vez). */
@Service
public class RunService {
    private final TaskDefRepository tasks;
    private final RunRepository runs;
    private final EvaluatorFactory evaluators;
    private final RunWorker worker;
    private final TaskExecutor executor;
    private final AppProperties props;
    private final ExampleBank bank;

    public RunService(TaskDefRepository tasks, RunRepository runs, EvaluatorFactory evaluators,
                      RunWorker worker, @Qualifier("runExecutor") TaskExecutor executor, AppProperties props,
                      ExampleBank bank) {
        this.bank = bank;
        this.tasks = tasks;
        this.runs = runs;
        this.evaluators = evaluators;
        this.worker = worker;
        this.executor = executor;
        this.props = props;
    }

    /** Nova execução: navega no site e avalia. */
    @Transactional
    public Run start(Long taskId, Condition condition, PromptStrategy strategy, String evaluatorName) {
        TaskDef task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Tarefa", taskId));
        return create(task, condition, strategy, evaluatorName, null);
    }

    /** Repetição da avaliação sobre as mesmas capturas de uma execução anterior (sem nova navegação). */
    @Transactional
    public Run reevaluate(Long sourceRunId, Condition condition, PromptStrategy strategy, String evaluatorName) {
        Run source = runs.findById(sourceRunId).orElseThrow(() -> new NotFoundException("Execução", sourceRunId));
        if (source.getStatus() != RunStatus.CONCLUIDA) {
            throw new IllegalStateException("Só é possível reavaliar uma execução concluída.");
        }
        Long origin = source.getSourceRunId() != null ? source.getSourceRunId() : source.getId();
        Condition c = condition != null ? condition : source.getCondition();
        String e = evaluatorName != null && !evaluatorName.isBlank() ? evaluatorName : source.getEvaluator();
        PromptStrategy st = strategy != null ? strategy : source.getPromptStrategy();
        return create(source.getTask(), c, st, e, origin);
    }

    private Run create(TaskDef task, Condition condition, PromptStrategy strategy, String evaluatorName,
                       Long sourceRunId) {
        CognitiveEvaluator ev = evaluators.get(evaluatorName);
        if ("claude".equals(ev.name()) && !evaluators.claudeConfigured()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY não configurada. Use o avaliador offline ou cadastre a chave.");
        }
        Run run = new Run(task, condition == null ? Condition.C2 : condition, ev.name(), sourceRunId);
        run.setModel(ev.modelId());
        run.setEffort(ev.effort());
        run.setPromptVersion(ev.promptVersion());
        run.setPromptStrategy(strategy == null ? PromptStrategy.ZERO_SHOT : strategy);
        if (run.getPromptStrategy() == PromptStrategy.FEW_SHOT) {
            ExampleBank.Selection sel = bank.select(task.getProject().getAllowedHosts());
            Map<String, Object> ex = new LinkedHashMap<>();
            ex.put("bankVersion", sel.bankVersion());
            ex.put("ids", sel.ids());
            ex.put("origins", sel.examples().stream().map(x -> x.origin()).distinct().toList());
            ex.put("excludedSameSystem", sel.excludedIds());
            run.setExamplesJson(JsonSupport.write(ex));
        }
        run.setConfigJson(JsonSupport.write(snapshot(task, run)));
        run = runs.save(run);
        Long id = run.getId();
        Runnable job = () -> worker.process(id);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { executor.execute(job); }
            });
        } else {
            executor.execute(job);
        }
        return run;
    }

    private Map<String, Object> snapshot(TaskDef task, Run run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("condition", run.getCondition());
        m.put("conditionHistory", run.getCondition().history);
        m.put("conditionVerification", run.getCondition().verification);
        m.put("evaluator", run.getEvaluator());
        m.put("model", run.getModel());
        m.put("effort", run.getEffort());
        m.put("promptVersion", run.getPromptVersion());
        m.put("promptStrategy", run.getPromptStrategy());
        m.put("llmCacheMode", props.llm().cacheMode());
        m.put("maxTokens", props.llm().maxTokens());
        m.put("sourceRunId", run.getSourceRunId());
        m.put("taskId", task.getId());
        m.put("taskTitle", task.getTitle());
        m.put("goal", task.getGoal());
        m.put("userProfile", task.getUserProfile());
        m.put("expectedKnowledge", task.getExpectedKnowledge());
        m.put("startUrl", task.getStartUrl());
        m.put("steps", task.getSteps());
        m.put("allowedHosts", task.getProject().getAllowedHosts());
        m.put("browser", props.browser());
        m.put("appVersion", RunService.class.getPackage().getImplementationVersion());
        return m;
    }
}
