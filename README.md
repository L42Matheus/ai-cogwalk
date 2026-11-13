# AI-CogWalk (MVP 1.0)

Percurso cognitivo (Q1–Q4) apoiado por LLM para antecipar dificuldades de usuários iniciantes em interfaces web.
Protótipo de pesquisa (PPGTI/IFPB). Os alertas são suspeitas, não problemas confirmados.

## O que faz
- Cadastro de projetos (site + hosts autorizados) e tarefas (objetivo, perfil, sequência correta de ações).
- Navegação controlada com Playwright: só GET nos hosts autorizados, nunca clica em formulários, intervalo entre ações.
  Captura a tela visível, a página inteira, o HTML e os elementos interativos de cada etapa.
- Avaliação Q1–Q4 por etapa com Claude ou com um avaliador offline (heurística de teste, sem custo).
  - Condições: C1 (tela isolada), C2 (com histórico), C3 (C2 + verificação estruturada dos alertas).
  - Estratégias de prompt: zero-shot, few-shot (banco versionado de casos de outros sistemas, que exclui o sistema avaliado) e CoT.
- Registro para reprodução: versão do prompt, texto enviado, hash, modelo, esforço, resposta bruta, tokens e tempo.
  Há um cache de respostas (off, read-write, replay) e a reavaliação usa as mesmas capturas.
- Revisão humana por alerta (procedente, improcedente, inconclusivo), com o tempo gasto.
- Relatórios em HTML (telas embutidas), Markdown, JSON e ZIP com tudo.

## Rodar localmente
```bash
cp .env.example .env            # opcional: ANTHROPIC_API_KEY e AICOGWALK_EVALUATOR=claude
docker compose up --build       # painel em http://localhost:3000
```
Sem Docker: PostgreSQL 16 local, `cd backend && ./gradlew bootRun` (defina AICOGWALK_CHROMIUM_PATH se o Chromium do
Playwright não estiver instalado) e `cd frontend && npm install && BACKEND_URL=http://localhost:8080 npm run dev`.
Testes: `cd backend && ./gradlew test`. O teste de ponta a ponta usa H2 e o portal fictício.

## Deploy no Railway
1. Suba este repositório no GitHub e crie um projeto no Railway.
2. Adicione um banco **PostgreSQL**.
3. Crie o serviço **backend** a partir do repositório, com Root Directory `backend` (usa `backend/Dockerfile` e `railway.json`).
   - Variáveis: `PGHOST=${{Postgres.PGHOST}}`, `PGPORT=${{Postgres.PGPORT}}`, `PGDATABASE=${{Postgres.PGDATABASE}}`,
     `PGUSER=${{Postgres.PGUSER}}`, `PGPASSWORD=${{Postgres.PGPASSWORD}}`.
   - Opcionais: `ANTHROPIC_API_KEY` e `AICOGWALK_EVALUATOR=claude`.
   - Anexe um **Volume** montado em `/data`, onde ficam as capturas e o cache. Sem ele, as capturas somem a cada deploy.
4. Crie o serviço **frontend** com Root Directory `frontend`.
   - Variável: `BACKEND_URL=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:${{backend.PORT}}`.
   - Gere um domínio público só para o frontend. O backend fica privado.

## Variáveis principais
| Variável | Padrão | Uso |
|---|---|---|
| `AICOGWALK_EVALUATOR` | `offline` | `claude` ou `offline` |
| `AICOGWALK_MODEL` / `AICOGWALK_EFFORT` | `claude-opus-5-5` / `high` | modelo e esforço |
| `AICOGWALK_LLM_CACHE` | `read-write` | `off`, `read-write`, `replay` |
| `AICOGWALK_STEP_DELAY_MS` | `2500` | intervalo entre ações no site |
| `AICOGWALK_STORAGE_DIR` | `./data` (`/data` no Docker) | capturas e cache |
| `AICOGWALK_SEED` | `true` | cadastra o portal fictício e o caso do IFPB num banco vazio |

## Limites éticos
Navegue apenas em páginas públicas, com autorização, sem cadastro, sem envio de formulários e sem alterar dados.
A coleta com participantes só começa após aprovação do CEP.
O banco de exemplos few-shot atual é **ilustrativo**: substitua-o por casos observados antes de usar few-shot em resultados.
