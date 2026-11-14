"""Casos adicionais (TP-t2 e TP-t8) e referência de popularidade. Não faz chamadas a LLMs.

Lê as exportações já gravadas e imprime:
  1. as 12 previsões adicionais (B1, B1-CW e B1-R, dois modelos, dois estados), com a JSD recalculada;
  2. a comparação com o piloto inicial: mesma entrada e mesmo prompt, chamadas diferentes;
  3. os contrastes entre condições dentro de cada rodada;
  4. o custo estimado por etapa;
  5. a referência de popularidade (contagens da outra metade das tarefas) contra UNIFORM e B0.

Uso: python3 nav_casos_adicionais.py   (a partir de analysis/)
"""
import glob
import json
import os
from collections import defaultdict

from nav_b0_crossfit import fit, retemper, sign_flip_p
from nav_kuric_analysis import cluster_ci, jsd, norm

AQUI = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(AQUI, "resultados-2026-11-13")
CASOS = {"comédia (TP-t2)": "piloto-comedia-2026-11-13", "notebook (TP-t8)": "piloto-notebook-2026-11-13"}
# US$ por milhão de tokens (entrada, saída), como registrado no protótipo em 13/11/2026
PRECO = {"gpt-4.1": (2.0, 8.0), "claude-opus-5-5": (4.0, 20.0)}
ROTULO = {"B1": "B1", "B1_CW": "B1-CW", "B1_R": "B1-R"}


def carregar(caminho):
    d = json.load(open(caminho, encoding="utf-8"))
    linhas = []
    for p in d["predictions"]:
        st = p["state"]
        ids = [o["id"] for o in st["options"]]
        h = norm(ids, st["humanCounts"])
        q = norm(ids, p["distribution"])
        pe, ps = PRECO[d["model"]]
        linhas.append({
            "rodada": d["id"], "metodo": d["method"], "modelo": d["model"], "estado": st["key"],
            "rotulos": [o["label"] for o in st["options"]], "ids": ids,
            "cliques": [st["humanCounts"].get(i, 0) for i in ids], "q": [q[i] for i in ids],
            "jsd": jsd(h, q), "jsd_exportada": p["metrics"]["jsd"],
            "hash_entrada": st["inputHash"], "hash_prompt": p["promptSha"], "cache": p["cacheHits"],
            "tokens": (p["inputTokens"], p["outputTokens"]),
            "custo": (p["inputTokens"] * pe + p["outputTokens"] * ps) / 1e6,
        })
    return linhas


def um(linhas, modelo, metodo, estado):
    achados = [r for r in linhas if (r["modelo"], r["metodo"], r["estado"]) == (modelo, metodo, estado)]
    assert len(achados) == 1, (modelo, metodo, estado, len(achados))
    return achados[0]


