package br.edu.ifpb.aicogwalk.evaluation;

import br.edu.ifpb.aicogwalk.config.JsonSupport;
import br.edu.ifpb.aicogwalk.domain.ActionType;
import br.edu.ifpb.aicogwalk.domain.Answer;
import br.edu.ifpb.aicogwalk.domain.Criterion;
import br.edu.ifpb.aicogwalk.navigation.Capture;
import br.edu.ifpb.aicogwalk.navigation.TextMatch;
import br.edu.ifpb.aicogwalk.navigation.UiElement;
import java.util.*;

/**
 * Avaliador heurístico determinístico, sem LLM. Serve para testes automatizados,
 * demonstrações sem chave de API e para exercitar o pipeline. NÃO é o método da pesquisa.
 */
public class OfflineEvaluator implements CognitiveEvaluator {
    public static final String VERSION = "heuristica-offline-1.0";

    private static final Set<String> STOP = Set.of("para", "como", "uma", "um", "de", "do", "da", "dos", "das",
            "o", "a", "os", "as", "e", "em", "no", "na", "nos", "nas", "por", "com", "que", "ao", "aos",
            "encontrar", "localizar", "obter", "emitir", "acessar", "procurar", "usuario", "seu", "sua");

    @Override public String name() { return "offline"; }

    @Override public String modelId() { return VERSION; }

    @Override public String promptVersion() { return VERSION; }

    @Override
    public EvaluationResult evaluate(EvaluationInput in) {
        Capture cur = in.current();
        UiElement target = cur.elements().stream()
                .filter(e -> e.id().equals(cur.targetElementId())).findFirst().orElse(null);
        Set<String> goalWords = keywords(in.goal());
        List<QuestionAnswer> answers = new ArrayList<>();
        List<ProposedAlert> alerts = new ArrayList<>();

        answers.add(new QuestionAnswer(Criterion.Q1, Answer.SIM,
                "Heurística: assume-se que o usuário mantém o objetivo da tarefa."));

        if (target != null && !target.inViewport()) {
            answers.add(new QuestionAnswer(Criterion.Q2, Answer.NAO, "O alvo não aparece sem rolar a página."));
            alerts.add(new ProposedAlert(Criterion.Q2,
                    "O elemento " + target.id() + " (\"" + target.text() + "\") está abaixo da área visível inicial.",
                    "O iniciante pode não perceber a opção sem rolar a página.",
                    List.of(target.id()), "Testar a opção em posição visível sem rolagem.", 0.5));
        } else {
            answers.add(new QuestionAnswer(Criterion.Q2, target == null ? Answer.INCERTO : Answer.SIM,
                    target == null ? "Alvo não identificado." : "O alvo aparece sem rolar."));
        }

        if (target != null) {
            Set<String> tw = keywords(target.text());
            boolean shares = tw.stream().anyMatch(goalWords::contains);
            List<UiElement> competitors = cur.elements().stream()
                    .filter(e -> !e.id().equals(target.id()) && e.inViewport())
                    .filter(e -> keywords(e.text()).stream().anyMatch(goalWords::contains))
                    .toList();
            if (!shares && in.current().action().type() == ActionType.CLICK && !competitors.isEmpty()) {
                answers.add(new QuestionAnswer(Criterion.Q3, Answer.NAO,
                        "O rótulo do alvo não contém termos do objetivo, mas outras opções contêm."));
                List<String> cited = new ArrayList<>();
                cited.add(target.id());
                competitors.stream().limit(3).forEach(c -> cited.add(c.id()));
                alerts.add(new ProposedAlert(Criterion.Q3,
                        "O alvo \"" + target.text() + "\" não usa termos do objetivo; opções visíveis como "
                                + quote(competitors) + " usam.",
                        "O iniciante pode seguir uma opção que parece mais próxima do objetivo do que o caminho correto.",
                        cited, "Testar rótulos que nomeiem o que o usuário procura.", 0.4));
            } else if (shares && competitors.size() >= 2) {
                answers.add(new QuestionAnswer(Criterion.Q3, Answer.INCERTO,
                        "Várias opções visíveis usam termos do objetivo; observar se iniciantes hesitam."));
            } else {
                answers.add(new QuestionAnswer(Criterion.Q3, Answer.SIM, "O rótulo do alvo se relaciona ao objetivo."));
            }
        } else {
            answers.add(new QuestionAnswer(Criterion.Q3, Answer.INCERTO, "Alvo não identificado."));
        }

        Capture after = in.after();
        if (after != null && target != null && cur.action().type() == ActionType.CLICK) {
            String afterText = TextMatch.normalize(after.title() + " " + String.join(" ", after.headings()));
            boolean echoed = keywords(target.text()).stream().anyMatch(afterText::contains);
            answers.add(new QuestionAnswer(Criterion.Q4, echoed ? Answer.SIM : Answer.INCERTO,
                    echoed ? "A tela seguinte repete o rótulo escolhido." : "A tela seguinte não repete o rótulo escolhido."));
        } else {
            answers.add(new QuestionAnswer(Criterion.Q4, Answer.INCERTO, "Sem tela resultante comparável."));
        }

        StepAssessment a = new StepAssessment(null, answers, alerts);
        String raw = JsonSupport.write(a);
        String user = "offline:" + cur.url() + "#" + cur.index();
        return new EvaluationResult(a, VERSION, user, Hashing.sha256(VERSION, user), raw, VERSION, 0, 0, 0, false);
    }

    static Set<String> keywords(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String w : TextMatch.normalize(text).split("[^a-z0-9]+")) {
            if (w.length() >= 4 && !STOP.contains(w)) out.add(w.length() > 6 ? w.substring(0, 6) : w);
        }
        return out;
    }

    private static String quote(List<UiElement> els) {
        StringJoiner j = new StringJoiner(", ");
        els.stream().limit(3).forEach(e -> j.add(e.id() + " \"" + e.text() + "\""));
        return j.toString();
    }
}
