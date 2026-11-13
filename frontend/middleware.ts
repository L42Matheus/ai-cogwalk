// Senha do painel (HTTP Basic). Ative definindo AICOGWALK_PANEL_PASSWORD no serviço do frontend.
// Sem a variável, o painel fica aberto (uso local). O usuário pode ser qualquer nome.
import { NextRequest, NextResponse } from "next/server";

export const config = { matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"] };

function safeEqual(a: string, b: string) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export function middleware(req: NextRequest) {
  const password = process.env.AICOGWALK_PANEL_PASSWORD;
  if (!password) return NextResponse.next();
  const header = req.headers.get("authorization") || "";
  if (header.startsWith("Basic ")) {
    try {
      const decoded = atob(header.slice(6));
      const given = decoded.slice(decoded.indexOf(":") + 1);
      if (safeEqual(given, password)) return NextResponse.next();
    } catch {
      // cabeçalho malformado: cai no pedido de senha
    }
  }
  return new NextResponse("Autenticação necessária.", {
    status: 401,
    headers: { "WWW-Authenticate": 'Basic realm="AI-CogWalk", charset="UTF-8"' },
  });
}
