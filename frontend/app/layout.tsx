import "./globals.css";
import Link from "next/link";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "AI-CogWalk",
  description: "Percurso cognitivo apoiado por IA para encontrar barreiras de iniciantes em interfaces web.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>
        <div className="top">
          <div className="in">
            <Link href="/" className="brand">AI-CogWalk<span>MVP · percurso cognitivo e previsão de navegação com IA</span></Link>
            <nav className="nav-links" aria-label="Módulos">
              <Link href="/">Tarefas</Link>
              <Link href="/nav">Modo NAV</Link>
            </nav>
          </div>
        </div>
        <main>{children}</main>
      </body>
    </html>
  );
}
