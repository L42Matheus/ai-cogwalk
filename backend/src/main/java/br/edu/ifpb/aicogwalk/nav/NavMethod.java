package br.edu.ifpb.aicogwalk.nav;

/**
 * Condições experimentais do AI-CogWalk-NAV.
 *
 * <p>B1 e B1-CW pedem ao modelo, numa única chamada, a distribuição de escolhas de uma população de
 * iniciantes. B1-S e B1-CW-S simulam participantes independentes (uma escolha por chamada) e
 * estimam a distribuição pelas frequências, no espírito do protocolo de "What Would GPT Click".
 * B1-R é o controle do B1-CW: analisa cada opção antes de estimar, mas sem as perguntas do percurso
 * cognitivo. Separa o efeito de Q1–Q3 do efeito de simplesmente deliberar sobre cada opção.
 */
public enum NavMethod {
    UNIFORM(false, false, false, "Referência: todas as opções com a mesma probabilidade"),
    B0(false, false, false, "Similaridade semântica tarefa × rótulo (embeddings MiniLM), softmax com temperatura ajustada no desenvolvimento"),
    B1(true, false, false, "LLM, uma chamada, distribuição declarada para a população de iniciantes"),
    B1_CW(true, true, false, "B1 com princípios do percurso cognitivo aplicáveis à primeira escolha (Q1–Q3)"),
    B1_R(true, false, false, true, "Controle do B1-CW: análise livre de cada opção antes de estimar, sem as perguntas do percurso cognitivo"),
    B1_S(true, false, true, "LLM como participante simulado, N chamadas independentes, uma escolha por chamada"),
    B1_CW_S(true, true, true, "B1-S com os princípios do percurso cognitivo");

    public final boolean llm;
    public final boolean cognitive;
    public final boolean sampled;
    /** Pede uma análise por opção antes da estimativa (Q1–Q3 no B1-CW; análise livre no B1-R). */
    public final boolean perOption;
    public final String description;

    NavMethod(boolean llm, boolean cognitive, boolean sampled, String description) {
        this(llm, cognitive, sampled, cognitive, description);
    }

    NavMethod(boolean llm, boolean cognitive, boolean sampled, boolean perOption, String description) {
        this.llm = llm;
        this.cognitive = cognitive;
        this.sampled = sampled;
        this.perOption = perOption;
        this.description = description;
    }
}
