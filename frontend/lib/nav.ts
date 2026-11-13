// Tipos e utilitários do modo NAV (predição de escolhas de navegação).

export type NavMethod = "UNIFORM" | "B0" | "B1" | "B1_CW" | "B1_S" | "B1_CW_S";
export type NavRunStatus = "PENDENTE" | "NAVEGANDO" | "AVALIANDO" | "CONCLUIDA" | "FALHOU";

export interface NavStatus {
  importStatus: string;
  methods: { id: NavMethod; llm: boolean; sampled: boolean; cognitive: boolean; description: string }[];
  anthropicConfigured: boolean;
  openaiConfigured: boolean;
  promptVersion: string;
}

export interface NavOption { id: string; label: string; x?: number; y?: number; width?: number; height?: number }

export interface NavState {
  id: number; key: string; variant: string; modality: "TEXT" | "VISUAL"; task: string; taskText: string;
  split: string; options: NavOption[]; humanCounts: Record<string, number>; humanN: number;
  screenshot?: string | null; viewport?: string | null; inputHash: string;
}

export interface Agg { states: number; clusters: number; mean: number; lo: number; hi: number }
export type Metrics = Record<string, Agg>;

export interface NavRun {
  id: number; datasetId: number; method: NavMethod; model?: string | null; status: NavRunStatus; error?: string | null;
  statesTotal: number; statesDone: number; frozenHash?: string | null; inputTokens: number; outputTokens: number;
  llmCalls: number; createdAt: string; finishedAt?: string | null; headline?: Metrics | null;
}

export interface NavDataset {
  id: number; code: string; name: string; sourceUrl?: string | null; revision?: string | null; license?: string | null;
  notes?: string | null; createdAt: string; states?: number; stateList?: NavState[]; runs?: NavRun[];
}

export interface StateMetrics {
  humanN: number; top1: number; jsd: number; coverage: number; omitted: number; entropyPred: number;
  entropyHuman: number; logLoss: number; brier: number; samplingFloorJsd: number;
}

export interface NavPrediction {
  id: number; state: NavState; distribution: Record<string, number>; samples?: string[] | null;
  metrics?: StateMetrics | null; promptSha?: string | null; inputTokens: number; outputTokens: number;
  cacheHits: number; error?: string | null;
}

export interface NavRunDetail extends NavRun {
  datasetName: string; datasetCode: string; config: Record<string, unknown> | null;
  summary: { groups?: Record<string, Metrics>; byVariant?: Record<string, Metrics>; [k: string]: unknown } | null;
  predictions: NavPrediction[];
}

export const methodLabel: Record<NavMethod, string> = {
  UNIFORM: "Uniforme (referência)",
  B0: "B0 · similaridade semântica",
  B1: "B1 · LLM, distribuição direta",
  B1_CW: "B1-CW · LLM + Q1–Q3",
  B1_S: "B1-S · participantes simulados",
  B1_CW_S: "B1-CW-S · simulados + Q1–Q3",
};

/** Métricas exibidas, na ordem do paper. `better` diz se valores menores ou maiores são melhores. */
export const metricInfo: { key: string; label: string; better: "low" | "high" | "none"; hint: string }[] = [
  { key: "jsd", label: "JSD", better: "low", hint: "Divergência de Jensen–Shannon (base 2) entre a distribuição humana e a prevista. Métrica principal." },
  { key: "coverage", label: "Cobertura", better: "high", hint: "Fração das pessoas cujas escolhas caíram em opções que o preditor considerou (P̂ ≥ τ)." },
  { key: "top1", label: "Top-1", better: "high", hint: "Concordância entre a opção mais provável prevista e a mais escolhida (empates contam fração)." },
  { key: "omitted", label: "Omitidas", better: "low", hint: "Opções escolhidas por ≥ 10% das pessoas às quais o preditor deu P̂ < τ." },
  { key: "entropyPred", label: "Entropia prev.", better: "none", hint: "Entropia normalizada da previsão (0 = uma opção só; 1 = uniforme)." },
  { key: "entropyHuman", label: "Entropia hum.", better: "none", hint: "Entropia normalizada das escolhas humanas." },
  { key: "logLoss", label: "Log-loss", better: "low", hint: "Perda logarítmica (bits por escolha), com 1% de massa uniforme." },
  { key: "samplingFloorJsd", label: "Piso amostral", better: "none", hint: "JSD esperada só pelo tamanho da amostra humana; nenhum preditor fica abaixo disso em média." },
];

/** Preços de referência por milhão de tokens (entrada, saída), em US$, para estimar custo no painel. */
export const priceTable: Record<string, [number, number]> = {
  "claude-opus-5-5": [4, 20],
  "gpt-4.1": [2, 8],
};

export function cost(model: string | null | undefined, inTok: number, outTok: number): number | null {
  if (!model) return 0;
  const p = priceTable[model];
  if (!p) return null;
  return (inTok * p[0] + outTok * p[1]) / 1e6;
}

export function fmt(x: number | null | undefined, d = 3) {
  if (x === null || x === undefined || Number.isNaN(x)) return "–";
  return x.toFixed(d).replace(".", ",");
}

export function fmtAgg(a?: Agg | null, d = 3) {
  if (!a || Number.isNaN(a.mean)) return "–";
  const ci = Number.isNaN(a.lo) || a.lo === null ? "" : ` [${fmt(a.lo, d)}; ${fmt(a.hi, d)}]`;
  return `${fmt(a.mean, d)}${ci}`;
}

export function normalize(ids: string[], raw: Record<string, number>) {
  const s = ids.reduce((acc, id) => acc + Math.max(0, raw[id] || 0), 0);
  const out: Record<string, number> = {};
  ids.forEach((id) => { out[id] = s > 0 ? Math.max(0, raw[id] || 0) / s : 1 / ids.length; });
  return out;
}
