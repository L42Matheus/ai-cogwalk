"use client";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { AlertView, api, Condition, conditionLabel, Decision, fmtDate, RunDetail, StepView, Strategy, strategyLabel } from "@/lib/api";
import { ErrorBox, StatusChip } from "@/lib/ui";

const VIEWPORT = { w: 1366, h: 768 };

export default function RunPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [run, setRun] = useState<RunDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sel, setSel] = useState(0);
  const [reviewer, setReviewer] = useState("");

  const load = () => api<RunDetail>(`/api/runs/${id}`).then(setRun).catch((e) => setError(e.message));
  useEffect(() => { load(); try { setReviewer(localStorage.getItem("acw-reviewer") || ""); } catch {} }, [id]);
  const running = run && run.summary.status !== "CONCLUIDA" && run.summary.status !== "FALHOU";
  useEffect(() => { if (!running) return; const t = setTimeout(load, 1500); return () => clearTimeout(t); }, [run]);

  const vp = useMemo(() => {
    const b = run?.config?.browser as { viewportWidth?: number; viewportHeight?: number } | undefined;
    return { w: b?.viewportWidth || VIEWPORT.w, h: b?.viewportHeight || VIEWPORT.h };
  }, [run]);

  if (!run) return <ErrorBox error={error} />;
  const s = run.summary;
  const step = run.steps[sel];
  const stepAlerts = run.alerts.filter((a) => a.stepIndex === sel);
  const reviewed = run.alerts.filter((a) => a.reviewDecision !== "PENDENTE").length;

  return (
    <>
      <div className="small"><Link href="/">Tarefas</Link> › <Link href={`/projects/${s.projectId}`}>{s.projectName}</Link> › <Link href={`/tasks/${s.taskId}`}>{s.taskTitle}</Link> › execução #{s.id}</div>
      <div className="row"><h1>Execução #{s.id}</h1><StatusChip status={s.status} /></div>
      <ErrorBox error={error} />
      {s.error && <div className="error">Falha: {s.error}</div>}
      <div className="card kv">
        <span className="muted">Condição</span><span>{conditionLabel[s.condition]}</span>
        <span className="muted">Estratégia de prompt</span><span>{strategyLabel[s.promptStrategy]}{run.examples ? ` · banco ${run.examples.bankVersion}: ${run.examples.ids.join(", ")}` : ""}</span>
        <span className="muted">Avaliador / modelo</span><span>{s.evaluator} / {s.model}{s.effort ? ` (esforço ${s.effort})` : ""}</span>
        <span className="muted">Versão do prompt</span><span>{s.promptVersion}</span>
        {s.sourceRunId && (<><span className="muted">Capturas de</span><span><Link href={`/runs/${s.sourceRunId}`}>execução #{s.sourceRunId}</Link> (reavaliação sem nova navegação)</span></>)}
        <span className="muted">Tokens (entrada / saída)</span><span>{s.inputTokens} / {s.outputTokens} · {Math.round(s.llmMillis / 100) / 10} s de modelo</span>
        <span className="muted">Início / fim</span><span>{fmtDate(s.startedAt)} / {fmtDate(s.finishedAt)}</span>
        <span className="muted">Alertas</span><span>{s.alertCount} ({s.suspectCount} suspeitas) · {reviewed} revisado(s)</span>
      </div>
      {run.examples?.origins.includes("ILUSTRATIVO") && <div className="notice">Esta execução usou casos de referência ilustrativos, não observados. Serve só para testar o pipeline.</div>}

      {s.status === "CONCLUIDA" && <Actions run={run} onDone={(nid) => router.push(`/runs/${nid}`)} setError={setError} />}
      {running && <p className="muted">{s.status === "NAVEGANDO" ? "Navegando no site e capturando as telas…" : s.status === "AVALIANDO" ? "Avaliando as etapas…" : "Na fila…"}</p>}

      {run.steps.length > 0 && (
        <>
          <h2>Telas analisadas</h2>
          <div className="steps-nav">
            {run.steps.map((st) => {
              const n = run.alerts.filter((a) => a.stepIndex === st.index).length;
              return <button key={st.index} className={st.index === sel ? "" : "neutral"} onClick={() => setSel(st.index)}>
                Etapa {st.index + 1}{st.action ? "" : " (final)"}{n ? ` · ${n}` : ""}</button>;
            })}
          </div>
          {step && <Step step={step} alerts={stepAlerts} vp={vp} reviewer={reviewer}
            setReviewer={(r) => { setReviewer(r); try { localStorage.setItem("acw-reviewer", r); } catch {} }}
            onReviewed={load} setError={setError} />}
        </>
      )}
    </>
  );
}

function Actions({ run, onDone, setError }: { run: RunDetail; onDone: (id: number) => void; setError: (e: string | null) => void }) {
  const [condition, setCondition] = useState<Condition>(run.summary.condition);
  const [strategy, setStrategy] = useState<Strategy>(run.summary.promptStrategy);
  const id = run.summary.id;
  return (
    <div className="card">
      <div className="row">
        <b>Exportar:</b>
        <a className="btn ghost" href={`/api/runs/${id}/report.html`} target="_blank">Relatório HTML</a>
        <a className="btn ghost" href={`/api/runs/${id}/report.md?download=true`}>Markdown</a>
        <a className="btn ghost" href={`/api/runs/${id}/report.json?download=true`}>JSON</a>
        <a className="btn" href={`/api/runs/${id}/export.zip`}>Pacote completo (ZIP)</a>
      </div>
      <div className="row" style={{ marginTop: 12 }}>
        <b>Reavaliar as mesmas telas:</b>
        <select style={{ width: "auto" }} value={condition} onChange={(e) => setCondition(e.target.value as Condition)}>
          {(["C1", "C2", "C3"] as Condition[]).map((c) => <option key={c} value={c}>{conditionLabel[c]}</option>)}
        </select>
        <select style={{ width: "auto" }} value={strategy} onChange={(e) => setStrategy(e.target.value as Strategy)}>
          {(Object.keys(strategyLabel) as Strategy[]).map((s) => <option key={s} value={s}>{strategyLabel[s]}</option>)}
        </select>
        <button className="ghost" onClick={async () => {
          try { const r = await api<{ id: number }>(`/api/runs/${id}/reevaluate`, { method: "POST", json: { condition, promptStrategy: strategy } }); onDone(r.id); }
          catch (e) { setError((e as Error).message); }
        }}>Reavaliar</button>
      </div>
      <p className="small muted">A reavaliação reaproveita as capturas e não navega de novo. Com o cache de respostas ativo, a mesma configuração reproduz a mesma avaliação.</p>
    </div>
  );
}

function Step({ step, alerts, vp, reviewer, setReviewer, onReviewed, setError }: {
  step: StepView; alerts: AlertView[]; vp: { w: number; h: number }; reviewer: string;
  setReviewer: (r: string) => void; onReviewed: () => void; setError: (e: string | null) => void;
}) {
  const [full, setFull] = useState(false);
  const cited = new Set(alerts.flatMap((a) => a.citedElements));
  const boxes = step.elements.filter((e) => e.inViewport && (e.id === step.targetElementId || cited.has(e.id)));
  const pct = (v: number, t: number) => `${(Math.max(0, v) * 100) / t}%`;
  return (
    <div className="split">
      <div>
        <div className="card">
          <h3>{step.title}</h3>
          <div className="small muted" style={{ wordBreak: "break-all" }}>{step.url}</div>
          <p><b>Ação correta:</b> {step.action ? `${step.action.type === "CLICK" ? "clicar em" : "localizar"} “${step.action.target}”` : "nenhuma (tela final)"}
            {step.action && <span className="small muted"> · alvo {step.targetElementId || "não encontrado"} ({step.targetCandidates} candidato(s))</span>}</p>
          <div className="row small" style={{ marginBottom: 6 }}>
            <button className="neutral" onClick={() => setFull(!full)}>{full ? "Ver área visível" : "Ver página inteira"}</button>
            {step.htmlUrl && <a href={step.htmlUrl} target="_blank">HTML capturado</a>}
          </div>
          {full ? (
            step.fullpageUrl && <div className="shot"><img src={step.fullpageUrl} alt={`Página inteira da etapa ${step.index + 1}`} /></div>
          ) : step.screenshotUrl && (
            <>
              <div className="shot">
                <img src={step.screenshotUrl} alt={`Tela da etapa ${step.index + 1}`} />
                {boxes.map((e) => (
                  <span key={e.id} title={`${e.id}: ${e.text}`} className={`box ${e.id === step.targetElementId ? "" : "cited"}`}
                    style={{ left: pct(e.x, vp.w), top: pct(e.y, vp.h), width: pct(e.width, vp.w), height: pct(e.height, vp.h) }} />
                ))}
              </div>
              <div className="legend"><i />elemento-alvo<i className="c" />elemento citado em alerta</div>
            </>
          )}
        </div>
        {step.promptUser && (
          <details className="card">
            <summary>Prompt, resposta e custo desta etapa</summary>
            <p className="small muted">sha {step.promptSha?.slice(0, 16)}… · {step.inputTokens}/{step.outputTokens} tokens · {step.latencyMs} ms{step.cacheHit ? " · resposta do cache" : ""}</p>
            <h3>Instruções (sistema)</h3><pre>{step.promptSystem}</pre>
            <h3>Entrada da etapa</h3><pre>{step.promptUser}</pre>
            <h3>Resposta bruta</h3><pre>{step.rawResponse}</pre>
          </details>
        )}
      </div>
      <div>
        {step.evalError && <div className="error">Erro na avaliação: {step.evalError}</div>}
        {step.assessment && (
          <div className="card">
            <h3>Perguntas do percurso cognitivo</h3>
            {step.assessment.reasoning && <details><summary className="small">Análise explícita do modelo (CoT)</summary><p className="small">{step.assessment.reasoning}</p></details>}
            <table><tbody>
              {step.assessment.answers.map((q) => (
                <tr key={q.criterion}><td style={{ width: 44 }}><b>{q.criterion}</b></td>
                  <td><span className={`ans-${q.answer}`}>{q.answer === "NAO" ? "NÃO" : q.answer}</span><div className="small">{q.justification}</div></td></tr>
              ))}
            </tbody></table>
          </div>
        )}
        {!step.action && <p className="muted">Tela final: estado alcançado ao fim da tarefa.</p>}
        {alerts.length > 0 && (
          <div className="card">
            <label>Revisor(a)</label>
            <input placeholder="Seu nome" value={reviewer} onChange={(e) => setReviewer(e.target.value)} />
          </div>
        )}
        {alerts.map((a) => <AlertCard key={a.id} alert={a} reviewer={reviewer} onReviewed={onReviewed} setError={setError} />)}
      </div>
    </div>
  );
}

function AlertCard({ alert, reviewer, onReviewed, setError }: {
  alert: AlertView; reviewer: string; onReviewed: () => void; setError: (e: string | null) => void;
}) {
  const shownAt = useRef(Date.now());
  const [note, setNote] = useState(alert.reviewNote || "");
  const [busy, setBusy] = useState(false);
  async function decide(decision: Decision) {
    setBusy(true);
    try {
      await api(`/api/alerts/${alert.id}/review`, { method: "POST", json: {
        decision, note, reviewer: reviewer || null, seconds: Math.round((Date.now() - shownAt.current) / 1000) } });
      onReviewed();
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  return (
    <div className={`alert ${alert.state === "NAO_CONFIRMADA" ? "nc" : ""}`}>
      <div className="row">
        <b>{alert.code}</b><span className="chip">{alert.criterion} · {alert.criterionLabel}</span>
        <span className={`chip ${alert.state === "SUSPEITA" ? "warn" : ""}`}>{alert.state === "SUSPEITA" ? "Suspeita" : "Não confirmada"}</span>
      </div>
      <dl>
        <dt>Evidência visual (fato)</dt><dd>{alert.visualEvidence}{alert.citedElements.length ? <span className="small muted"> [{alert.citedElements.join(", ")}]</span> : null}</dd>
        <dt>Diagnóstico (hipótese)</dt><dd>{alert.diagnosis}</dd>
        <dt>Recomendação (a testar)</dt><dd>{alert.recommendation}</dd>
        {alert.modelConfidence != null && (<><dt>Confiança declarada pelo modelo</dt><dd>{alert.modelConfidence}</dd></>)}
        {alert.verificationNote && (<><dt>Verificação (C3)</dt><dd className="small">{alert.verificationNote}</dd></>)}
      </dl>
      <label>Nota da revisão</label>
      <textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="Por que procede ou não?" />
      <div className="row" style={{ marginTop: 8 }}>
        <button className="ok" disabled={busy} onClick={() => decide("PROCEDENTE")}>Procedente</button>
        <button className="bad" disabled={busy} onClick={() => decide("IMPROCEDENTE")}>Improcedente</button>
        <button className="neutral" disabled={busy} onClick={() => decide("INCONCLUSIVO")}>Inconclusivo</button>
      </div>
      {alert.reviewDecision !== "PENDENTE" && (
        <p className="small muted">Decisão: <b>{alert.reviewDecision}</b>{alert.reviewer ? ` por ${alert.reviewer}` : ""}{alert.reviewSeconds != null ? ` em ${alert.reviewSeconds} s` : ""} · {fmtDate(alert.reviewedAt)}</p>
      )}
    </div>
  );
}
