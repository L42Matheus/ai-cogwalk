package br.edu.ifpb.aicogwalk.nav;

import br.edu.ifpb.aicogwalk.config.JsonSupport;
import br.edu.ifpb.aicogwalk.evaluation.Hashing;
import br.edu.ifpb.aicogwalk.evaluation.ResponseCache;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Chamada a um LLM multimodal com saída JSON estruturada: Claude (SDK oficial da Anthropic) ou
 * modelos da OpenAI (HTTP), escolhidos pelo prefixo do id do modelo. Cada resposta vai para o cache
 * com uma chave que inclui o índice da amostra: repetir a execução reproduz as mesmas amostras,
 * mas amostras diferentes são chamadas independentes.
 */
public class NavLlmClient {

    /** {@code failure} é "refusal" ou "max_tokens" quando o modelo não entregou resposta utilizável (não vai para o cache). */
    public record Result(String raw, long inputTokens, long outputTokens, long millis, boolean cacheHit, String key, String failure) {
        public boolean failed() { return failure != null; }
    }

    /**
     * Preços de referência em US$ por milhão de tokens (entrada, saída), usados só para estimar o custo
     * registrado na rodada. O valor cobrado de fato deve ser conferido no painel de faturamento do provedor.
     */
    static final Map<String, double[]> PRICES = Map.of(
            "claude-opus-5-5", new double[]{4.0, 20.0},
            "gpt-4.1", new double[]{2.0, 8.0});
    static final String PRICE_NOTE = "Estimativa por preço de tabela (claude-opus-5-5: 4/20; gpt-4.1: 2/8 US$ por MTok, "
            + "a conferir). O custo real é o do painel de faturamento do provedor.";

    static double[] pricePerMTok(String model) { return model == null ? null : PRICES.get(model); }

    /** Tentativas por amostra quando o modelo recusa ou trunca a resposta. */
    static final int ATTEMPTS = 3;

    private final String model;
    private final String effort;
    private final long maxTokens;
    private final ResponseCache cache;
    private volatile AnthropicClient anthropic;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public NavLlmClient(String model, String effort, long maxTokens, ResponseCache cache) {
        this.model = model;
        this.effort = effort;
        this.maxTokens = maxTokens;
        this.cache = cache;
    }

    public static boolean isOpenAi(String model) {
        return model != null && (model.startsWith("gpt-") || model.matches("^o\\d.*"));
    }

    public static boolean configured(String model) {
        String env = isOpenAi(model) ? "OPENAI_API_KEY" : "ANTHROPIC_API_KEY";
        String v = System.getenv(env);
        return v != null && !v.isBlank();
    }

    public String provider() { return isOpenAi(model) ? "openai" : "anthropic"; }

