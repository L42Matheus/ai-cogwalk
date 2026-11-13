package br.edu.ifpb.aicogwalk.evaluation;

import br.edu.ifpb.aicogwalk.domain.ActionType;
import br.edu.ifpb.aicogwalk.domain.PromptStrategy;
import br.edu.ifpb.aicogwalk.domain.TaskStep;
import br.edu.ifpb.aicogwalk.navigation.Capture;
import br.edu.ifpb.aicogwalk.navigation.UiElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Monta as instruções do percurso cognitivo. Qualquer mudança de texto exige nova {@link #VERSION},
 * registrada em cada execução.
 */
public final class PromptBuilder {
    public static final String VERSION = "cw-pt-1.0";

    public static final String SYSTEM = """
        Você é um avaliador de usabilidade que aplica o método do percurso cognitivo (cognitive walkthrough; Wharton et al., 1994) para identificar barreiras de aprendizagem de usuários iniciantes em interfaces web.

        Para a ação indicada, responda às quatro perguntas, cada uma com SIM, NAO ou INCERTO e uma justificativa curta baseada no que está na tela:
        Q1 (objetivo correto): o usuário tentará atingir o efeito que esta etapa exige?
        Q2 (ação visível): o usuário perceberá que a ação correta está disponível?
        Q3 (associação entre ação e objetivo): o usuário entenderá que essa ação leva ao que deseja?
        Q4 (progresso percebido): depois de agir, o usuário perceberá que avançou? Use a tela resultante informada.

        Regras:
        1. Avalie do ponto de vista do perfil de usuário informado. Você conhece o caminho correto; um iniciante não conhece.
        2. Separe fato observado de hipótese. "visualEvidence" descreve só o que está na tela (rótulos, posição, ausência de algo) e cita os ids dos elementos da lista (e1, e2, ...). "diagnosis" é a hipótese sobre a dificuldade do iniciante.
        3. Gere um alerta somente para pergunta com resposta NAO, com o mesmo "criterion". INCERTO não gera alerta: diga na justificativa o que precisaria ser observado em pessoas.
        4. Não invente elementos. Cite apenas ids que existem na lista. Se a evidência estiver só na imagem, descreva-a e deixe "citedElementIds" vazio.
        5. Opções parecidas, por si só, não provam um problema. Explique por que o iniciante teria dificuldade nesta tarefa.
        6. "recommendation" é uma mudança a testar, não uma certeza.
        7. "confidence" é sua estimativa, de 0 a 1, de que a dificuldade seria observada em testes com iniciantes.
        8. Escreva em português do Brasil.
        """;

    public static final Map<String, Object> ASSESSMENT_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("answers", "alerts"),
            "properties", Map.of(
                    "answers", Map.of("type", "array", "items", Map.of(
                            "type", "object", "additionalProperties", false,
                            "required", List.of("criterion", "answer", "justification"),
                            "properties", Map.of(
                                    "criterion", Map.of("type", "string", "enum", List.of("Q1", "Q2", "Q3", "Q4")),
                                    "answer", Map.of("type", "string", "enum", List.of("SIM", "NAO", "INCERTO")),
                                    "justification", Map.of("type", "string")))),
                    "alerts", Map.of("type", "array", "items", Map.of(
                            "type", "object", "additionalProperties", false,
                            "required", List.of("criterion", "visualEvidence", "diagnosis", "citedElementIds",
                                    "recommendation", "confidence"),
                            "properties", Map.of(
                                    "criterion", Map.of("type", "string", "enum", List.of("Q1", "Q2", "Q3", "Q4")),
                                    "visualEvidence", Map.of("type", "string"),
                                    "diagnosis", Map.of("type", "string"),
                                    "citedElementIds", Map.of("type", "array", "items", Map.of("type", "string")),
                                    "recommendation", Map.of("type", "string"),
                                    "confidence", Map.of("type", "number"))))));

    /** Mesmo esquema, com o campo "reasoning" obrigatório e gerado primeiro (estratégia CHAIN_OF_THOUGHT). */
    public static final Map<String, Object> ASSESSMENT_SCHEMA_COT = withReasoning();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> withReasoning() {
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        props.put("reasoning", Map.of("type", "string"));
        props.putAll((Map<String, Object>) ASSESSMENT_SCHEMA.get("properties"));
        return Map.of("type", "object", "additionalProperties", false,
                "required", List.of("reasoning", "answers", "alerts"), "properties", props);
    }

    public static Map<String, Object> assessmentSchema(PromptStrategy s) {
        return s == PromptStrategy.CHAIN_OF_THOUGHT ? ASSESSMENT_SCHEMA_COT : ASSESSMENT_SCHEMA;
    }

    static final String COT_INSTRUCTION = """
        Antes de responder, escreva em "reasoning" uma análise passo a passo: (1) o que o iniciante procura nesta tela,         (2) quais elementos visíveis competem pela atenção dele, (3) onde está a ação correta e como está rotulada,         (4) o que muda na tela resultante. Só depois responda às perguntas.""";

    public static final String VERIFY_SYSTEM = """
        Você verifica, de forma independente, alertas de usabilidade produzidos por outro avaliador.
        Responda com base apenas na captura de tela e na lista de elementos:
        - evidenceHolds: a evidência visual descrita existe de fato na tela, como descrita?
        - consequential: essa evidência torna plausível uma dificuldade real para um usuário iniciante nesta tarefa, ou é apenas um fato neutro?
        - reason: uma ou duas frases que justificam as respostas.
        Escreva em português do Brasil.
        """;

    public static final Map<String, Object> VERIFY_SCHEMA = Map.of(
            "type", "object", "additionalProperties", false,
            "required", List.of("evidenceHolds", "consequential", "reason"),
            "properties", Map.of(
                    "evidenceHolds", Map.of("type", "boolean"),
                    "consequential", Map.of("type", "boolean"),
                    "reason", Map.of("type", "string")));

    private PromptBuilder() {}

    public static String user(EvaluationInput in) {
        StringBuilder sb = new StringBuilder();
        Capture cur = in.current();
        TaskStep step = cur.action();
        if (!in.examples().isEmpty()) {
            sb.append(examples(in.examples())).append('\n');
        }
        sb.append("## Tarefa\n");
        sb.append("Objetivo do usuário: ").append(in.goal()).append('\n');
        sb.append("Perfil: ").append(in.userProfile()).append('\n');
        if (in.expectedKnowledge() != null && !in.expectedKnowledge().isBlank()) {
            sb.append("Conhecimento esperado: ").append(in.expectedKnowledge()).append('\n');
        }
        sb.append("\n## Etapa ").append(in.stepIndex() + 1).append(" de ").append(in.totalSteps()).append('\n');
        sb.append("Ação correta nesta tela: ").append(describe(step)).append('\n');
        if (cur.targetElementId() != null) {
            sb.append("Elemento-alvo: ").append(cur.targetElementId())
              .append(" (elementos com esse texto na tela: ").append(cur.targetCandidates()).append(")\n");
        }
        sb.append("URL: ").append(cur.url()).append('\n');
        sb.append("Título da página: ").append(cur.title()).append('\n');
        if (!cur.headings().isEmpty()) {
            sb.append("Títulos visíveis (h1–h3): ").append(String.join(" | ", cur.headings())).append('\n');
        }
        sb.append("A imagem anexada é a tela inicial desta etapa, sem rolagem (")
          .append("os elementos marcados \"sim\" na coluna visível aparecem nela).\n");

        sb.append("\n## Elementos interativos (id | tipo | texto | destino | visível sem rolar)\n");
        for (UiElement e : selectElements(cur, in.maxElements())) {
            sb.append(e.id()).append(" | ").append(kind(e)).append(" | ").append(e.text().isBlank() ? "(sem texto)" : e.text())
              .append(" | ").append(e.href().isBlank() ? "-" : e.href())
              .append(" | ").append(e.inViewport() ? "sim" : "não").append('\n');
        }
        if (cur.elements().size() > in.maxElements()) {
            sb.append("(lista limitada a ").append(in.maxElements()).append(" de ").append(cur.elements().size())
              .append(" elementos; priorizados os visíveis sem rolar)\n");
        }

        Capture after = in.after();
        sb.append("\n## Tela resultante após a ação (para Q4)\n");
        if (after == null) {
            sb.append("Não disponível.\n");
        } else if (step != null && step.type() == ActionType.LOCATE) {
            sb.append("A ação é localizar o item na própria tela; não há mudança de página. Em Q4, avalie se o usuário reconhece que encontrou o que procurava.\n");
        } else {
            sb.append("URL: ").append(after.url()).append('\n');
            sb.append("Título: ").append(after.title()).append('\n');
            if (!after.headings().isEmpty()) {
                sb.append("Títulos visíveis: ").append(String.join(" | ", after.headings())).append('\n');
            }
        }

        if (in.condition().history) {
            sb.append("\n## Histórico da navegação até aqui\n");
            if (in.history().isEmpty()) {
                sb.append("Esta é a primeira tela da tarefa.\n");
            }
            for (Capture h : in.history()) {
                sb.append("Etapa ").append(h.index() + 1).append(": tela \"").append(h.title()).append("\" (")
                  .append(h.url()).append("); ação: ").append(describe(h.action())).append('\n');
            }
        } else {
            sb.append("\nConsidere apenas esta tela; não há informação sobre as etapas anteriores.\n");
        }
        if (in.strategy() == PromptStrategy.CHAIN_OF_THOUGHT) {
            sb.append('\n').append(COT_INSTRUCTION).append('\n');
        }
        sb.append("\nResponda às quatro perguntas para esta ação e gere os alertas cabíveis.");
        return sb.toString();
    }

    /**
     * Seção de casos de referência (few-shot). Os casos vêm de OUTROS sistemas e servem como
     * padrão de raciocínio, não como resposta: o sistema avaliado nunca aparece aqui.
     */
    static String examples(List<FewShotExample> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Casos de referência de outros sistemas\n");
        sb.append("Use estes casos apenas como padrão de raciocínio. Eles não descrevem o sistema avaliado. ")
          .append("Há casos com e sem problema: não gere alerta só por semelhança com um caso.\n");
        int n = 1;
        for (FewShotExample ex : list) {
            sb.append("\nCaso ").append(n++).append(" (").append(ex.id()).append(", ").append(ex.criterion())
              .append(ex.problem() ? ", com problema" : ", sem problema").append(")\n");
            sb.append("Objetivo: ").append(ex.taskGoal()).append('\n');
            sb.append("Ação correta: ").append(ex.action()).append('\n');
            sb.append("Evidência na tela: ").append(ex.screenEvidence()).append('\n');
            sb.append("Comportamento: ").append(ex.observedBehavior()).append('\n');
            sb.append("Lição: ").append(ex.lesson()).append('\n');
        }
        return sb.toString();
    }

    public static String verifyUser(ProposedAlert alert, EvaluationInput in) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tarefa: ").append(in.goal()).append('\n');
        sb.append("Perfil: ").append(in.userProfile()).append('\n');
        sb.append("Ação correta nesta tela: ").append(describe(in.current().action())).append("\n\n");
        sb.append("## Alerta a verificar\n");
        sb.append("Critério: ").append(alert.criterion()).append('\n');
        sb.append("Evidência visual: ").append(alert.visualEvidence()).append('\n');
        sb.append("Elementos citados: ").append(alert.citedElementIds()).append('\n');
        sb.append("Diagnóstico: ").append(alert.diagnosis()).append("\n\n");
        sb.append("## Elementos interativos (id | texto | visível sem rolar)\n");
        for (UiElement e : selectElements(in.current(), in.maxElements())) {
            sb.append(e.id()).append(" | ").append(e.text()).append(" | ").append(e.inViewport() ? "sim" : "não").append('\n');
        }
        return sb.toString();
    }

    static List<UiElement> selectElements(Capture cur, int max) {
        List<UiElement> sorted = new ArrayList<>(cur.elements());
        String target = cur.targetElementId();
        sorted.sort(Comparator.comparing((UiElement e) -> !e.id().equals(target))
                .thenComparing(e -> !e.inViewport())
                .thenComparing(UiElement::y));
        List<UiElement> chosen = new ArrayList<>(sorted.subList(0, Math.min(max, sorted.size())));
        chosen.sort(Comparator.comparingInt(e -> Integer.parseInt(e.id().substring(1))));
        return chosen;
    }

    static String describe(TaskStep step) {
        if (step == null) return "(nenhuma; tela final)";
        String verb = step.type() == ActionType.CLICK ? "clicar em" : "localizar na tela";
        String d = verb + " \"" + step.target() + "\"";
        if (step.description() != null && !step.description().isBlank()) d += " (" + step.description() + ")";
        return d;
    }

    private static String kind(UiElement e) {
        if (!e.role().isBlank()) return e.role();
        return switch (e.tag()) {
            case "a" -> "link";
            case "button", "summary" -> "botão";
            case "input", "select", "textarea" -> "campo";
            default -> e.tag();
        };
    }
}
