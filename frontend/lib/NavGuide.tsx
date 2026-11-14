const steps = ["Escolher conjunto", "Configurar rodada", "Interpretar previsões"];

export function NavGuide({ step, message }: { step: 1 | 2 | 3; message: string }) {
  return (
    <section className="nav-guide" aria-label="Etapas da revisão">
      <ol>{steps.map((title, i) => (
        <li key={title} aria-current={i + 1 === step ? "step" : undefined}>
          <span className="nav-step-index" aria-hidden="true">{i + 1}</span>{title}
        </li>
      ))}</ol>
      <p role="status">{message}</p>
    </section>
  );
}
