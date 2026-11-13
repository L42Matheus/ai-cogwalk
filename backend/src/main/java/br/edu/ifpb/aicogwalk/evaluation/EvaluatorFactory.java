package br.edu.ifpb.aicogwalk.evaluation;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class EvaluatorFactory {
    private final AppProperties.Llm llm;
    private final ClaudeEvaluator claude;
    private final OfflineEvaluator offline = new OfflineEvaluator();

    public EvaluatorFactory(AppProperties props) {
        this.llm = props.llm();
        this.claude = new ClaudeEvaluator(llm.model(), llm.effort(), llm.maxTokens(),
                new ResponseCache(Path.of(llm.cacheDir()), llm.cacheMode()));
    }

    public CognitiveEvaluator get(String name) {
        String n = (name == null || name.isBlank()) ? llm.provider() : name;
        return switch (n) {
            case "claude" -> claude;
            case "offline" -> offline;
            default -> throw new IllegalArgumentException("Avaliador desconhecido: " + n + " (use claude ou offline)");
        };
    }

    public String defaultName() { return llm.provider(); }

    public String effort() { return llm.effort(); }

    public boolean claudeConfigured() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return key != null && !key.isBlank();
    }
}
