package br.edu.ifpb.aicogwalk.nav;

/**
 * Condições experimentais do AI-CogWalk-NAV.
 *
 * <p>B1 e B1-CW pedem ao modelo, numa única chamada, a distribuição de escolhas de uma população de
 * iniciantes. B1-S e B1-CW-S simulam participantes independentes (uma escolha por chamada) e
 * estimam a distribuição pelas frequências, no espírito do protocolo de "What Would GPT Click".
 */
public enum NavMethod {
    UNIFORM(false, false, false, "Referência: todas as opções com a mesma probabilidade"),
    B0(false, false, false, "Similaridade semântica tarefa × rótulo (embeddings MiniLM), softmax com temperatura ajustada no desenvolvimento"),
    B1(true, false, false, "LLM, uma chamada, distribuição declarada para a população de iniciantes"),
    B1_CW(true, true, false, "B1 com princípios do percurso cognitivo aplicáveis à primeira escolha (Q1–Q3)"),
    B1_S(true, false, true, "LLM como participante simulado, N chamadas independentes, uma escolha por chamada"),
    B1_CW_S(true, true, true, "B1-S com os princípios do percurso cognitivo");

    public final boolean llm;
    public final boolean cognitive;
    public final boolean sampled;
    public final String description;

    NavMethod(boolean llm, boolean cognitive, boolean sampled, String description) {
        this.llm = llm;
        this.cognitive = cognitive;
        this.sampled = sampled;
        this.description = description;
    }
}
