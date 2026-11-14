"""Compara previsões GPT no mesmo estado do benchmark pelo backend NAV."""
import argparse
import json
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ESTADO = "TP-t2-raiz"
MODELO = "gpt-4.1"
GRAVADAS = Path(__file__).parent / "resultados-2026-11-13"


def requisitar(base, caminho, dados=None):
    corpo = json.dumps(dados).encode() if dados is not None else None
    pedido = Request(base.rstrip("/") + "/api/nav" + caminho, data=corpo,
                     headers={"Content-Type": "application/json"})
    with urlopen(pedido, timeout=45) as resposta:
        return json.load(resposta)


def comparar(rodadas, estado):
    print(f"Estado {estado} | JSD menor = previsão mais próxima dos cliques humanos")
    for rodada in rodadas:
        previsao = next((p for p in rodada["predictions"] if p["state"]["key"] == estado), None)
        if not previsao or previsao.get("error") or not previsao.get("metrics"):
            raise ValueError(f"Rodada {rodada['id']} sem previsão válida para {estado}.")
        print(f"\n{rodada['model']} | {rodada['method']} | JSD {previsao['metrics']['jsd']:.3f}")
        tela = previsao["state"]
        for opcao in tela["options"]:
            codigo = opcao["id"]
            votos = tela["humanCounts"].get(codigo, 0)
            print(f"  {opcao['label']:18} GPT {previsao['distribution'][codigo]:6.1%}   "
                  f"pessoas {votos:2}/{tela['humanN']}")


def importar(base):
    conjuntos = requisitar(base, "/datasets")
    for conjunto in conjuntos:
        if conjunto["code"] == "kuric2025-ia":
            return conjunto["id"]
    status = requisitar(base, "/status")["importStatus"]
    if status not in ("importando", "na fila"):
        requisitar(base, "/datasets/kuric2025", {})
    print("Importando menus e telas do benchmark...", flush=True)
    limite = time.monotonic() + 900
    while time.monotonic() < limite:
        status = requisitar(base, "/status")["importStatus"]
        if status.startswith("falhou"):
            raise ValueError(status)
        if status == "concluído":
            for conjunto in requisitar(base, "/datasets"):
                if conjunto["code"] == "kuric2025-ia":
                    return conjunto["id"]
            raise ValueError("O backend terminou a importação sem publicar o conjunto.")
        time.sleep(2)
    raise TimeoutError("A importação segue no backend. Consulte o painel /nav.")


def testar(args):
    if not requisitar(args.backend, "/status")["openaiConfigured"]:
        raise ValueError("Configure OPENAI_API_KEY no .env e recrie o backend com docker compose up -d backend.")
    conjunto = importar(args.backend)
    estados = requisitar(args.backend, f"/datasets/{conjunto}")["stateList"]
    if not any(s["key"] == ESTADO and s["split"] == "DEV" for s in estados):
        raise ValueError(f"Estado de desenvolvimento ausente: {ESTADO}.")
    args.saida.mkdir(parents=True, exist_ok=True)
    metodos = ["B1", "B1_CW", "B1_R"]
    if args.amostras:
        metodos.append("B1_S")
    for metodo in metodos:
        pedido = {"method": metodo, "model": MODELO, "stateKeys": [ESTADO], "splits": ["DEV"]}
        if metodo == "B1_S":
            pedido["samples"] = args.amostras
        numero = requisitar(args.backend, f"/datasets/{conjunto}/runs", pedido)["id"]
        print(f"Rodada {numero}, {metodo}. Acompanhe em /nav/runs/{numero}.", flush=True)
        limite = time.monotonic() + 1800
        while time.monotonic() < limite:
            rodada = requisitar(args.backend, f"/runs/{numero}")
            if rodada["status"] == "FALHOU":
                raise ValueError(f"Rodada {numero}: {rodada['error']}")
            if rodada["status"] == "CONCLUIDA":
                exportacao = requisitar(args.backend, f"/runs/{numero}/export.json")
                caminho = args.saida / f"gpt-{numero}-{metodo}.json"
                caminho.write_text(json.dumps(exportacao, ensure_ascii=False, indent=2), encoding="utf-8")
                print(f"Exportação: {caminho}")
                comparar([exportacao], ESTADO)
                break
            time.sleep(2)
        else:
            raise TimeoutError(f"Rodada {numero} ainda em andamento. Consulte o painel antes de repetir.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--executar", action="store_true", help="Inicia rodadas com o GPT pelo backend.")
    parser.add_argument("--backend", default="http://localhost:8080")
    parser.add_argument("--saida", type=Path, default=Path("resultados-gpt"))
    parser.add_argument("--amostras", type=int, help="Acrescenta B1-S com N visitantes simulados (30 no protocolo).")
    args = parser.parse_args()
    if args.amostras is not None and (not args.executar or args.amostras < 1):
        parser.error("--amostras exige --executar e um inteiro positivo.")
    if args.executar:
        testar(args)
    else:
        print("Releitura dos resultados registrados no piloto de 13/11/2026.")
        rodadas = [json.loads((GRAVADAS / f"piloto-run{i}.json").read_text()) for i in (7, 8)]
        comparar(rodadas, ESTADO)


if __name__ == "__main__":
    try:
        main()
    except HTTPError as exc:
        sys.exit(f"Erro HTTP {exc.code} no backend: {exc.reason}")
    except (OSError, ValueError, URLError) as exc:
        sys.exit(str(exc))
