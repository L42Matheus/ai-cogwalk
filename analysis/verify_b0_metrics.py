"""Conferência independente (Python) do B0 e das métricas calculadas pelo backend Java.

Recalcula, a partir da exportação JSON de uma rodada B0 e do modelo all-MiniLM-L6-v2 (ONNX):
  - embeddings com o tokenizador oficial (biblioteca `tokenizers`) e onnxruntime;
  - a temperatura τ ajustada nos estados DEV (mesma grade log-espaçada do método);
  - a distribuição prevista de cada estado;
  - as métricas por estado (JSD via scipy, cobertura, top-1, omissões, entropias, log-loss, Brier).
Imprime a maior diferença absoluta entre Python e Java para cada grandeza.

Uso: python3 -I verify_b0_metrics.py <dir do modelo onnx> <export B0.json> [<export UNIFORM.json>]
"""
import json
import math
import sys

import numpy as np
import onnxruntime as ort
from scipy.spatial.distance import jensenshannon
from tokenizers import Tokenizer

TAU_COV = 0.05
EPS = 0.01
RELEVANT = 0.10


def embedder(model_dir):
    tok = Tokenizer.from_file(model_dir + "/tokenizer.json")
    tok.no_padding()
    tok.enable_truncation(256)
    sess = ort.InferenceSession(model_dir + "/model.onnx")
    names = {i.name for i in sess.get_inputs()}

    def embed(text):
        e = tok.encode(text)
        ids = np.array([e.ids], dtype=np.int64)
        mask = np.array([e.attention_mask], dtype=np.int64)
        feeds = {"input_ids": ids, "attention_mask": mask}
        if "token_type_ids" in names:
            feeds["token_type_ids"] = np.zeros_like(ids)
        h = sess.run(None, feeds)[0]
        v = (h * mask[..., None]).sum(1) / mask.sum(1, keepdims=True)
        return v[0] / np.linalg.norm(v[0])
    return embed


def softmax(sims, tau):
    x = np.array(sims) / tau
    x = np.exp(x - x.max())
    return x / x.sum()


def norm(v):
    v = np.clip(np.array(v, dtype=float), 0, None)
    return v / v.sum() if v.sum() > 0 else np.full(len(v), 1 / len(v))


def jsd(p, q):
    return float(jensenshannon(p, q, base=2) ** 2)


def entropy_norm(p):
    nz = p[p > 0]
    return float(-(nz * np.log2(nz)).sum() / math.log2(len(p)))


def metrics(counts, pred):
    n = counts.sum()
    h = counts / n
    p = norm(pred)
    top_p = np.isclose(p, p.max(), atol=1e-12)
    top_h = np.isclose(h, h.max(), atol=1e-12)
    k = len(p)
    pm = (1 - EPS) * p + EPS / k
    ll = float(sum(c * -math.log2(pm[i]) for i, c in enumerate(counts) if c > 0) / n)
    brier = float(sum(c * ((p - np.eye(k)[i]) ** 2).sum() for i, c in enumerate(counts) if c > 0) / n)
    return {
        "jsd": jsd(h, p),
        "coverage": float(h[p >= TAU_COV].sum()),
        "top1": float((top_p & top_h).sum() / top_p.sum()),
        "omitted": float(((p < TAU_COV) & (h >= RELEVANT)).sum()),
        "entropyPred": entropy_norm(p),
        "entropyHuman": entropy_norm(h),
        "logLoss": ll,
        "brier": brier,
    }


def check_metrics(run, label):
    worst = {}
    for pr in run["predictions"]:
        st = pr["state"]
        ids = [o["id"] for o in st["options"]]
        counts = np.array([st["humanCounts"].get(i, 0) for i in ids], dtype=float)
        mine = metrics(counts, [pr["distribution"].get(i, 0) for i in ids])
        for k, v in mine.items():
            worst[k] = max(worst.get(k, 0), abs(v - pr["metrics"][k]))
    print(f"[{label}] maior diferença Python × Java por métrica:",
          ", ".join(f"{k}={v:.2e}" for k, v in worst.items()))
    return max(worst.values())


def main(model_dir, b0_path, uni_path=None):
    run = json.load(open(b0_path))
    embed = embedder(model_dir)
    cache = {}

    def e(t):
        if t not in cache:
            cache[t] = embed(t)
        return cache[t]

    sims = {}
    for pr in run["predictions"]:
        st = pr["state"]
        t = e(st["taskText"])
        sims[st["key"]] = [float(np.dot(t, e(o["label"] or ""))) for o in st["options"]]

    dev = [pr["state"] for pr in run["predictions"] if pr["state"]["split"] == "DEV"]
    best, best_val = 0.05, float("inf")
    log_t = math.log(0.005)
    while log_t <= math.log(2.0) + 1e-9:
        t = math.exp(log_t)
        val = np.mean([jsd(norm([s["humanCounts"].get(o["id"], 0) for o in s["options"]]), softmax(sims[s["key"]], t))
                       for s in dev])
        if val < best_val:
            best, best_val = t, val
        log_t += 0.05
    java_tau = run["summary"]["tau"]
    print(f"τ ajustado: Python={best:.6f}  Java={java_tau:.6f}  JSD média DEV={best_val:.4f}")

    max_dist = 0.0
    for pr in run["predictions"]:
        st = pr["state"]
        p = softmax(sims[st["key"]], java_tau)
        q = np.array([pr["distribution"][o["id"]] for o in st["options"]])
        max_dist = max(max_dist, float(np.abs(p - q).max()))
    print(f"maior diferença na distribuição prevista do B0: {max_dist:.2e}")
    check_metrics(run, "B0")
    if uni_path:
        check_metrics(json.load(open(uni_path)), "UNIFORM")


if __name__ == "__main__":
    main(*sys.argv[1:])
