"use client";
import { RunStatus } from "./api";

export function StatusChip({ status }: { status: RunStatus }) {
  const cls = status === "CONCLUIDA" ? "ok" : status === "FALHOU" ? "bad" : "warn";
  const label = { PENDENTE: "Na fila", NAVEGANDO: "Navegando", AVALIANDO: "Avaliando", CONCLUIDA: "Concluída", FALHOU: "Falhou" }[status];
  return <span className={`chip ${cls}`}>{label}</span>;
}

export function ErrorBox({ error }: { error?: string | null }) {
  return error ? <div className="error">{error}</div> : null;
}
