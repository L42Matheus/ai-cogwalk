package br.edu.ifpb.aicogwalk.domain;

/**
 * Estratégia de prompting, registrada em cada execução e independente da condição.
 * <ul>
 *   <li>ZERO_SHOT: instruções do percurso cognitivo, sem exemplos. Padrão em C1, C2 e C3,
 *       para que as condições difiram apenas no contexto fornecido.</li>
 *   <li>FEW_SHOT: acrescenta casos de referência observados em OUTROS sistemas
 *       (padrões humanos; desenho C3a). Nunca usa casos do sistema avaliado.</li>
 *   <li>CHAIN_OF_THOUGHT: pede análise sequencial explícita antes das respostas; usada no estudo de
 *       calibração do piloto (comparação com zero-shot e few-shot em C2).</li>
 * </ul>
 */
public enum PromptStrategy {
    ZERO_SHOT,
    FEW_SHOT,
    /** Raciocínio explícito: o modelo examina os elementos e o caminho antes de responder (campo "reasoning"). */
    CHAIN_OF_THOUGHT
}
