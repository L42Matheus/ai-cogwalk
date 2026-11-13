// Encaminha /api/* para o backend Spring Boot. BACKEND_URL é lida em tempo de execução
// (no Railway, aponte para o domínio privado do serviço backend).
import { NextRequest } from "next/server";

export const dynamic = "force-dynamic";

const backend = () => (process.env.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");

async function proxy(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const url = `${backend()}/api/${path.map(encodeURIComponent).join("/")}${req.nextUrl.search}`;
  const headers = new Headers();
  const ct = req.headers.get("content-type");
  if (ct) headers.set("content-type", ct);
  const init: RequestInit = { method: req.method, headers, cache: "no-store" };
  if (req.method !== "GET" && req.method !== "HEAD") init.body = await req.arrayBuffer();
  let res: Response;
  try {
    res = await fetch(url, init);
  } catch {
    return Response.json({ error: "Backend indisponível em " + backend() }, { status: 502 });
  }
  const out = new Headers();
  for (const h of ["content-type", "content-disposition", "cache-control"]) {
    const v = res.headers.get(h);
    if (v) out.set(h, v);
  }
  return new Response(res.body, { status: res.status, headers: out });
}

export { proxy as GET, proxy as POST, proxy as PUT, proxy as DELETE };
