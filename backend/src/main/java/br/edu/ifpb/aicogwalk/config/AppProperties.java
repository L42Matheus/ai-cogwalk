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
        boolean seed) {

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
}
