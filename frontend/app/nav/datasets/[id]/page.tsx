"use client";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Fragment, useEffect, useMemo, useState } from "react";
import { api, fmtDate } from "@/lib/api";
import { NavBars } from "@/lib/NavBars";
import { cost, fmt, fmtAgg, methodLabel, metricInfo, NavDataset, NavMethod, NavRunDetail, NavStatus } from "@/lib/nav";
import { ErrorBox, StatusChip } from "@/lib/ui";

const MODELS = ["claude-opus-5-5", "gpt-4.1", "gpt-5.2"];

export default function NavDatasetPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [ds, setDs] = useState<NavDataset | null>(null);
  const [status, setStatus] = useState<NavStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({ method: "B0" as NavMethod, model: MODELS[0], effort: "medium", samples: 30, variants: [] as string[], splits: [] as string[], tau: "" });
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState<number | null>(null);

  const load = () => Promise.all([api<NavDataset>(`/api/nav/datasets/${id}`), api<NavStatus>("/api/nav/status")])
    .then(([d, s]) => { setDs(d); setStatus(s); setError(null); }).catch((e) => setError(e.message));
  useEffect(() => { load(); }, [id]);
  useEffect(() => {
    if (!ds?.runs?.some((r) => r.status !== "CONCLUIDA" && r.status !== "FALHOU")) return;
    const t = setTimeout(load, 2500);
    return () => clearTimeout(t);
  }, [ds]);

  const states = ds?.stateList || [];
  const hasHumans = states.some((s) => s.humanN > 0);
  const variants = useMemo(() => Array.from(new Set(states.map((s) => s.variant))), [states]);
  const splits = useMemo(() => Array.from(new Set(states.map((s) => s.split))), [states]);
  const method = status?.methods.find((m) => m.id === form.method);

  // Para conjuntos sem dados humanos (piloto), o B0 usa a temperatura ajustada no DEV do benchmark.
  const [kuricTau, setKuricTau] = useState<number | null>(null);
  useEffect(() => {
    if (!ds || hasHumans) return;
    api<NavDataset[]>("/api/nav/datasets").then(async (all) => {
      const k = all.find((d) => d.code === "kuric2025-ia");
      if (!k) return;
      const detail = await api<NavDataset>(`/api/nav/datasets/${k.id}`);
      const b0 = detail.runs?.find((r) => r.method === "B0" && r.status === "CONCLUIDA");
      if (!b0) return;
      const run = await api<NavRunDetail>(`/api/nav/runs/${b0.id}`);
      const t = run.summary?.tau;
      if (typeof t === "number") setKuricTau(t);
    }).catch(() => undefined);
  }, [ds, hasHumans]);

  const selected = states.filter((s) => (form.variants.length === 0 || form.variants.includes(s.variant)) && (form.splits.length === 0 || form.splits.includes(s.split)));
  const uniqueInputs = new Set(selected.map((s) => s.inputHash)).size;
  const plannedCalls = method?.llm ? (method.sampled ? form.samples : 1) * selected.length : 0;

  async function start() {
    setBusy(true); setError(null);
    try {
      const tau = form.tau.trim() ? Number(form.tau.replace(",", ".")) : (!hasHumans && form.method === "B0" ? kuricTau : null);
      const r = await api<{ id: number }>(`/api/nav/datasets/${id}/runs`, { method: "POST", json: {
        method: form.method, model: method?.llm ? form.model : null, effort: method?.llm ? form.effort : null,
        samples: method?.sampled ? form.samples : null, variants: form.variants, splits: form.splits, tau } });
      router.push(`/nav/runs/${r.id}`);
    } catch (e) { setError((e as Error).message); setBusy(false); }
  }

  const toggle = (list: string[], v: string) => list.includes(v) ? list.filter((x) => x !== v) : [...list, v];

  if (!ds) return <ErrorBox error={error} />;
  return (
    <>
      <div className="small"><Link href="/nav">Modo NAV</Link> › conjunto</div>
      <h1>{ds.name}</h1>
      <ErrorBox error={error} />
      <div className="card">
        <div className="kv">
          <span className="muted">Código</span><span>{ds.code}</span>
          {ds.sourceUrl && (<><span className="muted">Fonte</span><span><a href={ds.sourceUrl} target="_blank" rel="noreferrer">{ds.sourceUrl}</a></span></>)}
          {ds.revision && (<><span className="muted">Revisão</span><span className="small">{ds.revision}</span></>)}
          {ds.license && (<><span className="muted">Licença</span><span>{ds.license}</span></>)}
          {ds.notes && (<><span className="muted">Notas</span><span>{ds.notes}</span></>)}
          <span className="muted">Estados</span><span>{states.length} ({states.reduce((a, s) => a + s.humanN, 0)} escolhas humanas)</span>
        </div>
      </div>

      <h2>Nova rodada</h2>
      <div className="card">
        <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fill,minmax(200px,1fr))" }}>
          <div><label>Método</label>
            <select value={form.method} onChange={(e) => setForm({ ...form, method: e.target.value as NavMethod })}>
              {(status?.methods || []).map((m) => <option key={m.id} value={m.id}>{methodLabel[m.id]}</option>)}
            </select></div>
          {method?.llm && (<>
            <div><label>Modelo</label>
              <select value={form.model} onChange={(e) => setForm({ ...form, model: e.target.value })}>
                {MODELS.map((m) => <option key={m} value={m}>{m}</option>)}
              </select></div>
            <div><label>Esforço</label>
              <select value={form.effort} onChange={(e) => setForm({ ...form, effort: e.target.value })}>
                {["low", "medium", "high"].map((m) => <option key={m} value={m}>{m}</option>)}
              </select></div>
          </>)}
          {method?.sampled && (
            <div><label>Participantes simulados (N)</label>
              <input type="number" min={1} max={100} value={form.samples} onChange={(e) => setForm({ ...form, samples: Number(e.target.value) })} /></div>
          )}
          {form.method === "B0" && (
            <div><label>Temperatura τ (vazio = ajustar no DEV)</label>
              <input placeholder={!hasHumans && kuricTau ? `${fmt(kuricTau, 4)} (Kuric DEV)` : "ajustar"} value={form.tau} onChange={(e) => setForm({ ...form, tau: e.target.value })} /></div>
          )}
        </div>
        {variants.length > 1 && (<><label>Variantes (nenhuma marcada = todas)</label>
          <div className="row">{variants.map((v) => (
            <button key={v} type="button" className={form.variants.includes(v) ? "" : "neutral"} onClick={() => setForm({ ...form, variants: toggle(form.variants, v) })}>{v}</button>))}</div></>)}
        {splits.length > 1 && (<><label>Partições (nenhuma marcada = todas)</label>
          <div className="row">{splits.map((v) => (
            <button key={v} type="button" className={form.splits.includes(v) ? "" : "neutral"} onClick={() => setForm({ ...form, splits: toggle(form.splits, v) })}>{v}</button>))}</div></>)}
        <p className="small muted">
          {method?.description}. {selected.length} estado(s), {uniqueInputs} entrada(s) distinta(s).
          {method?.llm ? ` Até ${plannedCalls} chamadas pagas (entradas repetidas vêm do cache).` : ""}
        </p>
        {method?.sampled && <div className="notice">B1-S faz chamadas independentes por participante simulado. Confirme o orçamento antes de rodar com N alto.</div>}
        <button onClick={start} disabled={busy || !status}>{busy ? "Iniciando…" : "Iniciar rodada"}</button>
      </div>

      <h2>Comparação das rodadas</h2>
      <p className="small muted">Média nos estados de teste (tarefas ímpares) com IC de 95% por bootstrap de tarefas. Em conjuntos sem dados humanos não há métricas: as previsões ficam congeladas até a coleta.</p>
      <div className="card" style={{ overflowX: "auto" }}>
        <table>
          <thead><tr><th>#</th><th>Método</th><th>Status</th>
            {metricInfo.slice(0, 5).map((m) => <th key={m.key} title={m.hint}>{m.label}{m.better === "low" ? " ↓" : m.better === "high" ? " ↑" : ""}</th>)}
            <th>Custo</th><th>Hash congelado</th></tr></thead>
          <tbody>
            {(ds.runs || []).map((r) => {
              const c = cost(r.model, r.inputTokens, r.outputTokens);
              return (
                <tr key={r.id}>
                  <td><Link href={`/nav/runs/${r.id}`}>#{r.id}</Link></td>
                  <td className="small">{methodLabel[r.method]}<div className="muted">{r.model || ""}</div></td>
                  <td><StatusChip status={r.status} /></td>
                  {metricInfo.slice(0, 5).map((m) => <td key={m.key} className="small">{fmtAgg(r.headline?.[m.key])}</td>)}
                  <td className="small">{c === null ? "?" : `US$ ${fmt(c, 2)}`}<div className="muted">{r.llmCalls} chamadas</div></td>
                  <td className="small" title={r.frozenHash || ""}>{r.frozenHash ? r.frozenHash.slice(0, 12) : "–"}<div className="muted">{fmtDate(r.createdAt)}</div></td>
                </tr>
              );
            })}
            {(ds.runs || []).length === 0 && <tr><td colSpan={10} className="muted">Nenhuma rodada.</td></tr>}
          </tbody>
        </table>
      </div>

      <h2>Estados</h2>
      <div className="card" style={{ overflowX: "auto" }}>
        <table>
          <thead><tr><th>Estado</th><th>Partição</th><th>Modalidade</th><th>Tarefa</th><th>n</th></tr></thead>
          <tbody>
            {states.map((s) => (
              <Fragment key={s.id}>
                <tr onClick={() => setOpen(open === s.id ? null : s.id)} style={{ cursor: "pointer" }}>
                  <td className="small">{s.key}</td><td className="small">{s.split}</td><td className="small">{s.modality}</td>
                  <td className="small">{s.taskText.length > 110 ? s.taskText.slice(0, 110) + "…" : s.taskText}</td><td>{s.humanN}</td>
                </tr>
                {open === s.id && (
                  <tr><td colSpan={5}>
                    <div className="split">
                      <div>{s.screenshot ? <div className="shot"><img src={`/api/files/${s.screenshot}`} alt={`Tela do estado ${s.key}`} /></div> : <p className="muted small">Estado textual (menu em lista, sem imagem).</p>}</div>
                      <div><NavBars options={s.options} human={s.humanCounts} humanN={s.humanN} /></div>
                    </div>
                  </td></tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
