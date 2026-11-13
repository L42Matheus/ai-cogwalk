package br.edu.ifpb.aicogwalk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do AI-CogWalk (prefixo {@code aicogwalk}).
 */
@ConfigurationProperties(prefix = "aicogwalk")
public record AppProperties(
        String storageDir,
        Browser browser,
        Llm llm,
        DemoPortal demoPortal,
        boolean seed,
        Nav nav) {

    public record Browser(
            String executablePath,
            boolean headless,
            int viewportWidth,
            int viewportHeight,
            int stepDelayMs,
            int timeoutMs,
            int maxSteps,
            int maxElements,
            String userAgent) {}

    /**
     * @param provider    avaliador padrão: {@code claude} ou {@code offline}
     * @param cacheMode   {@code off}, {@code read-write} ou {@code replay}
     */
    public record Llm(
            String provider,
            String model,
            String effort,
            long maxTokens,
            String cacheDir,
            String cacheMode) {}

    public record DemoPortal(boolean enabled, String path) {}

    /**
     * Módulo de predição de escolhas de navegação (AI-CogWalk-NAV).
     *
     * @param kuricArchiveUrl  arquivo .zip do repositório público de Kuric et al. (2025) num commit fixo
     * @param kuricRevision    commit fixado do dataset
     * @param kuricLocalDir    diretório local já extraído (opcional; evita o download)
     * @param embeddingsUrl    pacote .tar.gz com model.onnx e vocab.txt (all-MiniLM-L6-v2)
     * @param embeddingsSha256 hash esperado do pacote de embeddings (vazio = não confere)
     * @param samples          amostras por estado nas condições B1-S e B1-CW-S
     * @param coverageTau      limiar de probabilidade prevista para a métrica de cobertura
     * @param effort           esforço do modelo nas condições B1
     * @param parallel         chamadas simultâneas ao LLM numa rodada
     * @param maxCalls         teto de chamadas pagas por rodada
     */
    public record Nav(
            String kuricArchiveUrl,
            String kuricRevision,
            String kuricLocalDir,
            String embeddingsUrl,
            String embeddingsSha256,
            int samples,
            double coverageTau,
            String effort,
            long maxTokens,
            int parallel,
            int maxCalls) {}
}
