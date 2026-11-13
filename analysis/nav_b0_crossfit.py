"""B0 com temperatura ajustada em validação cruzada de 2 partes, avaliado nas 10 tarefas.

O B0 é softmax(sim/τ). Como p ∝ exp(sim/τ0), a previsão com outra temperatura τ é
p^(τ0/τ) renormalizada, de modo que a exportação da rodada B0 (τ0 ajustado no DEV) basta para
reajustar τ em cada parte sem recalcular embeddings.

Partes: A = tarefas pares (DEV), B = tarefas ímpares (TEST). τ_A é ajustado em A e aplicado em B;
τ_B é ajustado em B e aplicado em A. Cada estado é avaliado com a temperatura ajustada na OUTRA parte.

Saídas: JSD do B0 com validação cruzada e do UNIFORM nas 10 tarefas, a diferença pareada com IC 95%
por bootstrap de tarefas (2.000 réplicas, semente 20261113) e o teste exato de sinais por tarefa
(troca de sinal das médias por tarefa), com 5 e com 10 tarefas.

Uso: python3 nav_b0_crossfit.py run_b0.json
"""
import itertools
import json
import math
import sys
from collections import defaultdict

from nav_kuric_analysis import cluster_ci, jsd, norm

GRID = [0.005 * math.exp(0.05 * k) for k in range(0, 200) if 0.005 * math.exp(0.05 * k) <= 2.0 + 1e-12]


def retemper(p, tau0, tau):
    w = {k: (v ** (tau0 / tau) if v > 0 else 0.0) for k, v in p.items()}
    s = sum(w.values())
    return {k: v / s for k, v in w.items()}


def mean_jsd(states, tau0, tau):
    vals = [jsd(s["h"], retemper(s["p"], tau0, tau)) for s in states]
    return sum(vals) / len(vals)


def fit(states, tau0):
    return min(GRID, key=lambda t: mean_jsd(states, tau0, t))


def sign_flip_p(task_means):
    obs = abs(sum(task_means))
    hits = total = 0
    for signs in itertools.product((1, -1), repeat=len(task_means)):
        total += 1
        if abs(sum(s * x for s, x in zip(signs, task_means))) >= obs - 1e-12:
            hits += 1
    return hits / total


def main(path):
    d = json.load(open(path))
    tau0 = d["summary"]["tau"]
    states = []
    for pr in d["predictions"]:
        st = pr["state"]
        ids = [o["id"] for o in st["options"]]
        states.append({"key": st["key"], "task": st["task"], "split": st["split"], "ids": ids,
                       "h": norm(ids, st["humanCounts"]), "p": norm(ids, pr["distribution"])})
    a = [s for s in states if s["split"] == "DEV"]
    b = [s for s in states if s["split"] == "TEST"]
    tau_a, tau_b = fit(a, tau0), fit(b, tau0)
    print(f"tau0 da rodada = {tau0:.6f}; tau ajustado em DEV = {tau_a:.6f}; tau ajustado em TEST = {tau_b:.6f}")

    diff = defaultdict(list)
    b0v = defaultdict(list)
    unv = defaultdict(list)
    for s in states:
        tau = tau_b if s["split"] == "DEV" else tau_a
        j_b0 = jsd(s["h"], retemper(s["p"], tau0, tau))
        j_un = jsd(s["h"], {i: 1 / len(s["ids"]) for i in s["ids"]})
        b0v[s["task"]].append(j_b0)
        unv[s["task"]].append(j_un)
        diff[s["task"]].append(j_b0 - j_un)

    for name, vals in (("B0 (validação cruzada)", b0v), ("UNIFORM", unv)):
        m, lo, hi = cluster_ci(vals)
        print(f"10 tarefas, {name}: JSD = {m:.3f} [{lo:.3f}; {hi:.3f}]")
    m, lo, hi = cluster_ci(diff)
    print(f"10 tarefas, B0 - UNIFORM: {m:.3f} [{lo:.3f}; {hi:.3f}]")
    task_means = {t: sum(v) / len(v) for t, v in diff.items()}
    neg = sum(1 for v in task_means.values() if v < 0)
    print(f"tarefas em que o B0 ficou abaixo do UNIFORM: {neg} de {len(task_means)}")
    print(f"teste exato de sinais (bilateral), 10 tarefas: p = {sign_flip_p(list(task_means.values())):.4f}")
    test_means = [v for t, v in task_means.items() if int(t) % 2 == 1]
    print(f"teste exato de sinais (bilateral), só as 5 do TEST: p = {sign_flip_p(test_means):.4f}"
          f" (mínimo possível com 5 tarefas: {2 / 2 ** 5:.4f})")
    for split in ("DEV", "TEST"):
        sub = {t: v for t, v in diff.items() if (int(t) % 2 == 0) == (split == "DEV")}
        m, lo, hi = cluster_ci(sub)
        print(f"{split} (temperatura da outra parte), B0 - UNIFORM: {m:.3f} [{lo:.3f}; {hi:.3f}]")


if __name__ == "__main__":
    main(sys.argv[1])
