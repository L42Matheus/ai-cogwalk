"use client";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, Project, Task } from "@/lib/api";
import { emptyTask, TaskForm } from "@/lib/TaskForm";
import { ErrorBox } from "@/lib/ui";

export default function ProjectPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [project, setProject] = useState<Project | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);

  const load = () => Promise.all([api<Project>(`/api/projects/${id}`), api<Task[]>(`/api/projects/${id}/tasks`)])
    .then(([p, t]) => { setProject(p); setTasks(t); }).catch((e) => setError(e.message));
  useEffect(() => { load(); }, [id]);

  async function remove() {
    if (!confirm("Excluir o projeto, suas tarefas e execuções?")) return;
    await api(`/api/projects/${id}`, { method: "DELETE" });
    router.push("/");
  }

  if (!project) return <ErrorBox error={error} />;
  return (
    <>
      <div className="small"><Link href="/">Tarefas</Link> › {project.name}</div>
      <h1>{project.name}</h1>
      <ErrorBox error={error} />
      <div className="card kv">
        <span className="muted">URL base</span><span>{project.baseUrl}</span>
        <span className="muted">Hosts autorizados</span><span>{project.allowedHosts}</span>
        {project.notes && (<><span className="muted">Observações</span><span>{project.notes}</span></>)}
      </div>
      <div className="row"><h2>Tarefas</h2><span className="spacer" />
        <button className="ghost" onClick={() => setOpen(!open)}>{open ? "Cancelar" : "Nova tarefa"}</button></div>
      {open && (
        <TaskForm initial={emptyTask(project.baseUrl)} submitLabel="Criar tarefa" onSubmit={async (t) => {
          try { const created = await api<Task>(`/api/projects/${id}/tasks`, { method: "POST", json: t }); router.push(`/tasks/${created.id}`); }
          catch (e) { setError((e as Error).message); }
        }} />
      )}
      {tasks.map((t) => (
        <Link key={t.id} href={`/tasks/${t.id}`} className="card" style={{ display: "block", textDecoration: "none", color: "inherit" }}>
          <h3>{t.title}</h3>
          <div className="small muted">{t.steps.length} ação(ões) · início em {t.startUrl}</div>
        </Link>
      ))}
      {tasks.length === 0 && <p className="muted">Nenhuma tarefa cadastrada.</p>}
      <p style={{ marginTop: 30 }}><button className="ghost" onClick={remove}>Excluir projeto</button></p>
    </>
  );
}
