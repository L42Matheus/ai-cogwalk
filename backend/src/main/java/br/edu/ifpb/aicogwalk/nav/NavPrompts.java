package br.edu.ifpb.aicogwalk.nav;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompts e esquemas das condições B1. O preditor recebe só o que o participante via no momento da
 * decisão: a tarefa, a tela (ou o menu em texto) e as opções visíveis. Nunca recebe o caminho
 * correto, o destino final nem dados humanos do estado avaliado.
 */
public final class NavPrompts {
    private NavPrompts() {}

    public static final String VERSION = "nav-1.1";

    static final String NOVICE = "a first-time visitor who has never used this website and does not know how its content is organized";

    public static final String SYSTEM_DIRECT = """
        You model how real people navigate websites. You will receive a task and the options visible on one screen.
        Estimate the distribution of FIRST choices that a population of novices would make: %s.
        Do not tell which option is correct. Estimate what such people would actually click, including plausible
        mistakes and minority choices. Give a probability to every option id; the probabilities must sum to 1.
        """.formatted(NOVICE);

    public static final String SYSTEM_DIRECT_CW = SYSTEM_DIRECT + """
        Before estimating, apply the cognitive walkthrough questions that make sense before the first action,
        to every option, from the novice's point of view:
        Q1 (goal): would a novice with this task plausibly decide to look under this option?
        Q2 (visibility): is the option noticeable on this screen, considering position, size and contrast?
        Q3 (association): does the option's label connect to the task in the novice's own vocabulary?
        Q4 (feedback after the action) does not apply here, because the result of the click is not visible yet.
        Base the distribution on these answers.
        """;

    public static final String SYSTEM_SAMPLE = """
        You are a participant in a usability study: %s.
        You will receive a task and the options visible on the screen. Choose the option you would click first,
        as this participant would, not as an expert. There are no right or wrong answers.
        In "reason", say in one short sentence why this participant would click that option.
        """.formatted(NOVICE);

    public static final String SYSTEM_SAMPLE_CW = SYSTEM_SAMPLE + """
        Before choosing, think briefly, as this participant, about each option: would I look here for this task (Q1)?
        Do I notice it on the screen (Q2)? Does its label match the words I have in mind (Q3)? Then choose.
        """;

    public static String system(NavMethod m) {
        return switch (m) {
            case B1 -> SYSTEM_DIRECT;
            case B1_CW -> SYSTEM_DIRECT_CW;
            case B1_S -> SYSTEM_SAMPLE;
            case B1_CW_S -> SYSTEM_SAMPLE_CW;
            default -> throw new IllegalArgumentException("Método sem prompt de LLM: " + m);
        };
    }

    public static String user(NavInput in) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task: ").append(in.taskText().trim()).append("\n\n");
        if (in.screenshotPng() != null) {
            sb.append("The image shows the screen as the participant sees it before the first click (")
              .append(in.viewport()).append(" pixels).\n");
        } else {
            sb.append("The participant sees a website menu presented as a list of categories (no images).\n");
        }
        sb.append("Options (id: label");
        if (in.screenshotPng() != null) sb.append(" @ x,y,width,height");
        sb.append("):\n");
        for (NavOption o : in.options()) {
            sb.append("- ").append(o.id()).append(": ").append(o.label() == null || o.label().isBlank() ? "(no text)" : o.label());
            if (in.screenshotPng() != null && o.x() != null) {
                sb.append(" @ ").append(o.x()).append(',').append(o.y()).append(',').append(o.width()).append(',').append(o.height());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Esquema JSON da resposta. Os mapas são ordenados (LinkedHashMap) para que o JSON enviado, a chave de
     * cache e o hash do prompt sejam idênticos entre execuções da JVM; a ordem das propriedades também
     * define a ordem de geração (raciocínio curto antes da escolha).
     */
    public static Map<String, Object> schema(NavMethod m, List<String> ids) {
        Map<String, Object> optionId = o("type", "string", "enum", ids);
        Map<String, Object> rating = o("type", "string", "enum", List.of("yes", "partly", "no"));
        Map<String, Object> cwItem = obj(o("optionId", optionId, "q1", rating, "q2", rating, "q3", rating));
        Map<String, Object> cw = o("type", "array", "items", cwItem);
        if (m.sampled) {
            if (m.cognitive) return obj(o("walkthrough", cw, "choice", optionId));
            return obj(o("reason", o("type", "string"), "choice", optionId));
        }
        Map<String, Object> distItem = obj(o("optionId", optionId, "probability", o("type", "number")));
        Map<String, Object> dist = o("type", "array", "items", distItem);
        if (m.cognitive) return obj(o("walkthrough", cw, "distribution", dist, "rationale", o("type", "string")));
        return obj(o("distribution", dist, "rationale", o("type", "string")));
    }

    private static Map<String, Object> obj(Map<String, Object> properties) {
        return o("type", "object", "additionalProperties", false,
                "required", List.copyOf(properties.keySet()), "properties", properties);
    }

    private static Map<String, Object> o(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
