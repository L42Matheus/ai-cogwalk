"use client";
import { NavOption, normalize } from "./nav";

/** Barras pareadas por opção: proporção humana observada × probabilidade prevista. */
export function NavBars({ options, human, humanN, predicted }: {
  options: NavOption[]; human: Record<string, number>; humanN: number; predicted?: Record<string, number> | null;
}) {
  const ids = options.map((o) => o.id);
  const h = humanN > 0 ? normalize(ids, human) : null;
  const p = predicted ? normalize(ids, predicted) : null;
  return (
    <div className="bars" role="table" aria-label="Escolhas humanas e previstas por opção">
      {options.map((o) => (
        <div className="bar-row" role="row" key={o.id}>
          <span className="bar-label" role="cell" title={o.label}>{o.label || "(sem texto)"}</span>
          <span className="bar-track" role="cell">
            {h && <span className="bar human" style={{ width: `${(h[o.id] * 100).toFixed(1)}%` }} />}
            {p && <span className="bar pred" style={{ width: `${(p[o.id] * 100).toFixed(1)}%` }} />}
          </span>
          <span className="bar-num small" role="cell">
            {h ? `${Math.round(h[o.id] * 100)}%` : ""}{h && p ? " · " : ""}{p ? `${Math.round(p[o.id] * 100)}%` : ""}
          </span>
        </div>
      ))}
      <div className="legend">
        {h && <><i className="lg human" /> humanos (n={humanN})</>}
        {p && <><i className="lg pred" /> previsto</>}
      </div>
    </div>
  );
}
