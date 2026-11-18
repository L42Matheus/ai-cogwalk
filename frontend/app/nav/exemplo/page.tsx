"use client";
import Link from "next/link";
import { useState } from "react";
import { NavBars } from "@/lib/NavBars";
import rodadas from "./rodadas.json";

export default function Exemplo() {
  const [caso, escolherCaso] = useState("notebook");
  const [modelo, escolherModelo] = useState("gpt-4.1");
  const [metodo, escolherMetodo] = useState("B1");
  const r = rodadas.find(x => x.caso === caso && x.modelo === modelo && x.metodo === metodo)!;
  const human = Object.fromEntries(Object.entries(r.cliques).map(([id, n]) => [id, Number(n)]));
  const principal = r.opcoes.reduce((a, b) => (human[b.id] || 0) > (human[a.id] || 0) ? b : a);
  const previsao: Record<string, number> = r.previsao;
  return <>
    <Link href="/nav">← Revisão do primeiro clique</Link>
    <h1>Onde os visitantes clicaram?</h1>
    <p className="muted">Explore duas tarefas do mesmo menu com as previsões já registradas no estudo.</p>
    <div className="card"><h2>1. Escolha a tarefa</h2>
      <label>Objetivo do visitante<select value={caso} onChange={e => escolherCaso(e.target.value)}>
        <option value="notebook">Encontrar recomendações para comprar um notebook</option>
        <option value="comedia">Encontrar uma comédia para assistir com amigos</option>
      </select></label></div>
    <div className="card"><h2>2. Escolha como fazer a previsão</h2>
      <div className="grid"><label>Modelo<select value={modelo} onChange={e => escolherModelo(e.target.value)}>
        <option value="gpt-4.1">GPT-4.1</option><option value="claude-opus-5-5">Claude Opus 5.5</option></select></label>
        <label>Instrução<select value={metodo} onChange={e => escolherMetodo(e.target.value)}>
          <option value="B1">Estimar diretamente</option><option value="B1_CW">Aplicar o percurso cognitivo</option><option value="B1_R">Analisar livremente cada opção</option>
        </select></label></div></div>
    <section className="card"><h2>3. Compare com os cliques observados</h2>
      <p aria-live="polite">{human[principal.id]} de {r.n} pessoas escolheram <strong>{principal.label}</strong>. O modelo atribuiu {Math.round(previsao[principal.id] * 100)}% a essa opção.</p>
      <NavBars options={r.opcoes} human={human} humanN={r.n} predicted={previsao}/>
      <p className="small muted">JSD das oito opções: <strong>{r.jsd.toFixed(3).replace(".", ",")}</strong>. Quanto mais próxima de zero, mais semelhantes as distribuições.</p></section>
  </>;
}
