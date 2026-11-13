package br.edu.ifpb.aicogwalk.nav;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.config.JsonSupport;
import br.edu.ifpb.aicogwalk.domain.TaskDef;
import br.edu.ifpb.aicogwalk.evaluation.Hashing;
import br.edu.ifpb.aicogwalk.navigation.Capture;
import br.edu.ifpb.aicogwalk.navigation.NavigationPolicy;
import br.edu.ifpb.aicogwalk.navigation.NavigationService;
import br.edu.ifpb.aicogwalk.navigation.UiElement;
import br.edu.ifpb.aicogwalk.repo.TaskDefRepository;
import br.edu.ifpb.aicogwalk.service.NotFoundException;
import br.edu.ifpb.aicogwalk.service.Storage;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Orquestra o módulo NAV: importação do benchmark, captura independente de tarefas e rodadas. */
@Service
public class NavService {
    private final KuricImporter importer;
    private final NavWorker worker;
    private final NavDatasetRepository datasets;
    private final NavStateRepository states;
    private final NavRunRepository runs;
    private final TaskDefRepository tasks;
    private final NavigationService navigation;
    private final Storage storage;
    private final TaskExecutor executor;
    private final AppProperties props;
    private final AtomicReference<String> importStatus = new AtomicReference<>("ocioso");

    public NavService(KuricImporter importer, NavWorker worker, NavDatasetRepository datasets, NavStateRepository states,
                      NavRunRepository runs, TaskDefRepository tasks, NavigationService navigation, Storage storage,
                      @Qualifier("runExecutor") TaskExecutor executor, AppProperties props) {
        this.importer = importer;
        this.worker = worker;
        this.datasets = datasets;
        this.states = states;
        this.runs = runs;
        this.tasks = tasks;
        this.navigation = navigation;
        this.storage = storage;
        this.executor = executor;
        this.props = props;
    }

    public String importStatus() { return importStatus.get(); }

    /** Importa o benchmark em segundo plano (download, extração e renderização dos protótipos). */
    public void importKuricAsync() {
        if (datasets.findByCode(KuricImporter.CODE).isPresent()) {
            importStatus.set("concluído");
            return;
        }
        importStatus.set("na fila");
        executor.execute(() -> {
            try {
                importStatus.set("importando");
                importer.importDataset();
                importStatus.set("concluído");
            } catch (Exception e) {
                importStatus.set("falhou: " + e.getMessage());
            }
        });
    }

    public NavDataset importKuricNow() { return importer.importDataset(); }

    /**
     * Captura independente: abre só a página inicial da tarefa, sem executar a rota cadastrada,
     * e cria um estado de primeiro clique com as opções visíveis na primeira dobra. Não há dados
     * humanos (estado de PILOTO); as previsões ficam congeladas para comparação futura.
     */
    @Transactional
    public NavState captureTask(Long taskId) {
        TaskDef task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Tarefa", taskId));
        NavigationPolicy policy = new NavigationPolicy(task.getProject().getAllowedHosts());
        String code = "tarefa-" + task.getId();
        NavDataset ds = datasets.findByCode(code).orElseGet(() -> datasets.save(new NavDataset(code,
                "Tarefa " + task.getId() + ": " + task.getTitle(), task.getStartUrl(), null, null,
                "Capturas independentes da página inicial (sem seguir a rota correta). Sem dados humanos até a coleta autorizada.")));
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        String rel = "nav/" + code + "/" + stamp;
        List<Capture> caps = navigation.walk(task.getStartUrl(), List.of(), policy, storage.resolve(rel), rel);
        Capture c = caps.get(0);
        List<NavOption> options = new ArrayList<>();
        for (UiElement e : c.elements()) {
            if (!e.inViewport() || !policy.isClickable(e)) continue;
            options.add(new NavOption(e.id(), e.text(), e.x(), e.y(), e.width(), e.height()));
        }
        if (options.isEmpty()) throw new IllegalStateException("Nenhuma opção clicável visível na página inicial.");
        byte[] png = storage.read(c.screenshotPath());
        String inputHash = Hashing.sha256(task.getGoal(), KuricImporter.labels(options), "VISUAL", Hashing.sha256(png));
        String vp = props.browser().viewportWidth() + "x" + props.browser().viewportHeight();
        return states.save(new NavState(ds, "captura-" + stamp, "pagina-inicial", "VISUAL", "tarefa-" + task.getId(),
                task.getGoal(), "", options, Map.of(), c.screenshotPath(), vp, NavState.PILOT, inputHash));
    }

    @Transactional
    public NavRun start(Long datasetId, NavMethod method, String model, String effort, Integer samples,
                        List<String> variants, List<String> splits, Double tau,
                        List<String> stateKeys) {
        NavDataset ds = datasets.findById(datasetId).orElseThrow(() -> new NotFoundException("Conjunto", datasetId));
        if (states.countByDatasetId(datasetId) == 0) throw new IllegalStateException("O conjunto não tem estados.");
        AppProperties.Nav nav = props.nav();
        String m = method.llm ? (model == null || model.isBlank() ? props.llm().model() : model) : null;
        if (method.llm && !NavLlmClient.configured(m)) {
            throw new IllegalStateException("Chave da API não configurada para " + m + ".");
        }
        NavWorker.RunConfig cfg = new NavWorker.RunConfig(method, m,
                method.llm ? (effort == null || effort.isBlank() ? nav.effort() : effort) : null,
                method.sampled ? (samples == null || samples < 1 ? nav.samples() : samples) : 1,
                Math.max(1, nav.parallel()), nav.maxCalls(), nav.coverageTau(), tau,
                variants == null ? List.of() : variants, splits == null ? List.of() : splits,
                method.llm ? NavPrompts.VERSION : (method == NavMethod.B0 ? "b0-minilm-1.0" : "uniforme"),
                props.llm().cacheMode(), ds.getRevision(), stateKeys == null ? List.of() : stateKeys);
        NavRun run = new NavRun(ds, method, m);
        run.setConfigJson(JsonSupport.write(cfg));
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
}
