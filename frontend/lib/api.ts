export type Condition = "C1" | "C2" | "C3";
export type Strategy = "ZERO_SHOT" | "FEW_SHOT" | "CHAIN_OF_THOUGHT";
export type RunStatus = "PENDENTE" | "NAVEGANDO" | "AVALIANDO" | "CONCLUIDA" | "FALHOU";
export type Decision = "PENDENTE" | "PROCEDENTE" | "IMPROCEDENTE" | "INCONCLUSIVO";
export type Criterion = "Q1" | "Q2" | "Q3" | "Q4";

export interface Status {
  defaultEvaluator: string;
  claudeConfigured: boolean;
  model: string;
  effort: string;
  cacheMode: string;
  conditions: { id: Condition; history: boolean; verification: boolean }[];
  strategies: Strategy[];
  criteria: { id: Criterion; label: string; question: string }[];
  exampleBank: { version: string; size: number; illustrative: boolean };
  demoPortal: boolean;
}

export interface Project {
  id: number; name: string; baseUrl: string; allowedHosts: string; notes?: string; createdAt: string; taskCount: number;
}

export interface TaskStep { type: "CLICK" | "LOCATE"; target: string; description?: string | null }

export interface Task {
  id: number; projectId: number; projectName: string; title: string; goal: string; userProfile: string;
  expectedKnowledge?: string | null; startUrl: string; steps: TaskStep[]; createdAt: string;
}

export interface RunSummary {
  id: number; taskId: number; taskTitle: string; projectId: number; projectName: string;
  condition: Condition; promptStrategy: Strategy; evaluator: string; model: string; effort?: string | null;
  promptVersion: string; status: RunStatus; error?: string | null; sourceRunId?: number | null;
  inputTokens: number; outputTokens: number; llmMillis: number;
  createdAt: string; startedAt?: string | null; finishedAt?: string | null;
  stepCount: number; alertCount: number; suspectCount: number; pendingReview: number;
}

export interface UiElement {
  id: string; tag: string; role: string; text: string; href: string; inViewport: boolean; insideForm: boolean;
  x: number; y: number; width: number; height: number;
}

export interface QuestionAnswer { criterion: Criterion; answer: "SIM" | "NAO" | "INCERTO"; justification: string }

export interface StepView {
  index: number; url: string; title: string; action: TaskStep | null; targetElementId?: string | null;
  targetCandidates: number; screenshotUrl?: string; fullpageUrl?: string; htmlUrl?: string;
  headings: string[]; elements: UiElement[];
  assessment?: { reasoning?: string; answers: QuestionAnswer[]; alerts: unknown[] } | null;
  promptSha?: string; promptSystem?: string; promptUser?: string; rawResponse?: string;
  inputTokens: number; outputTokens: number; latencyMs: number; cacheHit: boolean; evalError?: string | null;
}

export interface AlertView {
  id: number; code: string; stepIndex: number; criterion: Criterion; criterionLabel: string;
  visualEvidence: string; diagnosis: string; citedElements: string[]; recommendation: string;
  modelConfidence?: number | null; state: "SUSPEITA" | "NAO_CONFIRMADA"; verificationNote?: string | null;
  reviewDecision: Decision; reviewNote?: string | null; reviewer?: string | null; reviewSeconds?: number | null;
  reviewedAt?: string | null;
}

export interface RunDetail {
  summary: RunSummary; task: Task; config: Record<string, unknown>;
  examples?: { bankVersion: string; ids: string[]; origins: string[]; excludedSameSystem: string[] } | null;
  steps: StepView[]; alerts: AlertView[];
}

export async function api<T>(path: string, init?: RequestInit & { json?: unknown }): Promise<T> {
  const opts: RequestInit = { ...init, cache: "no-store" };
  if (init?.json !== undefined) {
    opts.body = JSON.stringify(init.json);
    opts.headers = { "content-type": "application/json", ...(init.headers || {}) };
  }
  const res = await fetch(path, opts);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) throw new Error((data && data.error) || `Erro ${res.status}`);
  return data as T;
}

export const strategyLabel: Record<Strategy, string> = {
  ZERO_SHOT: "Zero-shot",
  FEW_SHOT: "Few-shot (casos de outros sistemas)",
  CHAIN_OF_THOUGHT: "Raciocínio explícito (CoT)",
};

export const conditionLabel: Record<Condition, string> = {
  C1: "C1 · tela isolada",
  C2: "C2 · com histórico da navegação",
  C3: "C3 · histórico + verificação estruturada",
};

export const statusLabel: Record<RunStatus, string> = {
  PENDENTE: "Na fila", NAVEGANDO: "Navegando", AVALIANDO: "Avaliando", CONCLUIDA: "Concluída", FALHOU: "Falhou",
};

export function fmtDate(s?: string | null) {
  if (!s) return "–";
  return new Date(s).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}