    public Result call(String system, String user, byte[] png, Map<String, Object> schema, int sampleIndex) {
        String key = Hashing.sha256(provider(), model, String.valueOf(effort), system, user,
                png == null ? "" : Hashing.sha256(png), JsonSupport.write(schema), "amostra-" + sampleIndex);
        Optional<ResponseCache.Entry> hit = cache.get(key);
        if (hit.isPresent()) {
            return new Result(hit.get().raw(), hit.get().inputTokens(), hit.get().outputTokens(), 0, true, key, null);
        }
        if (cache.replayOnly()) throw new IllegalStateException("Modo replay: resposta ausente no cache (" + key + ")");
        long tin = 0, tout = 0, ms = 0;
        Result r = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            r = isOpenAi(model) ? openai(system, user, png, schema, key) : claude(system, user, png, schema, key);
            tin += r.inputTokens();
            tout += r.outputTokens();
            ms += r.millis();
            if (!r.failed()) break;
        }
        r = new Result(r.raw(), tin, tout, ms, false, key, r.failure());
        if (!r.failed()) cache.put(key, new ResponseCache.Entry(r.raw(), r.inputTokens(), r.outputTokens()));
        return r;
    }

    private AnthropicClient anthropic() {
        if (anthropic == null) {
            synchronized (this) {
                if (anthropic == null) anthropic = AnthropicOkHttpClient.fromEnv();
            }
        }
        return anthropic;
    }

    private Result claude(String system, String user, byte[] png, Map<String, Object> schema, String key) {
        JsonOutputFormat.Schema.Builder sb = JsonOutputFormat.Schema.builder();
        schema.forEach((k, v) -> sb.putAdditionalProperty(k, JsonValue.from(v)));
        OutputConfig.Builder oc = OutputConfig.builder().format(JsonOutputFormat.builder().schema(sb.build()).build());
        if (effort != null && !effort.isBlank()) oc.effort(OutputConfig.Effort.of(effort));
        List<ContentBlockParam> blocks = new ArrayList<>();
        if (png != null) {
            blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder().data(Base64.getEncoder().encodeToString(png))
                            .mediaType(Base64ImageSource.MediaType.IMAGE_PNG).build()).build()));
        }
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(user).build()));
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model).maxTokens(maxTokens).system(system).outputConfig(oc.build())
                .addUserMessageOfContentBlockParams(blocks).build();
        long t0 = System.currentTimeMillis();
        Message msg = withRetry(() -> anthropic().messages().create(params));
        long ms = System.currentTimeMillis() - t0;
        String failure = null;
        if (msg.stopReason().isPresent() && msg.stopReason().get().equals(StopReason.REFUSAL)) failure = "refusal";
        else if (msg.stopReason().isPresent() && msg.stopReason().get().equals(StopReason.MAX_TOKENS)) failure = "max_tokens";
        StringBuilder text = new StringBuilder();
        msg.content().forEach(b -> b.text().ifPresent(t -> text.append(t.text())));
        return new Result(text.toString(), msg.usage().inputTokens(), msg.usage().outputTokens(), ms, false, key, failure);
    }

    @SuppressWarnings("unchecked")
    private Result openai(String system, String user, byte[] png, Map<String, Object> schema, String key) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", user));
        if (png != null) {
            content.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(png), "detail", "high")));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", content)));
        body.put("response_format", Map.of("type", "json_schema",
                "json_schema", Map.of("name", "prediction", "strict", true, "schema", schema)));
        if (model.startsWith("gpt-5") && effort != null && !effort.isBlank()) body.put("reasoning_effort", effort);
        String json = JsonSupport.write(body);
        long t0 = System.currentTimeMillis();
        String resp = withRetry(() -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 429 || r.statusCode() >= 500) throw new RetryableException("HTTP " + r.statusCode());
            if (r.statusCode() != 200) throw new IllegalStateException("OpenAI HTTP " + r.statusCode() + ": " + abbreviate(r.body()));
            return r.body();
        });
        long ms = System.currentTimeMillis() - t0;
        Map<String, Object> m = JsonSupport.read(resp, Map.class);
        Map<String, Object> choice = ((List<Map<String, Object>>) m.get("choices")).get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        String failure = message.get("refusal") != null ? "refusal"
                : "length".equals(choice.get("finish_reason")) ? "max_tokens" : null;
        Map<String, Object> usage = (Map<String, Object>) m.getOrDefault("usage", Map.of());
        long in = ((Number) usage.getOrDefault("prompt_tokens", 0)).longValue();
        long out = ((Number) usage.getOrDefault("completion_tokens", 0)).longValue();
        return new Result(String.valueOf(message.get("content")), in, out, ms, false, key, failure);
    }

    static class RetryableException extends RuntimeException {
        RetryableException(String m) { super(m); }
    }

    interface Attempt<T> { T run() throws Exception; }

    private static <T> T withRetry(Attempt<T> a) {
        long wait = 2000;
        for (int i = 0; ; i++) {
            try {
                return a.run();
            } catch (Exception e) {
                boolean retry = e instanceof RetryableException
                        || e.getClass().getSimpleName().contains("RateLimit")
                        || e.getClass().getSimpleName().contains("InternalServer")
                        || e.getClass().getSimpleName().contains("Overloaded")
                        || e instanceof java.io.IOException;
                if (!retry || i >= 5) {
                    if (e instanceof RuntimeException re) throw re;
                    throw new IllegalStateException(e);
                }
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ie);
                }
                wait = Math.min(wait * 2, 60000);
            }
        }
    }

    private static String abbreviate(String s) { return s == null ? "" : s.length() > 400 ? s.substring(0, 400) + "…" : s; }
}
