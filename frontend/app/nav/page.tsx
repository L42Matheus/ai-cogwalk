"use client";
import { NavGuide } from "@/lib/NavGuide";
import Link from "next/link";
import { useEffect, useState } from "react";
import { api, fmtDate } from "@/lib/api";
import { fmtAgg, methodLabel, NavDataset, NavRun, NavStatus } from "@/lib/nav";
import { ErrorBox, StatusChip } from "@/lib/ui";

export default function NavHome() {
  const [status, setStatus] = useState<NavStatus | null>(null);
  const [datasets, setDatasets] = useState<NavDataset[]>([]);
  const [runs, setRuns] = useState<NavRun[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    Promise.all([api<NavStatus>("/api/nav/status"), api<NavDataset[]>("/api/nav/datasets"), api<NavRun[]>("/api/nav/runs")])
      .then(([s, d, r]) => { setStatus(s); setDatasets(d); setRuns(r); setError(null); })
      .catch((e) => setError(e.message));
  useEffect(() => { load(); }, []);
  const busy = status?.importStatus && !["ocioso", "concluído"].includes(status.importStatus) && !status.importStatus.startsWith("falhou");
  useEffect(() => {
    const pending = runs.some((r) => r.status !== "CONCLUIDA" && r.status !== "FALHOU");
    if (!busy && !pending) return;
    const t = setTimeout(load, 2500);
    return () => clearTimeout(t);
  }, [runs, busy]);

  async function importKuric() {
    try { await api("/api/nav/datasets/kuric2025", { method: "POST" }); load(); }
    catch (e) { setError((e as Error).message); }
  }

  return (
    <>
      <h1>Revisão do primeiro clique</h1>
      <NavGuide step={1} message={busy ? "Importando tarefas, menus e telas. O conjunto aparecerá aqui ao terminar." : !status ? "Consultando os conjuntos disponíveis…" : datasets.length ? "Abra um conjunto abaixo para escolher as tarefas e o método de previsão." : "Para experimentar com cliques já observados, importe o conjunto Kuric 2025."} />
      <section className="nav-entry">
        <div><h2>Explorar um teste já realizado</h2><p className="muted">Veja onde 30 pessoas clicaram e compare três formas de perguntar aos modelos.</p></div>
        <Link className="btn ghost" href="/nav/exemplo">Ver casos do estudo →</Link>
      </section>
      <ErrorBox error={error} />
      {status && (
        <div className="card row small">
          <span className={`chip ${status.anthropicConfigured ? "ok" : "warn"}`}>Anthropic {status.anthropicConfigured ? "configurada" : "sem chave"}</span>
          <span className={`chip ${status.openaiConfigured ? "ok" : "warn"}`}>OpenAI {status.openaiConfigured ? "configurada" : "sem chave"}</span>
          <span className="muted">Prompts: {status.promptVersion}</span>
          <span className="muted">Importação: {status.importStatus}</span>
        </div>
      )}

      <div className="row"><h2>Conjuntos</h2><span className="spacer" />
        <button className="ghost" onClick={importKuric} disabled={!!busy}>{busy ? "Importando…" : "Importar dataset Kuric 2025"}</button></div>
      <div className="grid">
        {datasets.map((d) => (
          <Link key={d.id} href={`/nav/datasets/${d.id}`} className="card" style={{ textDecoration: "none", color: "inherit" }}>
            <h3>{d.name}</h3>
            <div className="small muted">{d.code}{d.revision ? ` · rev. ${d.revision.slice(0, 7)}` : ""}</div>
            <div className="small" style={{ marginTop: 8 }}>{d.states} estado(s){d.license ? ` · ${d.license}` : ""}</div>
          </Link>
        ))}
        {datasets.length === 0 && !error && (
          <p className="muted">Nenhum conjunto. Importe o dataset Kuric 2025 ou capture a página inicial de uma tarefa (Tarefas → projeto → tarefa → “Capturar página inicial para o NAV”).</p>
        )}
      </div>

      <h2>Rodadas</h2>
      <div className="card" style={{ overflowX: "auto" }}>
        <table>
          <thead><tr><th>#</th><th>Método</th><th>Modelo</th><th>Status</th><th title="Estados de teste quando a rodada os inclui; senão, todos os estados avaliados">JSD</th><th>Cobertura</th><th>Top-1</th><th>Criada</th></tr></thead>
          <tbody>
            {runs.map((r) => (
              <tr key={r.id}>
                <td><Link href={`/nav/runs/${r.id}`}>#{r.id}</Link></td>
                <td className="small">{methodLabel[r.method]}</td>
                <td className="small">{r.model || "–"}</td>
                <td><StatusChip status={r.status} />{r.status !== "CONCLUIDA" && r.status !== "FALHOU" ? <span className="small muted"> {r.statesDone}/{r.statesTotal}</span> : null}</td>
                <td className="small">{fmtAgg(r.headline?.jsd)}</td>
                <td className="small">{fmtAgg(r.headline?.coverage)}</td>
                <td className="small">{fmtAgg(r.headline?.top1)}</td>
                <td className="small">{fmtDate(r.createdAt)}</td>
              </tr>
            ))}
            {runs.length === 0 && <tr><td colSpan={8} className="muted">Nenhuma rodada ainda.</td></tr>}
          </tbody>
        </table>
      </div>
    </>
  );
}
