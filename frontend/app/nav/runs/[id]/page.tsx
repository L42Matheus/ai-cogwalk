"use client";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api, fmtDate } from "@/lib/api";
import { NavBars } from "@/lib/NavBars";
import { cost, fmt, fmtAgg, methodLabel, metricInfo, NavPrediction, NavRunDetail, normalize } from "@/lib/nav";
import { ErrorBox, StatusChip } from "@/lib/ui";

const GROUPS: [string, string][] = [
  ["TEST|ALL", "Teste · todas"], ["TEST|TEXT", "Teste · texto"], ["TEST|VISUAL", "Teste · visual"],
  ["DEV|ALL", "Desenvolvimento"], ["PILOTO|ALL", "Piloto"], ["ALL|ALL", "Todos os estados"],
];

interface PromptDetail { promptSystem?: string; promptUser?: string; detail?: unknown }

export default function NavRunPage() {
  const { id } = useParams<{ id: string }>();
  const [run, setRun] = useState<NavRunDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState("TEST");
  const [prompt, setPrompt] = useState<Record<number, PromptDetail>>({});

  const load = () => api<NavRunDetail>(`/api/nav/runs/${id}`).then((r) => { setRun(r); setError(null); }).catch((e) => setError(e.message));
  useEffect(() => { load(); }, [id]);
  useEffect(() => {
    if (!run || run.status === "CONCLUIDA" || run.status === "FALHOU") return;
    const t = setTimeout(load, 2500);
    return () => clearTimeout(t);
  }, [run]);

  async function showPrompt(p: NavPrediction) {
    if (prompt[p.id]) { const c = { ...prompt }; delete c[p.id]; setPrompt(c); return; }
    try { const d = await api<PromptDetail>(`/api/nav/predictions/${p.id}`); setPrompt({ ...prompt, [p.id]: d }); }
    catch (e) { setError((e as Error).message); }
  }

  if (!run) return <ErrorBox error={error} />;
  const groups = run.summary?.groups || {};
  const extra = Object.entries(run.summary || {}).filter(([k]) => k !== "groups" && k !== "byVariant");
  const preds = run.predictions.filter((p) => filter === "ALL" || p.state.split === filter);
  const c = cost(run.model, run.inputTokens, run.outputTokens);

  return (
    <>
      <div className="small"><Link href="/nav">Modo NAV</Link> › <Link href={`/nav/datasets/${run.datasetId}`}>{run.datasetName}</Link> › rodada #{run.id}</div>
      <h1>{methodLabel[run.method]} {run.model ? <span className="muted">· {run.model}</span> : null}</h1>
      <ErrorBox error={error || run.error} />
      <div className="card">
        <div className="kv">
          <span className="muted">Status</span><span><StatusChip status={run.status} /> {run.statesDone}/{run.statesTotal} estados</span>
          <span className="muted">Hash das previsões congeladas</span><span className="small" style={{ wordBreak: "break-all" }}>{run.frozenHash || "–"}</span>
          <span className="muted">Uso</span><span className="small">{run.llmCalls} chamadas · {run.inputTokens} tokens de entrada · {run.outputTokens} de saída{c !== null && run.model ? ` · ≈ US$ ${fmt(c, 2)}` : ""}</span>
          <span className="muted">Criada / concluída</span><span className="small">{fmtDate(run.createdAt)} / {fmtDate(run.finishedAt)}</span>
          {extra.map(([k, v]) => (<span key={k} style={{ display: "contents" }}><span className="muted">{k}</span><span className="small">{typeof v === "number" ? fmt(v, Number.isInteger(v) ? 0 : 4) : String(v)}</span></span>))}
        </div>
        <div className="row" style={{ marginTop: 10 }}>
          <a className="btn ghost" href={`/api/nav/runs/${run.id}/export.json`}>Exportar JSON</a>
          <a className="btn ghost" href={`/api/nav/runs/${run.id}/export.csv`}>Exportar CSV</a>
        </div>
      </div>

      {Object.keys(groups).length > 0 && (<>
        <h2>Resumo</h2>
        <p className="small muted">Média com IC de 95% por bootstrap de tarefas (semente fixa). ↓ menor é melhor, ↑ maior é melhor.</p>
        <div className="card" style={{ overflowX: "auto" }}>
          <table>
            <thead><tr><th>Grupo</th><th>Estados</th>{metricInfo.map((m) => <th key={m.key} title={m.hint}>{m.label}{m.better === "low" ? " ↓" : m.better === "high" ? " ↑" : ""}</th>)}</tr></thead>
            <tbody>
              {GROUPS.filter(([g]) => groups[g]).map(([g, label]) => (
                <tr key={g}><td>{label}</td><td>{groups[g].jsd?.states}</td>
                  {metricInfo.map((m) => <td key={m.key} className="small">{fmtAgg(groups[g][m.key])}</td>)}</tr>
              ))}
            </tbody>
          </table>
        </div>
      </>)}

      <div className="row"><h2>Estados</h2><span className="spacer" />
        {["TEST", "DEV", "PILOTO", "ALL"].filter((f) => f === "ALL" || run.predictions.some((p) => p.state.split === f)).map((f) => (
          <button key={f} className={filter === f ? "" : "neutral"} onClick={() => setFilter(f)}>{f === "ALL" ? "Todos" : f}</button>))}
      </div>
      {preds.map((p) => {
        const ids = p.state.options.map((o) => o.id);
        const dist = normalize(ids, p.distribution);
        const [vw, vh] = (p.state.viewport || "1366x768").split("x").map(Number);
        return (
          <div className="card" key={p.id}>
            <div className="row"><h3>{p.state.key}</h3><span className="chip">{p.state.split}</span><span className="chip">{p.state.modality}</span><span className="spacer" />
              <button className="ghost" onClick={() => showPrompt(p)}>{prompt[p.id] ? "Ocultar prompt" : "Ver prompt e resposta"}</button></div>
            <p className="small">{p.state.taskText}</p>
            {p.error && <div className="error">{p.error}</div>}
            <div className="split">
              <div>
                <NavBars options={p.state.options} human={p.state.humanCounts} humanN={p.state.humanN} predicted={p.distribution} />
                {p.samples && p.samples.length > 0 && <p className="small muted">Escolhas simuladas: {p.samples.join(", ")}</p>}
              </div>
              <div>
                {p.metrics ? (
                  <div className="kv small">
                    {metricInfo.map((m) => (<span key={m.key} style={{ display: "contents" }}><span className="muted" title={m.hint}>{m.label}</span><span>{fmt((p.metrics as unknown as Record<string, number>)[m.key])}</span></span>))}
                  </div>
                ) : <p className="small muted">Sem dados humanos para este estado: previsão congelada.</p>}
                {p.state.screenshot && (
                  <div className="shot" style={{ marginTop: 10 }}>
                    <img src={`/api/files/${p.state.screenshot}`} alt={`Tela do estado ${p.state.key}`} />
                    {p.state.options.filter((o) => o.x !== undefined && o.x !== null).map((o) => (
                      <span key={o.id} className="box pred" title={`${o.label}: ${Math.round(dist[o.id] * 100)}%`}
                        style={{ left: `${(o.x! / vw) * 100}%`, top: `${(o.y! / vh) * 100}%`, width: `${(o.width! / vw) * 100}%`, height: `${(o.height! / vh) * 100}%`, opacity: 0.15 + 0.85 * dist[o.id] }} />
                    ))}
                  </div>
                )}
              </div>
            </div>
            {prompt[p.id] && (<>
              <h3 style={{ marginTop: 10 }}>Prompt do sistema</h3><pre>{prompt[p.id].promptSystem || "(sem LLM)"}</pre>
              <h3>Mensagem do usuário</h3><pre>{prompt[p.id].promptUser || "(sem LLM)"}</pre>
              <h3>Resposta / detalhe</h3><pre>{JSON.stringify(prompt[p.id].detail, null, 2)}</pre>
            </>)}
          </div>
        );
      })}
    </>
  );
}
