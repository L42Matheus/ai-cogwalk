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
            <Link href="/" className="brand">AI-CogWalk<span>MVP 1.0 · percurso cognitivo com IA</span></Link>
          </div>
        </div>
        <main>{children}</main>
      </body>
    </html>
  );
}
