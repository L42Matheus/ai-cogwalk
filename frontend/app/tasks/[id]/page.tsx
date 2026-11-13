"use client";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, Condition, conditionLabel, fmtDate, RunSummary, Status, Strategy, strategyLabel, Task } from "@/lib/api";
import { TaskForm } from "@/lib/TaskForm";
import { ErrorBox, StatusChip } from "@/lib/ui";

export default function TaskPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [task, setTask] = useState<Task | null>(null);
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [status, setStatus] = useState<Status | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [condition, setCondition] = useState<Condition>("C2");
  const [strategy, setStrategy] = useState<Strategy>("ZERO_SHOT");
  const [evaluator, setEvaluator] = useState("");
  const [busy, setBusy] = useState(false);
  const [capturing, setCapturing] = useState(false);

  const load = () => Promise.all([api<Task>(`/api/tasks/${id}`), api<RunSummary[]>(`/api/tasks/${id}/runs`), api<Status>("/api/status")])
    .then(([t, r, s]) => { setTask(t); setRuns(r); setStatus(s); setEvaluator((e) => e || (s.claudeConfigured ? "claude" : "offline")); })
    .catch((e) => setError(e.message));
  useEffect(() => { load(); }, [id]);
  useEffect(() => {
    if (!runs.some((r) => r.status !== "CONCLUIDA" && r.status !== "FALHOU")) return;
    const t = setTimeout(load, 2000);
    return () => clearTimeout(t);
  }, [runs]);

  async function start() {
    setBusy(true); setError(null);
    try {
      const r = await api<RunSummary>(`/api/tasks/${id}/runs`, { method: "POST", json: { condition, promptStrategy: strategy, evaluator } });
      router.push(`/runs/${r.id}`);
    } catch (e) { setError((e as Error).message); setBusy(false); }
  }

  async function captureNav() {
    setCapturing(true); setError(null);
    try {
      const r = await api<{ datasetId: number }>(`/api/nav/tasks/${id}/capture`, { method: "POST" });
      router.push(`/nav/datasets/${r.datasetId}`);
    } catch (e) { setError((e as Error).message); setCapturing(false); }
  }

  if (!task) return <ErrorBox error={error} />;
  return (
    <>
      <div className="small"><Link href="/">Tarefas</Link> › <Link href={`/projects/${task.projectId}`}>{task.projectName}</Link> › tarefa</div>
      <h1>{task.title}</h1>
      <ErrorBox error={error} />
      {editing ? (
        <TaskForm initial={task} submitLabel="Salvar alterações" onSubmit={async (t) => {
          try { setTask(await api<Task>(`/api/tasks/${id}`, { method: "PUT", json: t })); setEditing(false); }
          catch (e) { setError((e as Error).message); }
        }} />
      ) : (
        <div className="card">
          <div className="kv">
            <span className="muted">Objetivo</span><span>{task.goal}</span>
            <span className="muted">Perfil</span><span>{task.userProfile}</span>
            {task.expectedKnowledge && (<><span className="muted">Conhecimento esperado</span><span>{task.expectedKnowledge}</span></>)}
            <span className="muted">URL inicial</span><span>{task.startUrl}</span>
          </div>
          <ol>{task.steps.map((s, i) => <li key={i}>{s.type === "CLICK" ? "Clicar em" : "Localizar"} “{s.target}”{s.description ? <span className="muted"> ({s.description})</span> : null}</li>)}</ol>
          <button className="ghost" onClick={() => setEditing(true)}>Editar tarefa</button>
        </div>
      )}

      <h2>Modo NAV · previsão do primeiro clique</h2>
      <div className="card">
        <p className="small">
          Captura só a página inicial da tarefa, sem seguir a rota cadastrada, e cria um estado no conjunto
          “tarefa-{task.id}”. Lá você congela previsões (B0, B1…) antes de qualquer coleta com pessoas.
          O preditor recebe o objetivo, a tela e as opções visíveis; nunca os passos acima.
        </p>
        <button className="ghost" onClick={captureNav} disabled={capturing}>{capturing ? "Capturando…" : "Capturar página inicial para o NAV"}</button>
      </div>

      <details className="secondary" style={{ marginTop: 28 }}>
        <summary><h2 style={{ display: "inline" }}>Avaliação por percurso cognitivo (versão anterior)</h2></summary>
        <p className="small muted">
          Fluxo da primeira versão do protótipo: a IA percorre a rota cadastrada e aplica as perguntas do percurso cognitivo
          a cada passo. Continua disponível para consulta, mas não faz parte do experimento atual, que é o modo NAV.
        </p>
      <h3>Nova execução</h3>
      <div className="card">
        <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fill,minmax(220px,1fr))" }}>
          <div><label>Condição</label>
            <select value={condition} onChange={(e) => setCondition(e.target.value as Condition)}>
              {(["C1", "C2", "C3"] as Condition[]).map((c) => <option key={c} value={c}>{conditionLabel[c]}</option>)}
            </select></div>
          <div><label>Estratégia de prompt</label>
            <select value={strategy} onChange={(e) => setStrategy(e.target.value as Strategy)}>
              {(status?.strategies || ["ZERO_SHOT"]).map((s) => <option key={s} value={s}>{strategyLabel[s]}</option>)}
            </select></div>
          <div><label>Avaliador</label>
            <select value={evaluator} onChange={(e) => setEvaluator(e.target.value)}>
              <option value="claude" disabled={!status?.claudeConfigured}>Claude ({status?.model}){status?.claudeConfigured ? "" : " – sem chave"}</option>
              <option value="offline">Offline (heurística de teste)</option>
            </select></div>
        </div>
        {strategy === "FEW_SHOT" && status?.exampleBank.illustrative && (
          <div className="notice">O banco de exemplos atual ({status.exampleBank.version}) é ilustrativo. Use few-shot só para testar o pipeline até haver casos observados com participantes.</div>
        )}
        {evaluator === "offline" && <p className="small muted">O avaliador offline aplica regras simples para testar o fluxo. Ele não é o método da pesquisa.</p>}
        <button onClick={start} disabled={busy} style={{ marginTop: 10 }}>{busy ? "Iniciando…" : "Executar percurso"}</button>
      </div>

      <h3>Execuções desta tarefa</h3>
      <div className="card" style={{ overflowX: "auto" }}>
        <table>
          <thead><tr><th>#</th><th>Condição</th><th>Prompt</th><th>Avaliador</th><th>Status</th><th>Alertas</th><th>Tokens</th><th>Criada</th></tr></thead>
          <tbody>
            {runs.map((r) => (
              <tr key={r.id}>
                <td><Link href={`/runs/${r.id}`}>#{r.id}</Link>{r.sourceRunId ? <div className="small muted">reavaliação de #{r.sourceRunId}</div> : null}</td>
                <td>{r.condition}</td><td className="small">{strategyLabel[r.promptStrategy]}</td>
                <td className="small">{r.evaluator}</td><td><StatusChip status={r.status} /></td>
                <td>{r.alertCount} <span className="small muted">({r.suspectCount} suspeitas)</span></td>
                <td className="small">{r.inputTokens + r.outputTokens}</td><td className="small">{fmtDate(r.createdAt)}</td>
              </tr>
            ))}
            {runs.length === 0 && <tr><td colSpan={8} className="muted">Nenhuma execução.</td></tr>}
          </tbody>
        </table>
      </div>
      </details>
    </>
  );
}
