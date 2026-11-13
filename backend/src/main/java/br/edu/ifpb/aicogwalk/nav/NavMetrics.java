package br.edu.ifpb.aicogwalk.nav;

import java.util.*;

/**
 * Métricas de fidelidade entre a distribuição prevista e as escolhas humanas observadas num estado.
 * Logaritmos na base 2 (JSD em [0, 1]).
 */
public final class NavMetrics {
    private NavMetrics() {}

    /** Probabilidade mínima misturada à prevista antes da log-loss (1% de massa uniforme). */
    public static final double LOGLOSS_EPS = 0.01;
    /** Uma opção escolhida por pelo menos 10% das pessoas é "relevante" para a contagem de omissões. */
    public static final double RELEVANT_HUMAN_SHARE = 0.10;

    public record StateMetrics(int humanN, double top1, double jsd, double coverage, int omitted,
                               double entropyPred, double entropyHuman, double logLoss, double brier,
                               double samplingFloorJsd) {}

    public static Map<String, Double> normalize(List<String> ids, Map<String, ? extends Number> raw) {
        Map<String, Double> out = new LinkedHashMap<>();
        double s = 0;
        for (String id : ids) {
            Number n = raw.get(id);
            double v = n == null ? 0 : Math.max(0, n.doubleValue());
            if (Double.isNaN(v) || Double.isInfinite(v)) v = 0;
            out.put(id, v);
            s += v;
        }
        if (s <= 0) {
            for (String id : ids) out.put(id, 1.0 / ids.size());
        } else {
            for (String id : ids) out.put(id, out.get(id) / s);
        }
        return out;
    }

    public static double jsd(Map<String, Double> p, Map<String, Double> q) {
        double d = 0;
        for (String k : p.keySet()) {
            double a = p.get(k), b = q.getOrDefault(k, 0.0), m = (a + b) / 2;
            if (a > 0) d += 0.5 * a * log2(a / m);
            if (b > 0) d += 0.5 * b * log2(b / m);
        }
        return Math.max(0, Math.min(1, d));
    }

    public static double entropyNorm(Map<String, Double> p) {
        if (p.size() <= 1) return 0;
        double h = 0;
        for (double v : p.values()) if (v > 0) h -= v * log2(v);
        return h / log2(p.size());
    }

    public static StateMetrics compute(List<String> ids, Map<String, Integer> counts, Map<String, Double> pred,
                                       double tau, long seed) {
        int n = counts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> h = normalize(ids, counts);
        Map<String, Double> p = normalize(ids, pred);
        double maxH = Collections.max(h.values());
        double maxP = Collections.max(p.values());
        // Concordância top-1 esperada com desempate aleatório: fração das opções empatadas no topo da
        // previsão que também estão no topo humano. Uma previsão uniforme não "acerta" por empate.
        int topP = 0, both = 0;
        for (String id : ids) {
            if (p.get(id) >= maxP - 1e-12) {
                topP++;
                if (h.get(id) >= maxH - 1e-12) both++;
            }
        }
        double agree = (double) both / topP;
        double cov = 0;
        int omitted = 0;
        for (String id : ids) {
            if (p.get(id) >= tau) cov += h.get(id);
            else if (h.get(id) >= RELEVANT_HUMAN_SHARE) omitted++;
        }
        double ll = 0, brier = 0;
        int k = ids.size();
        for (String y : ids) {
            int c = counts.getOrDefault(y, 0);
            if (c == 0) continue;
            double pm = (1 - LOGLOSS_EPS) * p.get(y) + LOGLOSS_EPS / k;
            ll += c * -log2(pm);
            double b = 0;
            for (String a : ids) {
                double t = p.get(a) - (a.equals(y) ? 1 : 0);
                b += t * t;
            }
            brier += c * b;
        }
        return new StateMetrics(n, agree, jsd(h, p), cov, omitted, entropyNorm(p), entropyNorm(h),
                n == 0 ? Double.NaN : ll / n, n == 0 ? Double.NaN : brier / n, samplingFloor(ids, h, n, seed));
    }

    /**
     * Piso amostral: JSD esperada entre a distribuição humana observada e uma nova amostra de mesmo
     * tamanho tirada dela (bootstrap). Um preditor perfeito avaliado contra n pessoas não fica,
     * em média, abaixo desse valor.
     */
    public static double samplingFloor(List<String> ids, Map<String, Double> h, int n, long seed) {
        if (n <= 1) return Double.NaN;
        Random rnd = new Random(seed);
        int reps = 400;
        double s = 0;
        double[] cum = new double[ids.size()];
        double acc = 0;
        for (int i = 0; i < ids.size(); i++) { acc += h.get(ids.get(i)); cum[i] = acc; }
        for (int r = 0; r < reps; r++) {
            Map<String, Integer> c = new HashMap<>();
            for (int j = 0; j < n; j++) {
                double u = rnd.nextDouble() * acc;
                int i = 0;
                while (i < cum.length - 1 && u > cum[i]) i++;
                c.merge(ids.get(i), 1, Integer::sum);
            }
            s += jsd(h, normalize(ids, c));
        }
        return s / reps;
    }

    static double log2(double x) { return Math.log(x) / Math.log(2); }

    /** Média com intervalo de 95% por bootstrap de agrupamentos (tarefas), semente fixa. */
    public record Agg(int states, int clusters, double mean, double lo, double hi) {}

    public static Agg clusterBootstrap(Map<String, List<Double>> byCluster, long seed) {
        List<List<Double>> clusters = new ArrayList<>();
        for (List<Double> v : byCluster.values()) {
            List<Double> ok = v.stream().filter(d -> d != null && !d.isNaN()).toList();
            if (!ok.isEmpty()) clusters.add(ok);
        }
        int states = clusters.stream().mapToInt(List::size).sum();
        if (clusters.isEmpty()) return new Agg(0, 0, Double.NaN, Double.NaN, Double.NaN);
        double mean = clusters.stream().flatMap(List::stream).mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        if (clusters.size() < 2) return new Agg(states, clusters.size(), mean, Double.NaN, Double.NaN);
        Random rnd = new Random(seed);
        int reps = 2000;
        double[] means = new double[reps];
        for (int r = 0; r < reps; r++) {
            double s = 0;
            int c = 0;
            for (int i = 0; i < clusters.size(); i++) {
                List<Double> pick = clusters.get(rnd.nextInt(clusters.size()));
                for (double d : pick) { s += d; c++; }
            }
            means[r] = s / c;
        }
        Arrays.sort(means);
        return new Agg(states, clusters.size(), mean, means[(int) (0.025 * reps)], means[(int) (0.975 * reps) - 1]);
    }
}