def main():
    novos = {nome: [r for f in sorted(glob.glob(os.path.join(AQUI, pasta, "*.json"))) for r in carregar(f)]
             for nome, pasta in CASOS.items()}
    inicial = [r for f in sorted(glob.glob(os.path.join(BASE, "piloto-run*.json"))) for r in carregar(f)]
    todos = [r for linhas in novos.values() for r in linhas]

    print("== 1. Previsões adicionais (uma chamada por combinação) ==")
    for nome, linhas in novos.items():
        r0 = linhas[0]
        print(f"\n{nome}: cliques humanos " + ", ".join(f"{l} {c}" for l, c in zip(r0["rotulos"], r0["cliques"])))
        for r in sorted(linhas, key=lambda r: (r["modelo"], r["metodo"])):
            assert abs(r["jsd"] - r["jsd_exportada"]) < 1e-9 and r["cache"] == 0
            print(f"  {r['modelo']:16} {ROTULO[r['metodo']]:6} JSD {r['jsd']:.4f}  tokens {r['tokens'][0]}/{r['tokens'][1]}"
                  f"  US$ {r['custo']:.5f}  | " + " ".join(f"{100 * x:.1f}" for x in r["q"]))
    print("\nTodas as 12 JSDs recalculadas coincidem com as exportadas; nenhuma veio do cache.")

    print("\n== 2. Mesma entrada e mesmo prompt, duas chamadas (piloto inicial x adicional) ==")
    variacao = defaultdict(list)
    inversoes = 0
    for modelo in PRECO:
        for estado in ("TP-t2-raiz", "TP-t8-raiz"):
            par = {}
            for metodo in ("B1", "B1_CW"):
                a, b = um(inicial, modelo, metodo, estado), um(todos, modelo, metodo, estado)
                assert a["hash_entrada"] == b["hash_entrada"] and a["hash_prompt"] == b["hash_prompt"]
                vt = 0.5 * sum(abs(x - y) for x, y in zip(a["q"], b["q"]))
                variacao[modelo].append(abs(a["jsd"] - b["jsd"]))
                par[metodo] = (a["jsd"], b["jsd"])
                print(f"  {modelo:16} {estado} {ROTULO[metodo]:6} JSD {a['jsd']:.4f} -> {b['jsd']:.4f}"
                      f"  |dif| {abs(a['jsd'] - b['jsd']):.4f}  variação total entre as duas previsões {vt:.3f}")
            d0, d1 = par["B1_CW"][0] - par["B1"][0], par["B1_CW"][1] - par["B1"][1]
            inversoes += (d0 > 0) != (d1 > 0)
            print(f"      contraste B1-CW - B1: inicial {d0:+.4f}, adicional {d1:+.4f}")
    print(f"  Direção do contraste mudou em {inversoes} de 4 combinações.")
    for modelo, v in variacao.items():
        print(f"  {modelo}: |dif. de JSD| entre chamadas, média {sum(v) / len(v):.4f}, mín. {min(v):.4f}, máx. {max(v):.4f} (4 pares)")

    print("\n== 3. Contrastes dentro da rodada adicional ==")
    for modelo in PRECO:
        for estado in ("TP-t2-raiz", "TP-t8-raiz"):
            b1, cw, r = (um(todos, modelo, m, estado)["jsd"] for m in ("B1", "B1_CW", "B1_R"))
            melhor = min((b1, "B1"), (cw, "B1-CW"), (r, "B1-R"))[1]
            print(f"  {modelo:16} {estado}  CW-B1 {cw - b1:+.4f}  CW-R {cw - r:+.4f}  R-B1 {r - b1:+.4f}  menor JSD: {melhor}")

    print("\n== 4. Custo estimado (US$) ==")
    def soma(linhas, modelo):
        return sum(r["custo"] for r in linhas if r["modelo"] == modelo)
    etapas = [("Piloto inicial", inicial)] + [(n, l) for n, l in novos.items()]
    tg = tc = n = 0
    for nome, linhas in etapas:
        g, c = soma(linhas, "gpt-4.1"), soma(linhas, "claude-opus-5-5")
        tg, tc, n = tg + g, tc + c, n + len(linhas)
        print(f"  {nome:20} {len(linhas):2} chamadas  GPT-4.1 {g:.5f}  Claude {c:.5f}  total {g + c:.5f}")
    print(f"  {'Total documentado':20} {n:2} chamadas  GPT-4.1 {tg:.5f}  Claude {tc:.5f}  total {tg + tc:.5f}")

    print("\n== 5. Referência de popularidade (10 tarefas) ==")
    b0 = json.load(open(os.path.join(BASE, "kuric-b0.json"), encoding="utf-8"))
    tau0 = b0["summary"]["tau"]
    estados = []
    for pr in b0["predictions"]:
        st = pr["state"]
        ids = [o["id"] for o in st["options"]]
        estados.append({"tarefa": st["task"], "metade": st["split"], "ids": ids, "contagens": st["humanCounts"],
                        "h": norm(ids, st["humanCounts"]), "p": norm(ids, pr["distribution"])})
    tau = {m: fit([e for e in estados if e["metade"] == m], tau0) for m in ("DEV", "TEST")}
    pop = {}
    for ajuste, avaliacao in (("DEV", "TEST"), ("TEST", "DEV")):
        ids = estados[0]["ids"]
        m = {i: sum(e["contagens"].get(i, 0) for e in estados if e["metade"] == ajuste) for i in ids}
        pop[avaliacao] = norm(ids, {i: m[i] + 1 for i in ids})
        print(f"  popularidade ajustada em {ajuste}: " + " ".join(f"{pop[avaliacao][i]:.3f}" for i in ids))
    jp, ju, jb = defaultdict(list), defaultdict(list), defaultdict(list)
    for e in estados:
        outra = "TEST" if e["metade"] == "DEV" else "DEV"
        jp[e["tarefa"]].append(jsd(e["h"], pop[e["metade"]]))
        ju[e["tarefa"]].append(jsd(e["h"], {i: 1 / len(e["ids"]) for i in e["ids"]}))
        jb[e["tarefa"]].append(jsd(e["h"], retemper(e["p"], tau0, tau[outra])))
    tarefas = sorted(jp, key=int)

    def resumo(nome, a, b=None):
        dif = {t: ([x - y for x, y in zip(a[t], b[t])] if b else a[t]) for t in tarefas}
        media, lo, hi = cluster_ci(dif)
        texto = f"  {nome:28} {media:+.4f} [{lo:+.4f}; {hi:+.4f}]"
        if b:
            medias = [sum(dif[t]) / len(dif[t]) for t in tarefas]
            texto += f"  tarefas com diferença < 0: {sum(x < 0 for x in medias)}/10  troca de sinal p = {sign_flip_p(medias):.3f}"
        print(texto)

    resumo("JSD popularidade", jp)
    resumo("JSD B0 (val. cruzada)", jb)
    resumo("JSD UNIFORM", ju)
    resumo("popularidade - UNIFORM", jp, ju)
    resumo("B0 - popularidade", jb, jp)
    resumo("B0 - UNIFORM", jb, ju)
    print("  por tarefa (popularidade | B0 | UNIFORM): " +
          "  ".join(f"t{t} {sum(jp[t]) / 6:.3f}|{sum(jb[t]) / 6:.3f}|{sum(ju[t]) / 6:.3f}" for t in tarefas))


if __name__ == "__main__":
    main()
