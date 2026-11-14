"""Análises complementares do AI-CogWalk-NAV sobre a rodada B0 (sem chamadas a LLMs).

Todas usam só a exportação da rodada B0 (e, opcionalmente, as exportações do piloto):

 1. B0 em validação cruzada nas 10 tarefas: métricas, intervalo t de Student sobre as médias por tarefa,
    sensibilidade ao modo de escolher a temperatura (deixando uma tarefa de fora; temperatura fixa).
 2. Poder do teste exato de troca de sinal com 10 tarefas e cobertura real do bootstrap percentil.
 3. Referência humana entre variantes comparada ao valor esperado se a apresentação não tivesse efeito
    (permutação das escolhas entre as variantes, dentro de cada tarefa).
 4. Previsões "de colapso": toda a massa na opção mais escolhida pelas pessoas do próprio estado.
 5. Erros do B0 por tarefa, contra as escolhas humanas agregadas nas seis variantes.
 6. Relação entre a entropia humana de cada estado e a JSD do B0, do UNIFORM e da diferença.
 7. Variantes textuais agregadas (mesma entrada do modelo, n de cerca de 90).
 8. Piloto: efeito de reamostrar 30 escolhas da distribuição declarada e probabilidades iguais ao limiar.

Uso: python3 nav_analises_complementares.py b0.json [piloto1.json piloto2.json ...]
Requer numpy (só para as simulações dos itens 2 e 8).
"""
import itertools
import json
import math
import random
import sys
from collections import defaultdict

import numpy as np

from nav_b0_crossfit import GRID, fit, retemper, sign_flip_p
from nav_kuric_analysis import cluster_ci, jsd, norm

SEED = 20261113
T9 = 2.262157  # quantil 0,975 da t de Student com 9 graus de liberdade
TEXT = ("TP", "TC", "TO")


def entropy(p):
    return -sum(v * math.log2(v) for v in p.values() if v > 0) / math.log2(len(p))


def rank(v):
    order = sorted(range(len(v)), key=lambda i: v[i])
    r = [0.0] * len(v)
    i = 0
    while i < len(v):
        j = i
        while j + 1 < len(v) and v[order[j + 1]] == v[order[i]]:
            j += 1
        for k in range(i, j + 1):
            r[order[k]] = (i + j) / 2 + 1
        i = j + 1
    return r


def spearman(a, b):
    ra, rb = rank(a), rank(b)
    ma, mb = sum(ra) / len(ra), sum(rb) / len(rb)
    num = sum((x - ma) * (y - mb) for x, y in zip(ra, rb))
    return num / math.sqrt(sum((x - ma) ** 2 for x in ra) * sum((y - mb) ** 2 for y in rb))


def load(path):
    d = json.load(open(path))
    tau0 = d["summary"].get("tau")
    states = []
    for pr in d["predictions"]:
        st = pr["state"]
        ids = [o["id"] for o in st["options"]]
        states.append({
            "key": st["key"], "task": int(st["task"]), "split": st["split"], "variant": st["key"].split("-")[0],
            "ids": ids, "label": {o["id"]: o["label"] for o in st["options"]},
            "counts": {i: int(st["humanCounts"].get(str(i), 0)) for i in ids},
            "h": norm(ids, st["humanCounts"]), "p": norm(ids, pr["distribution"]),
            "floor": pr["metrics"].get("samplingFloorJsd"),
        })
    return d, tau0, states


def ci(states, key, keep=lambda s: True):
    by_task = defaultdict(list)
    for s in states:
        if keep(s):
            by_task[str(s["task"])].append(key(s))
    m, lo, hi = cluster_ci(by_task)
    return f"{m:.3f} [{lo:.3f}; {hi:.3f}]"


def coverage(s, tc=0.05):
    return sum(s["h"][i] for i in s["ids"] if s["q"][i] >= tc)


def omitted(s, tc=0.05):
    return sum(1 for i in s["ids"] if s["h"][i] >= 0.10 and s["q"][i] < tc)


def top1(s):
    mq, mh = max(s["q"].values()), max(s["h"].values())
    a = {i for i in s["ids"] if abs(s["q"][i] - mq) < 1e-12}
    b = {i for i in s["ids"] if abs(s["h"][i] - mh) < 1e-12}
    return len(a & b) / len(a)


