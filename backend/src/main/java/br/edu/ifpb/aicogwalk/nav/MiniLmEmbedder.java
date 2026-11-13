package br.edu.ifpb.aicogwalk.nav;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Embeddings de sentença (all-MiniLM-L6-v2 em ONNX): média dos estados finais ponderada pela
 * máscara de atenção e normalização L2, como no sentence-transformers.
 */
public class MiniLmEmbedder implements AutoCloseable {
    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final WordPieceTokenizer tokenizer;
    private final boolean needsTokenTypes;

    public MiniLmEmbedder(Path modelDir) {
        try {
            OrtSession.SessionOptions so = new OrtSession.SessionOptions();
            so.setIntraOpNumThreads(2);
            this.session = env.createSession(modelDir.resolve("model.onnx").toString(), so);
            this.needsTokenTypes = session.getInputNames().contains("token_type_ids");
        } catch (OrtException e) {
            throw new IllegalStateException("Não foi possível carregar o modelo de embeddings.", e);
        }
        this.tokenizer = new WordPieceTokenizer(modelDir.resolve("vocab.txt"), 256);
    }

    public synchronized float[] embed(String text) {
        long[] ids = tokenizer.encode(text);
        long[] mask = new long[ids.length];
        java.util.Arrays.fill(mask, 1L);
        long[] types = new long[ids.length];
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(env, new long[][]{ids}));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, new long[][]{mask}));
            if (needsTokenTypes) inputs.put("token_type_ids", OnnxTensor.createTensor(env, new long[][]{types}));
            try (OrtSession.Result r = session.run(inputs)) {
                float[][][] hidden = (float[][][]) r.get(0).getValue();
                int dim = hidden[0][0].length;
                float[] mean = new float[dim];
                for (float[] tok : hidden[0]) for (int d = 0; d < dim; d++) mean[d] += tok[d];
                double norm = 0;
                for (int d = 0; d < dim; d++) { mean[d] /= hidden[0].length; norm += mean[d] * mean[d]; }
                norm = Math.max(Math.sqrt(norm), 1e-12);
                for (int d = 0; d < dim; d++) mean[d] /= (float) norm;
                return mean;
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Falha ao calcular embedding.", e);
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    public static double cosine(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException ignored) {
            // nada a fazer
        }
    }
}
