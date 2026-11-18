"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { api, fmtDate, Project, RunSummary, Status, strategyLabel } from "@/lib/api";
import { ErrorBox, StatusChip } from "@/lib/ui";

export default function Dashboard() {
  const [status, setStatus] = useState<Status | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({ name: "", baseUrl: "", allowedHosts: "", notes: "" });
  const [open, setOpen] = useState(false);

  const load = () =>
    Promise.all([api<Status>("/api/status"), api<Project[]>("/api/projects"), api<RunSummary[]>("/api/runs")])
      .then(([s, p, r]) => { setStatus(s); setProjects(p); setRuns(r); setError(null); })
      .catch((e) => setError(e.message));

  useEffect(() => { load(); }, []);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    try {
      await api("/api/projects", { method: "POST", json: form });
      setForm({ name: "", baseUrl: "", allowedHosts: "", notes: "" });
      setOpen(false);
      load();
    } catch (err) { setError((err as Error).message); }
  }

  return (
    <>
      <h1>Projetos e tarefas</h1>
      <section className="nav-entry">
        <div><h2>Revisar o primeiro clique</h2>
          <p className="muted">Explore os menus do benchmark ou analise a página inicial de uma tarefa cadastrada.</p></div>
        <Link className="btn" href="/nav">Abrir revisão guiada →</Link>
      </section>
      <p className="muted">Para revisar seu portal, abra um projeto, escolha uma tarefa e use “Capturar página inicial para o NAV”.</p>
      <ErrorBox error={error} />

      <div className="row"><h2>Projetos</h2><span className="spacer" />
        <button className="ghost" onClick={() => setOpen(!open)}>{open ? "Cancelar" : "Novo projeto"}</button></div>
      {open && (
        <form className="card" onSubmit={create}>
          <label>Nome</label><input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <label>URL base</label><input required type="url" placeholder="https://www.exemplo.edu.br" value={form.baseUrl} onChange={(e) => setForm({ ...form, baseUrl: e.target.value })} />
          <label>Hosts autorizados (separados por vírgula; subdomínios incluídos)</label>
          <input required placeholder="exemplo.edu.br" value={form.allowedHosts} onChange={(e) => setForm({ ...form, allowedHosts: e.target.value })} />
          <label>Observações (autorização, limites de acesso)</label>
          <textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
          <p className="small muted">A navegação só faz requisições GET nos hosts autorizados, nunca preenche formulários e espera entre as ações.</p>
          <button type="submit">Criar projeto</button>
        </form>
      )}
      <div className="grid">
        {projects.map((p) => (
          <Link key={p.id} href={`/projects/${p.id}`} className="card" style={{ textDecoration: "none", color: "inherit" }}>
            <h3>{p.name}</h3>
            <div className="small muted">{p.baseUrl}</div>
            <div className="small" style={{ marginTop: 8 }}>{p.taskCount} tarefa(s) · hosts: {p.allowedHosts}</div>
          </Link>
        ))}
        {projects.length === 0 && !error && <p className="muted">Nenhum projeto ainda.</p>}
      </div>

      <details className="secondary" style={{ marginTop: 28 }}>
        <summary><h2 style={{ display: "inline" }}>Avaliação por percurso cognitivo (versão anterior)</h2></summary>
      {status && (
        <div className="card row small">
          <span>Avaliador padrão: <b>{status.defaultEvaluator}</b></span>
          <span className={`chip ${status.claudeConfigured ? "ok" : "warn"}`}>
            {status.claudeConfigured ? `Claude configurado (${status.model}, esforço ${status.effort})` : "ANTHROPIC_API_KEY ausente: só o avaliador offline"}
          </span>
          <span className="muted">Cache de respostas: {status.cacheMode}</span>
          <span className="muted">Banco few-shot: {status.exampleBank.version}{status.exampleBank.illustrative ? " (ilustrativo)" : ""}</span>
        </div>
      )}
      <h3>Execuções recentes</h3>
      <div className="card" style={{ overflowX: "auto" }}>
        <table>
          <thead><tr><th>#</th><th>Tarefa</th><th>Condição</th><th>Prompt</th><th>Avaliador</th><th>Status</th><th>Alertas</th><th>Criada</th></tr></thead>
          <tbody>
            {runs.map((r) => (
              <tr key={r.id}>
                <td><Link href={`/runs/${r.id}`}>#{r.id}</Link></td>
                <td>{r.taskTitle}<div className="small muted">{r.projectName}</div></td>
                <td>{r.condition}</td>
                <td className="small">{strategyLabel[r.promptStrategy]}</td>
                <td className="small">{r.evaluator}<div className="muted">{r.model}</div></td>
                <td><StatusChip status={r.status} /></td>
                <td>{r.alertCount} <span className="small muted">({r.pendingReview} a revisar)</span></td>
                <td className="small">{fmtDate(r.createdAt)}</td>
              </tr>
            ))}
            {runs.length === 0 && <tr><td colSpan={8} className="muted">Nenhuma execução ainda.</td></tr>}
          </tbody>
        </table>
      </div>
      </details>
    </>
  );
}
