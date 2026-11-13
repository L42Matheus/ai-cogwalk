package br.edu.ifpb.aicogwalk.nav;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import br.edu.ifpb.aicogwalk.evaluation.Hashing;
import br.edu.ifpb.aicogwalk.service.Storage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importa o dataset público de Kuric, Demcak e Krajcovic (2025, IST 183:107740) como estados de
 * primeiro clique: 6 variantes × 10 tarefas, cada uma com as 8 categorias do menu principal e a
 * contagem de primeiros cliques observados (30 participantes por variante).
 *
 * <p>Variantes de tree test (TP, TC, TO) viram estados textuais; as de protótipo web (WTC, WTCI, WM)
 * viram estados visuais, com a captura do protótipo HTML renderizada localmente.
 */
@Service
public class KuricImporter {
    public static final String CODE = "kuric2025-ia";
    /** Divisão fixada antes de qualquer previsão: tarefas pares para desenvolvimento, ímpares para teste. */
    public static final Set<String> DEV_TASKS = Set.of("2", "4", "6", "8", "10");

    static final Map<String, String> VARIANTS = new LinkedHashMap<>();
    static {
        VARIANTS.put("TP", "TEXT");
        VARIANTS.put("TC", "TEXT");
        VARIANTS.put("TO", "TEXT");
        VARIANTS.put("WTC", "VISUAL");
        VARIANTS.put("WTCI", "VISUAL");
        VARIANTS.put("WM", "VISUAL");
    }
    static final Map<String, String> PROTOTYPE = Map.of("WTC", "desktop.html", "WTCI", "desktop-intrusive.html", "WM", "mobile.html");

    private final NavAssets assets;
    private final PrototypeRenderer renderer;
    private final NavDatasetRepository datasets;
    private final NavStateRepository states;
    private final Storage storage;
    private final AppProperties.Nav cfg;

    public KuricImporter(NavAssets assets, PrototypeRenderer renderer, NavDatasetRepository datasets,
                         NavStateRepository states, Storage storage, AppProperties props) {
        this.assets = assets;
        this.renderer = renderer;
        this.datasets = datasets;
        this.states = states;
        this.storage = storage;
        this.cfg = props.nav();
    }

    @Transactional
    public NavDataset importDataset() {
        Optional<NavDataset> existing = datasets.findByCode(CODE);
        if (existing.isPresent()) return existing.get();
        Path dir = assets.kuricDir();
        List<String> top = topLevel(dir.resolve("analysis/data/tree.csv"));
        List<Map<String, String>> rows = readCsv(dir.resolve("analysis/data/results.csv"));

        NavDataset ds = datasets.save(new NavDataset(CODE,
                "Kuric, Demcak e Krajcovic (2025) — tree testing e protótipos web",
                "https://github.com/treetest-research/information-architecture-validation", cfg.kuricRevision(),
                "CC BY-NC 4.0 (uso acadêmico, com atribuição)",
                "180 participantes, 1.800 execuções. Estados de primeiro clique por variante e tarefa. "
                        + "Divisão: tarefas pares = DEV, ímpares = TEST. Participantes em inglês (Reino Unido); "
                        + "site fictício de revista. Benchmark, não validação em portais institucionais."));

        Map<String, String> taskText = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        for (Map<String, String> r : rows) {
            String task = r.get("task");
            taskText.putIfAbsent(task, r.get("taskText"));
            String fc = r.getOrDefault("firstClick", "");
            if (fc == null || fc.isBlank()) continue;
            String topId = fc.split("-")[0];
            counts.computeIfAbsent(r.get("variant") + "|" + task, k -> new HashMap<>()).merge(topId, 1, Integer::sum);
        }

        Map<String, PrototypeRenderer.Rendered> renders = new HashMap<>();
        for (var v : VARIANTS.entrySet()) {
            String variant = v.getKey();
            boolean visual = v.getValue().equals("VISUAL");
            List<NavOption> options;
            String shotRel = null;
            String viewport = null;
            String shotSha = "";
            if (visual) {
                PrototypeRenderer.Rendered rd = renders.computeIfAbsent(variant, k ->
                        renderer.render(dir.resolve("webpage").resolve(PROTOTYPE.get(k)), k.equals("WM"), top));
                options = rd.options();
                viewport = rd.viewport();
                shotRel = "datasets/kuric2025/render/" + variant + ".png";
                write(shotRel, rd.png());
                shotSha = Hashing.sha256(rd.png());
            } else {
                options = new ArrayList<>();
                for (int i = 0; i < top.size(); i++) options.add(new NavOption(String.valueOf(i), top.get(i)));
            }
            for (var t : taskText.entrySet()) {
                String task = t.getKey();
                Map<String, Integer> c = counts.getOrDefault(variant + "|" + task, Map.of());
                String split = DEV_TASKS.contains(task) ? NavState.DEV : NavState.TEST;
                String inputHash = Hashing.sha256(t.getValue(), labels(options), v.getValue(), shotSha);
                states.save(new NavState(ds, variant + "-t" + task + "-raiz", variant, v.getValue(), task,
                        t.getValue(), "", options, c, shotRel, viewport, split, inputHash));
            }
        }
        return ds;
    }

    static String labels(List<NavOption> options) {
        StringBuilder sb = new StringBuilder();
        for (NavOption o : options) sb.append(o.id()).append('=').append(o.label()).append('\n');
        return sb.toString();
    }

    private void write(String rel, byte[] data) {
        try {
            Path p = storage.resolve(rel);
            Files.createDirectories(p.getParent());
            Files.write(p, data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Primeiro nível da arquitetura da informação (coluna 1 de tree.csv), na ordem dos ids do dataset. */
    static List<String> topLevel(Path treeCsv) {
        List<String> out = new ArrayList<>();
        try {
            List<List<String>> rows = parse(Files.readString(treeCsv, StandardCharsets.UTF_8));
            for (int i = 1; i < rows.size(); i++) {
                List<String> r = rows.get(i);
                if (!r.isEmpty() && !r.get(0).isBlank()) out.add(r.get(0).trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    static List<Map<String, String>> readCsv(Path csv) {
        try {
            List<List<String>> rows = parse(Files.readString(csv, StandardCharsets.UTF_8));
            List<String> header = rows.get(0);
            List<Map<String, String>> out = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++) {
                List<String> r = rows.get(i);
                if (r.size() == 1 && r.get(0).isBlank()) continue;
                Map<String, String> m = new HashMap<>();
                for (int j = 0; j < header.size() && j < r.size(); j++) m.put(header.get(j), r.get(j));
                out.add(m);
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** CSV RFC 4180 (aspas duplas, quebras de linha dentro de campos). */
    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder f = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { f.append('"'); i++; } else q = false;
                } else f.append(c);
            } else if (c == '"') q = true;
            else if (c == ',') { row.add(f.toString()); f.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(f.toString()); f.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else f.append(c);
        }
        if (!f.isEmpty() || !row.isEmpty()) { row.add(f.toString()); rows.add(row); }
        return rows;
    }

}
