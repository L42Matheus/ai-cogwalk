package br.edu.ifpb.aicogwalk.evaluation;

import br.edu.ifpb.aicogwalk.config.JsonSupport;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Avaliador baseado no Claude (Messages API) com imagem da tela e saída JSON estruturada.
 */
public class ClaudeEvaluator implements CognitiveEvaluator {

    private final String model;
    private final String effort;
    private final long maxTokens;
    private final ResponseCache cache;
    private volatile AnthropicClient client;

    public ClaudeEvaluator(String model, String effort, long maxTokens, ResponseCache cache) {
        this.model = model;
        this.effort = effort;
        this.maxTokens = maxTokens;
        this.cache = cache;
    }

    @Override public String name() { return "claude"; }

    @Override public String modelId() { return model; }

    @Override public String effort() { return effort; }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) client = AnthropicOkHttpClient.fromEnv();
            }
        }
        return client;
    }

    @Override
    public EvaluationResult evaluate(EvaluationInput in) {
        String user = PromptBuilder.user(in);
        Call call = call(PromptBuilder.SYSTEM, user, in.screenshotPng(), PromptBuilder.assessmentSchema(in.strategy()));
        StepAssessment assessment = JsonSupport.read(call.raw, StepAssessment.class);
        return new EvaluationResult(assessment, PromptBuilder.SYSTEM, user, call.key, call.raw, model,
                call.in, call.out, call.millis, call.cacheHit);
    }

    @Override
    public Optional<VerificationVerdict> verify(ProposedAlert alert, EvaluationInput context) {
        String user = PromptBuilder.verifyUser(alert, context);
        Call call = call(PromptBuilder.VERIFY_SYSTEM, user, context.screenshotPng(), PromptBuilder.VERIFY_SCHEMA);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = JsonSupport.read(call.raw, Map.class);
        return Optional.of(new VerificationVerdict(Boolean.TRUE.equals(m.get("evidenceHolds")),
                Boolean.TRUE.equals(m.get("consequential")), String.valueOf(m.get("reason")),
                call.in, call.out, call.millis));
    }

    private record Call(String key, String raw, long in, long out, long millis, boolean cacheHit) {}

    private Call call(String system, String user, byte[] png, Map<String, Object> schema) {
        String imageSha = Hashing.sha256(png);
        String key = Hashing.sha256(model, effort, system, user, imageSha, JsonSupport.write(schema));
        Optional<ResponseCache.Entry> hit = cache.get(key);
        if (hit.isPresent()) {
            ResponseCache.Entry e = hit.get();
            return new Call(key, e.raw(), e.inputTokens(), e.outputTokens(), 0, true);
        }
        if (cache.replayOnly()) {
            throw new IllegalStateException("Modo replay: resposta ausente no cache para a chave " + key);
        }
        JsonOutputFormat.Schema.Builder sb = JsonOutputFormat.Schema.builder();
        schema.forEach((k, v) -> sb.putAdditionalProperty(k, JsonValue.from(v)));
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(effort))
                        .format(JsonOutputFormat.builder().schema(sb.build()).build())
                        .build())
                .addUserMessageOfContentBlockParams(List.of(
                        ContentBlockParam.ofImage(ImageBlockParam.builder()
                                .source(Base64ImageSource.builder()
                                        .data(Base64.getEncoder().encodeToString(png))
                                        .mediaType(Base64ImageSource.MediaType.IMAGE_PNG)
                                        .build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(user).build())))
                .build();
        long t0 = System.currentTimeMillis();
        Message msg = client().messages().create(params);
        long millis = System.currentTimeMillis() - t0;
        if (msg.stopReason().isPresent() && msg.stopReason().get().equals(StopReason.REFUSAL)) {
            throw new IllegalStateException("O modelo recusou a avaliação desta etapa.");
        }
        if (msg.stopReason().isPresent() && msg.stopReason().get().equals(StopReason.MAX_TOKENS)) {
            throw new IllegalStateException("Resposta truncada pelo limite de tokens; aumente aicogwalk.llm.max-tokens.");
        }
        StringBuilder text = new StringBuilder();
        msg.content().forEach(b -> b.text().ifPresent(t -> text.append(t.text())));
        String raw = text.toString();
        long in = msg.usage().inputTokens();
        long out = msg.usage().outputTokens();
        cache.put(key, new ResponseCache.Entry(raw, in, out));
        return new Call(key, raw, in, out, millis, false);
    }
}