def mean_jsd(states, tau0, tau):
    return sum(jsd(s["h"], retemper(s["p"], tau0, tau)) for s in states) / len(states)


def reference_between_variants(groups):
    """Média, nas variantes de uma tarefa, da JSD entre a variante e as outras agregadas."""
    out = []
    for i, g in enumerate(groups):
        other = defaultdict(int)
        for j, g2 in enumerate(groups):
            if j != i:
                for k, v in g2.items():
                    other[k] += v
        keys = sorted(set(g) | set(other))
        n1, n2 = sum(g.values()), sum(other.values())
        out.append(jsd({k: g.get(k, 0) / n1 for k in keys}, {k: other.get(k, 0) / n2 for k in keys}))
    return sum(out) / len(out)


def main(b0_path, pilot_paths):
    _, tau0, S = load(b0_path)
    tasks = sorted({s["task"] for s in S})
    tau_even = fit([s for s in S if s["split"] == "DEV"], tau0)
    tau_odd = fit([s for s in S if s["split"] == "TEST"], tau0)
    for s in S:
        s["q"] = retemper(s["p"], tau0, tau_odd if s["split"] == "DEV" else tau_even)
        s["jb"] = jsd(s["h"], s["q"])
        s["ju"] = jsd(s["h"], {i: 1 / len(s["ids"]) for i in s["ids"]})
        s["eh"], s["eq"] = entropy(s["h"]), entropy(s["q"])

    print("== 1. B0 em validação cruzada, 10 tarefas ==")
    print(f"tau ajustado nas pares = {tau_even:.6f}; nas ímpares = {tau_odd:.6f}")
    print("B0: JSD", ci(S, lambda s: s["jb"]), "| cobertura", ci(S, coverage), "| omitidas", ci(S, omitted),
          "| top-1", ci(S, top1), "| entropia prevista", ci(S, lambda s: s["eq"]))
    print("UNIFORM: JSD", ci(S, lambda s: s["ju"]))
    print("piso amostral:", ci(S, lambda s: s["floor"]), "| entropia humana:", ci(S, lambda s: s["eh"]))
    for name, keep in (("texto", lambda s: s["variant"] in TEXT), ("visual", lambda s: s["variant"] not in TEXT)):
        print(f"{name}: B0", ci(S, lambda s: s["jb"], keep), "| UNIFORM", ci(S, lambda s: s["ju"], keep),
              "| diferença", ci(S, lambda s: s["jb"] - s["ju"], keep))
    for v in ("TP", "TC", "TO", "WTC", "WTCI", "WM"):
        g = [s for s in S if s["variant"] == v]
        print(f"  {v}: UNIFORM {sum(s['ju'] for s in g) / len(g):.3f}  B0 {sum(s['jb'] for s in g) / len(g):.3f}")
    tm = {t: sum(s["jb"] - s["ju"] for s in S if s["task"] == t) / sum(1 for s in S if s["task"] == t) for t in tasks}
    x = list(tm.values())
    m = sum(x) / len(x)
    sd = math.sqrt(sum((v - m) ** 2 for v in x) / (len(x) - 1))
    half = T9 * sd / math.sqrt(len(x))
    print("médias por tarefa (B0 - UNIFORM):", {t: round(v, 3) for t, v in tm.items()})
    print(f"média {m:.3f}; dp entre tarefas {sd:.3f}; IC t (gl=9) [{m - half:.3f}; {m + half:.3f}]; "
          f"troca de sinal p = {sign_flip_p(x):.4f}")
    loto = []
    for t in tasks:
        tau = min(GRID, key=lambda g: mean_jsd([s for s in S if s["task"] != t], tau0, g))
        te = [s for s in S if s["task"] == t]
        loto.append(mean_jsd(te, tau0, tau) - sum(s["ju"] for s in te) / len(te))
    print(f"tau escolhido deixando a tarefa de fora: média {sum(loto) / 10:+.3f}; p = {sign_flip_p(loto):.4f}; "
          f"negativas {sum(v < 0 for v in loto)}/10")
    for tau in (0.03, tau_odd, tau_even, 0.08, 0.10, 0.15):
        y = []
        for t in tasks:
            te = [s for s in S if s["task"] == t]
            y.append(mean_jsd(te, tau0, tau) - sum(s["ju"] for s in te) / len(te))
        print(f"tau fixo {tau:.4f}: média {sum(y) / 10:+.3f}; p = {sign_flip_p(y):.4f}; negativas {sum(v < 0 for v in y)}/10")
    prox = []
    for t in tasks:
        te = [s for s in S if s["task"] == t]
        prox.append(mean_jsd(te, tau0, tau_even) - mean_jsd(te, tau0, tau_odd))
    pm = sum(prox) / 10
    print(f"dois preditores quase iguais (B0 com as duas temperaturas): dp entre tarefas da diferença = "
          f"{math.sqrt(sum((v - pm) ** 2 for v in prox) / 9):.3f}")

    print("\n== 2. Poder do teste de troca de sinal e cobertura do bootstrap, 10 tarefas (simulação normal) ==")
    rng = np.random.default_rng(SEED)
    signs = np.array(list(itertools.product((1, -1), repeat=10)))
    for d_eff in (0.5, 0.75, 1.0, 1.17, 1.4):
        pv = []
        for _ in range(4000):
            z = rng.normal(d_eff, 1, 10)
            pv.append((np.abs(signs @ z) >= abs(z.sum()) - 1e-12).mean())
        pv = np.array(pv)
        print(f"efeito padronizado {d_eff}: poder com alfa 0,05 = {(pv < 0.05).mean():.2f}; "
              f"alfa 0,025 = {(pv < 0.025).mean():.2f}")
    cov_pct = cov_t = 0
    n_sim = 3000
    for _ in range(n_sim):
        z = rng.normal(0, 1, 10)
        boot = z[rng.integers(0, 10, (2000, 10))].mean(1)
        lo, hi = np.percentile(boot, [2.5, 97.5])
        cov_pct += lo <= 0 <= hi
        cov_t += abs(z.mean()) <= T9 * z.std(ddof=1) / math.sqrt(10)
    print(f"cobertura real do intervalo nominal de 95%: bootstrap percentil = {cov_pct / n_sim:.3f}; t = {cov_t / n_sim:.3f}")

    print("\n== 3. Referência humana entre variantes x valor esperado sem efeito de apresentação ==")
    random.seed(SEED)
    obs, null = [], [0.0] * 2000
    for t in tasks:
        groups = [{s["label"][i]: s["counts"][i] for i in s["ids"]} for s in S if s["task"] == t]
        obs.append(reference_between_variants(groups))
        pool = [k for g in groups for k, v in g.items() for _ in range(v)]
        sizes = [sum(g.values()) for g in groups]
        for r in range(2000):
            random.shuffle(pool)
            pos, gs = 0, []
            for n in sizes:
                c = defaultdict(int)
                for k in pool[pos:pos + n]:
                    c[k] += 1
                gs.append(c)
                pos += n
            null[r] += reference_between_variants(gs) / len(tasks)
    o = sum(obs) / len(obs)
    print(f"observado = {o:.4f}; esperado sem efeito = {sum(null) / len(null):.4f}; "
          f"p (permutação, unilateral) = {sum(v >= o for v in null) / len(null):.3f}")

    print("\n== 4. Colapso na opção mais escolhida do próprio estado (usa os dados humanos) ==")
    for s in S:
        mh = max(s["h"].values())
        modes = [i for i in s["ids"] if s["h"][i] == mh]
        s["jc"] = sum(jsd(s["h"], {k: (1.0 if k == i else 0.0) for k in s["ids"]}) for i in modes) / len(modes)
    print("JSD:", ci(S, lambda s: s["jc"]), "| cobertura (= parcela da moda):", ci(S, lambda s: max(s["h"].values())))

    print("\n== 5. Erros do B0 por tarefa, contra os humanos agregados nas seis variantes ==")
    right = wider = 0
    eh_all, eq_all = [], []
    for t in tasks:
        g = [s for s in S if s["task"] == t]
        agg = defaultdict(int)
        for s in g:
            for i in s["ids"]:
                agg[s["label"][i]] += s["counts"][i]
        n = sum(agg.values())
        H = {k: v / n for k, v in agg.items()}
        Q = {g[0]["label"][i]: g[0]["q"][i] for i in g[0]["ids"]}
        hm, qm = max(H, key=H.get), max(Q, key=Q.get)
        eH, eQ = entropy(H), entropy(Q)
        right += hm == qm
        wider += eQ > eH
        eh_all.append(eH)
        eq_all.append(eQ)
        print(f"  t{t}: humanos {hm} ({H[hm]:.2f}); B0 {qm} ({Q[qm]:.2f}); B0 na moda humana {Q[hm]:.2f}; "
              f"entropia humana {eH:.2f}, B0 {eQ:.2f}; diferença de JSD {tm[t]:+.3f}")
    print(f"B0 acerta a opção mais escolhida em {right}/10 tarefas; é mais disperso que os humanos em {wider}/10; "
          f"entropia média: humanos {sum(eh_all) / 10:.3f}, B0 {sum(eq_all) / 10:.3f}")

    print("\n== 6. Entropia humana do estado x JSD (exploratória) ==")
    eh = [s["eh"] for s in S]
    random.seed(SEED)
    for name, f in (("JSD do UNIFORM", lambda s: s["ju"]), ("JSD do B0", lambda s: s["jb"]),
                    ("B0 - UNIFORM", lambda s: s["jb"] - s["ju"])):
        vals = []
        for _ in range(2000):
            st = [s for t in (random.choice(tasks) for _ in tasks) for s in S if s["task"] == t]
            vals.append(spearman([s["eh"] for s in st], [f(s) for s in st]))
        vals.sort()
        print(f"Spearman, entropia x {name}: {spearman(eh, [f(s) for s in S]):+.3f} "
              f"[{vals[49]:+.3f}; {vals[1949]:+.3f}] (IC por bootstrap de tarefas)")
    ordered = sorted(S, key=lambda s: s["eh"])
    for name, g in (("baixo", ordered[:20]), ("médio", ordered[20:40]), ("alto", ordered[40:])):
        print(f"  tercil {name}: entropia {g[0]['eh']:.2f} a {g[-1]['eh']:.2f}; UNIFORM {sum(s['ju'] for s in g) / 20:.3f}; "
              f"B0 {sum(s['jb'] for s in g) / 20:.3f}; diferença {sum(s['jb'] - s['ju'] for s in g) / 20:+.3f}; "
              f"B0 pior que UNIFORM em {sum(s['jb'] > s['ju'] for s in g)}/20; top-1 {sum(top1(s) for s in g) / 20:.2f}")
    worse = [s for s in S if s["jb"] > s["ju"]]
    print(f"B0 pior que UNIFORM em {len(worse)}/60 estados; tarefas: {sorted({s['task'] for s in worse})}")
    print("pontos (entropia humana, JSD do B0):", " ".join(f"({s['eh']:.3f},{s['jb']:.3f})" for s in S))
    print("pontos (entropia humana, JSD do UNIFORM):", " ".join(f"({s['eh']:.3f},{s['ju']:.3f})" for s in S))

    print("\n== 7. Variantes textuais agregadas (mesma entrada do modelo) ==")
    agg_j, mean3 = [], []
    for t in tasks:
        g = [s for s in S if s["task"] == t and s["variant"] in TEXT]
        c = defaultdict(int)
        for s in g:
            for i in s["ids"]:
                c[i] += s["counts"][i]
        n = sum(c.values())
        agg_j.append(jsd({i: c[i] / n for i in g[0]["ids"]}, g[0]["q"]))
        mean3.append(sum(s["jb"] for s in g) / len(g))
    print(f"JSD do B0: média dos três estados = {sum(mean3) / 10:.3f}; contra o agregado = {sum(agg_j) / 10:.3f}")

    if pilot_paths:
        print("\n== 8. Piloto: reamostragem de 30 escolhas e probabilidades iguais ao limiar ==")
    for path in pilot_paths:
        d, _, P = load(path)
        infl, base, at_thr, total = [], [], 0, 0
        for s in P:
            pv = np.array([s["p"][i] for i in s["ids"]])
            at_thr += int(sum(abs(v - 0.05) < 1e-9 for v in pv))
            total += len(pv)
            b = jsd(s["h"], s["p"])
            sims = [jsd(s["h"], dict(zip(s["ids"], rng.multinomial(30, pv / pv.sum()) / 30))) for _ in range(3000)]
            base.append(b)
            infl.append(sum(sims) / len(sims) - b)
        print(f"  {d['method']} {d['model']}: JSD declarada {sum(base) / len(base):.3f}; aumento médio ao reamostrar "
              f"30 escolhas = {sum(infl) / len(infl):+.3f}; probabilidades iguais a 0,05: {at_thr} de {total}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
