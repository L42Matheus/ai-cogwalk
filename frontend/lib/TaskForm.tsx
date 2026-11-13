"use client";
import { useState } from "react";
import { Task, TaskStep } from "./api";

export type TaskInput = Omit<Task, "id" | "projectId" | "projectName" | "createdAt">;

export const emptyTask = (startUrl = ""): TaskInput => ({
  title: "", goal: "", userProfile: "Usuário iniciante: nunca usou esta seção do site.", expectedKnowledge: "",
  startUrl, steps: [{ type: "CLICK", target: "", description: "" }],
});

export function TaskForm({ initial, onSubmit, submitLabel }: {
  initial: TaskInput; onSubmit: (t: TaskInput) => Promise<void>; submitLabel: string;
}) {
  const [t, setT] = useState<TaskInput>(initial);
  const [busy, setBusy] = useState(false);
  const setStep = (i: number, s: Partial<TaskStep>) =>
    setT({ ...t, steps: t.steps.map((x, j) => (j === i ? { ...x, ...s } : x)) });

  return (
    <form className="card" onSubmit={async (e) => { e.preventDefault(); setBusy(true); try { await onSubmit(t); } finally { setBusy(false); } }}>
      <label>Título</label><input required value={t.title} onChange={(e) => setT({ ...t, title: e.target.value })} />
      <label>Objetivo do usuário</label><textarea required value={t.goal} onChange={(e) => setT({ ...t, goal: e.target.value })} />
      <label>Perfil do usuário</label><textarea required value={t.userProfile} onChange={(e) => setT({ ...t, userProfile: e.target.value })} />
      <label>Conhecimento esperado (opcional)</label>
      <input value={t.expectedKnowledge || ""} onChange={(e) => setT({ ...t, expectedKnowledge: e.target.value })} />
      <label>URL inicial</label><input required type="url" value={t.startUrl} onChange={(e) => setT({ ...t, startUrl: e.target.value })} />
      <label>Sequência correta de ações (o texto do alvo deve ser o rótulo visível na tela)</label>
      {t.steps.map((s, i) => (
        <div className="step-row" key={i}>
          <select value={s.type} onChange={(e) => setStep(i, { type: e.target.value as TaskStep["type"] })}>
            <option value="CLICK">Clicar</option><option value="LOCATE">Localizar</option>
          </select>
          <input required placeholder="Rótulo do alvo" value={s.target} onChange={(e) => setStep(i, { target: e.target.value })} />
          <input placeholder="Descrição (opcional)" value={s.description || ""} onChange={(e) => setStep(i, { description: e.target.value })} />
          <button type="button" className="neutral" aria-label="Remover ação" disabled={t.steps.length === 1}
            onClick={() => setT({ ...t, steps: t.steps.filter((_, j) => j !== i) })}>×</button>
        </div>
      ))}
      <div className="row" style={{ marginTop: 8 }}>
        <button type="button" className="ghost" onClick={() => setT({ ...t, steps: [...t.steps, { type: "CLICK", target: "", description: "" }] })}>Adicionar ação</button>
        <span className="spacer" />
        <button type="submit" disabled={busy}>{submitLabel}</button>
      </div>
    </form>
  );
}
