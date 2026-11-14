"""Referência de popularidade: metade das tarefas para ajuste, outra para avaliação."""
import json
import sys
from collections import Counter, defaultdict

from nav_kuric_analysis import cluster_ci, jsd, norm


def avaliar(exportacao):
    estados = [p["state"] for p in exportacao["predictions"]]
    por_tarefa = defaultdict(list)
    diferencas = defaultdict(list)
    for ajuste, avaliacao in (("DEV", "TEST"), ("TEST", "DEV")):
        contagens = Counter()
        tarefas = set()
        for estado in estados:
            if estado["split"] == ajuste:
                tarefas.add(estado["task"])
                contagens.update(estado["humanCounts"])
        for estado in estados:
            if estado["split"] != avaliacao:
                continue
            assert estado["task"] not in tarefas
            ids = [o["id"] for o in estado["options"]]
            humano = norm(ids, estado["humanCounts"])
            popularidade = norm(ids, {i: contagens[i] + 1 for i in ids})
            uniforme = {i: 1 / len(ids) for i in ids}
            valor = jsd(humano, popularidade)
            por_tarefa[estado["task"]].append(valor)
            diferencas[estado["task"]].append(valor - jsd(humano, uniforme))
    return cluster_ci(por_tarefa), cluster_ci(diferencas)


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8") as arquivo:
        for nome, resultado in zip(("Popularidade", "Popularidade - uniforme"), avaliar(json.load(arquivo))):
            media, inferior, superior = resultado
            print(f"{nome}: {media:.6f} [{inferior:.6f}, {superior:.6f}]")
