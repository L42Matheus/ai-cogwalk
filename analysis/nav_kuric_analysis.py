"""Análise complementar das rodadas NAV no dataset Kuric 2025.

Lê as exportações JSON das rodadas (GET /api/nav/runs/{id}/export.json) e calcula:
  1. diferença pareada de JSD entre dois métodos, com IC 95% por bootstrap de tarefas;
  2. a referência "humanos de outras variantes": para cada estado, a distribuição agregada das
     escolhas humanas da MESMA tarefa nas OUTRAS variantes. Não é um preditor (usa dados humanos),
     mas mostra quanto da distribuição é explicado pela tarefa, independentemente da apresentação.

Uso: python3 nav_kuric_analysis.py run_uniform.json run_b0.json
"""
import json
import math
import random
import sys
from collections import defaultdict

SEED = 20261113
REPS = 2000


def norm(ids, raw):
    s = sum(max(0.0, float(raw.get(i, 0))) for i in ids)
    return {i: (max(0.0, float(raw.get(i, 0))) / s if s > 0 else 1.0 / len(ids)) for i in ids}


def jsd(p, q):
    d = 0.0
    for k in p:
        a, b = p[k], q.get(k, 0.0)
        m = (a + b) / 2
        if a > 0:
            d += 0.5 * a * math.log2(a / m)
        if b > 0:
            d += 0.5 * b * math.log2(b / m)
    return max(0.0, min(1.0, d))


def cluster_ci(by_task):
    clusters = [v for v in by_task.values() if v]
    flat = [x for v in clusters for x in v]
    mean = sum(flat) / len(flat)
    rnd = random.Random(SEED)
    means = []
    for _ in range(REPS):
        pick = [clusters[rnd.randrange(len(clusters))] for _ in clusters]
        f = [x for v in pick for x in v]
        means.append(sum(f) / len(f))
    means.sort()
    return mean, means[int(0.025 * REPS)], means[int(0.975 * REPS) - 1]


def load(path):
    d = json.load(open(path))
    return d["method"], {p["state"]["key"]: p for p in d["predictions"]}


def main(a_path, b_path):
    a_name, a = load(a_path)
    b_name, b = load(b_path)
    for split in ("TEST", "DEV"):
        diff = defaultdict(list)
        for k, pa in a.items():
            st = pa["state"]
            if st["split"] != split:
                continue
            ids = [o["id"] for o in st["options"]]
            h = norm(ids, st["humanCounts"])
            diff[st["task"]].append(jsd(h, norm(ids, b[k]["distribution"])) - jsd(h, norm(ids, pa["distribution"])))
        m, lo, hi = cluster_ci(diff)
        print(f"{split}: JSD({b_name}) - JSD({a_name}) = {m:.3f} [{lo:.3f}; {hi:.3f}]")

    # Referência: humanos da mesma tarefa nas outras variantes.
    states = [p["state"] for p in a.values()]
    by_task = defaultdict(list)
    for st in states:
        by_task[st["task"]].append(st)
    for split in ("TEST", "DEV"):
        for scope in ("ALL", "TEXT", "VISUAL"):
            vals = defaultdict(list)
            for st in states:
                if st["split"] != split or (scope != "ALL" and st["modality"] != scope):
                    continue
                ids = [o["id"] for o in st["options"]]
                pooled = defaultdict(int)
                for other in by_task[st["task"]]:
                    if other["variant"] != st["variant"]:
                        for i, c in other["humanCounts"].items():
                            pooled[i] += c
                vals[st["task"]].append(jsd(norm(ids, st["humanCounts"]), norm(ids, pooled)))
            m, lo, hi = cluster_ci(vals)
            print(f"humanos de outras variantes, {split}|{scope}: JSD = {m:.3f} [{lo:.3f}; {hi:.3f}]")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
