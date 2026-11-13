package br.edu.ifpb.aicogwalk;

import br.edu.ifpb.aicogwalk.domain.ActionType;
import br.edu.ifpb.aicogwalk.domain.Condition;
import br.edu.ifpb.aicogwalk.domain.PromptStrategy;
import br.edu.ifpb.aicogwalk.domain.TaskStep;
import br.edu.ifpb.aicogwalk.evaluation.EvaluationInput;
import br.edu.ifpb.aicogwalk.evaluation.ExampleBank;
import br.edu.ifpb.aicogwalk.evaluation.PromptBuilder;
import br.edu.ifpb.aicogwalk.navigation.Capture;
import br.edu.ifpb.aicogwalk.navigation.NavigationPolicy;
import br.edu.ifpb.aicogwalk.navigation.TextMatch;
import br.edu.ifpb.aicogwalk.navigation.UiElement;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitTests {

    private static UiElement el(String id, String text, boolean visible, int y, boolean form) {
        return new UiElement(id, "a", "", text, "#", visible, form, 0, y, 100, 20);
    }

    @Test
    void policyAllowsOnlyListedHostsAndSubdomains() {
        NavigationPolicy p = new NavigationPolicy("ifpb.edu.br, localhost");
        assertThat(p.isAllowedUrl("https://www.ifpb.edu.br/polodeinovacao")).isTrue();
        assertThat(p.isAllowedUrl("http://localhost:8080/x")).isTrue();
        assertThat(p.isAllowedUrl("https://ifpb.edu.br.evil.com/")).isFalse();
        assertThat(p.isAllowedUrl("https://gov.br/")).isFalse();
        assertThat(p.isAllowedUrl("javascript:alert(1)")).isFalse();
        assertThat(p.allowsNavigation("POST", "https://www.ifpb.edu.br/")).isFalse();
        assertThat(p.allowsNavigation("GET", "https://www.ifpb.edu.br/")).isTrue();
        assertThatThrownBy(() -> new NavigationPolicy(" , ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policyNeverClicksInsideForms() {
        NavigationPolicy p = new NavigationPolicy("localhost");
        assertThat(p.isClickable(el("e1", "Buscar", true, 0, true))).isFalse();
        assertThat(p.isClickable(el("e2", "Editais", true, 0, false))).isTrue();
    }

    @Test
    void textMatchPrefersExactThenVisible() {
        List<UiElement> els = List.of(
                el("e1", "Editais e Concursos", true, 10, false),
                el("e2", "Editais", false, 900, false),
                el("e3", "EDITAIS", true, 300, false));
        List<UiElement> c = TextMatch.candidates(new java.util.ArrayList<>(els), "editais");
        assertThat(c).extracting(UiElement::id).containsExactly("e3", "e2");
        assertThat(TextMatch.normalize("  Edição  Nº 01 ")).isEqualTo("edicao nº 01");
    }

    private static EvaluationInput input(Condition c, PromptStrategy s, ExampleBank bank) {
        UiElement target = el("e2", "Editais", true, 40, false);
        Capture first = new Capture(0, "http://localhost/a", "Início", new TaskStep(ActionType.CLICK, "Núcleo", null),
                "e1", 1, "a.png", "a-full.png", "a.html", List.of(el("e1", "Núcleo", true, 10, false)), List.of(), Instant.now());
        Capture cur = new Capture(1, "http://localhost/b", "Núcleo", new TaskStep(ActionType.CLICK, "Editais", null),
                "e2", 1, "b.png", "b-full.png", "b.html", List.of(target), List.of("Núcleo de Inovação"), Instant.now());
        Capture after = new Capture(2, "http://localhost/c", "Editais", null, null, 0, "c.png", null, null, List.of(),
                List.of("Editais"), Instant.now());
        List<Capture> history = c.history ? List.of(first) : List.of();
        return new EvaluationInput("Achar o edital", "Iniciante", null, c, 1, 2, cur, after, history, new byte[0], 50,
                s == PromptStrategy.FEW_SHOT ? bank.select("localhost").examples() : List.of(), s);
    }

    @Test
    void promptDiffersOnlyByTheManipulatedFactor() {
        ExampleBank bank = new ExampleBank("classpath:prompt-examples/exemplos.json", 3);
        String c1 = PromptBuilder.user(input(Condition.C1, PromptStrategy.ZERO_SHOT, bank));
        String c2 = PromptBuilder.user(input(Condition.C2, PromptStrategy.ZERO_SHOT, bank));
        String fs = PromptBuilder.user(input(Condition.C2, PromptStrategy.FEW_SHOT, bank));
        String cot = PromptBuilder.user(input(Condition.C2, PromptStrategy.CHAIN_OF_THOUGHT, bank));
        assertThat(c1).doesNotContain("Histórico da navegação").contains("apenas esta tela");
        assertThat(c2).contains("Histórico da navegação").contains("Etapa 1: tela \"Início\"");
        assertThat(fs).startsWith("## Casos de referência de outros sistemas").endsWith(c2);
        assertThat(cot).contains("\"reasoning\"").doesNotContain("Casos de referência");
        assertThat(PromptBuilder.assessmentSchema(PromptStrategy.CHAIN_OF_THOUGHT).get("required"))
                .isEqualTo(List.of("reasoning", "answers", "alerts"));
        assertThat(PromptBuilder.assessmentSchema(PromptStrategy.ZERO_SHOT)).isSameAs(PromptBuilder.ASSESSMENT_SCHEMA);
    }

    @Test
    void exampleBankExcludesCasesFromTheEvaluatedSystem() {
        ExampleBank bank = new ExampleBank("classpath:prompt-examples/exemplos.json", 3);
        ExampleBank.Selection all = bank.select("ifpb.edu.br");
        assertThat(all.examples()).hasSize(3);
        ExampleBank.Selection excl = bank.select("academico-a.example");
        assertThat(excl.excludedIds()).containsExactly("IL-01", "IL-02");
        assertThat(excl.examples()).extracting(e -> e.sourceHost()).doesNotContain("academico-a.example");
    }
}
