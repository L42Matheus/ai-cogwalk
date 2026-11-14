# AI-CogWalk (MVP 1.1)

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

## Modo NAV (AI-CogWalk-NAV)
Prevê a distribuição do **primeiro clique** de iniciantes, P(a | tela, tarefa), e a compara com escolhas humanas.
O preditor recebe só a tarefa, a tela e as opções visíveis: nunca a rota correta, o alvo ou dados humanos.
- **Métodos:** UNIFORM (referência); B0 (similaridade MiniLM tarefa × rótulo, softmax com τ ajustado no DEV, sem LLM);
  B1 (LLM, distribuição direta); B1-CW (B1 + Q1–Q3 do percurso cognitivo; Q4 não se aplica ao primeiro clique);
  B1-S e B1-CW-S (N participantes simulados, chamadas independentes).
- **Dados:** importador do dataset de Kuric et al. (2025, CC BY-NC 4.0), com 60 estados (6 variantes × 10 tarefas).
  As tarefas pares formam o DEV e as ímpares o TEST. As telas dos protótipos são renderizadas com relógio simulado, para saírem idênticas a cada importação.
  Também há captura independente da página inicial de uma tarefa do modo CW (estado de piloto, sem dados humanos).
- **Métricas:** JSD (principal), cobertura, top-1, opções omitidas, entropias, log-loss e Brier, com IC por bootstrap de tarefas.
- **Previsões congeladas:** cada rodada guarda prompts, respostas, tokens, custo estimado e um hash SHA-256 das
  entradas e distribuições. O painel fica em `/nav`, com exportação JSON e CSV.
- **Conferência independente em Python:** `analysis/verify_b0_metrics.py` (B0 e métricas) e
  `analysis/nav_kuric_analysis.py` (diferença pareada e referência entre variantes).
  As exportações das rodadas de 13/11/2026 estão em `analysis/resultados-2026-11-13/`.

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
   - Variáveis: `PORT=8080`, `PGHOST=${{Postgres.PGHOST}}`, `PGPORT=${{Postgres.PGPORT}}`, `PGDATABASE=${{Postgres.PGDATABASE}}`,
     `PGUSER=${{Postgres.PGUSER}}`, `PGPASSWORD=${{Postgres.PGPASSWORD}}`.
   - Opcionais: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY` (modo NAV com GPT) e `AICOGWALK_EVALUATOR=claude`.
   - Anexe um **Volume** montado em `/data`, onde ficam as capturas e o cache. Sem ele, as capturas somem a cada deploy.
4. Crie o serviço **frontend** com Root Directory `frontend`.
   - Variável: `BACKEND_URL=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:${{backend.PORT}}`.
   - Gere um domínio público só para o frontend. O backend fica privado.
   - Defina `AICOGWALK_PANEL_PASSWORD` para exigir senha no painel (HTTP Basic, qualquer usuário) antes de cadastrar chaves pagas.

## Variáveis principais
| Variável | Padrão | Uso |
|---|---|---|
| `AICOGWALK_EVALUATOR` | `offline` | `claude` ou `offline` |
| `AICOGWALK_MODEL` / `AICOGWALK_EFFORT` | `claude-opus-5-5` / `high` | modelo e esforço |
| `AICOGWALK_LLM_CACHE` | `read-write` | `off`, `read-write`, `replay` |
| `AICOGWALK_STEP_DELAY_MS` | `2500` | intervalo entre ações no site |
| `AICOGWALK_STORAGE_DIR` | `./data` (`/data` no Docker) | capturas e cache |
| `AICOGWALK_SEED` | `true` | cadastra o portal fictício e o caso do IFPB num banco vazio |
| `AICOGWALK_PANEL_PASSWORD` (frontend) | vazio | senha do painel; vazio = aberto (uso local) |
| `OPENAI_API_KEY` | vazio | modelos `gpt-*` no modo NAV |
| `AICOGWALK_NAV_SAMPLES` | `30` | participantes simulados por estado (B1-S) |
| `AICOGWALK_NAV_EFFORT` | `medium` | esforço das chamadas do modo NAV |
| `AICOGWALK_NAV_MAX_CALLS` | `2500` | teto de chamadas pagas por rodada |
| `AICOGWALK_NAV_PARALLEL` | `6` | chamadas simultâneas |
| `AICOGWALK_KURIC_DIR` | vazio | cópia local do dataset Kuric; vazio = baixa a revisão fixada do GitHub |

## Limites éticos
Navegue apenas em páginas públicas, com autorização, sem cadastro, sem envio de formulários e sem alterar dados.
A coleta com participantes só começa após aprovação do CEP.
O banco de exemplos few-shot atual é **ilustrativo**: substitua-o por casos observados antes de usar few-shot em resultados.

## Testar a pergunta do artigo com GPT-4.1

Na raiz do projeto, execute `python3 analysis/piloto_gpt.py` para ver um exemplo já registrado: a tarefa da recomendação de comédia, os cliques humanos e o que o GPT previu em cada opção. Esse comando funciona sem chave e sem Docker.

Para gerar previsões com sua conta:

1. Copie `.env.example` para `.env` se ainda não existir e preencha `OPENAI_API_KEY` no arquivo local.
2. Execute `docker compose up -d --build backend`. O Docker inicia também o PostgreSQL. Aguarde o backend em `http://localhost:8080/api/nav/status`.
3. Execute `python3 analysis/piloto_gpt.py --executar`. O script importa o benchmark se necessário e testa a mesma tarefa de desenvolvimento em três condições: resposta direta (B1), perguntas do percurso cognitivo (B1-CW) e análise livre de cada opção (B1-R). São três previsões planejadas. O backend pode reutilizar o cache ou repetir chamadas após falhas.
4. Leia as porcentagens no terminal. Cada JSON da rodada fica em `resultados-gpt/`. A coluna modelo é a probabilidade prevista. A coluna pessoas mostra quantos dos 30 participantes clicaram ali. A JSD resume a distância entre as duas distribuições.

Para incluir a simulação individual, use `python3 analysis/piloto_gpt.py --executar --amostras 30`. Essa opção acrescenta uma rodada B1-S com 30 escolhas planejadas. Para acompanhar pelo painel, inicie também o frontend com `docker compose up -d --build frontend` e abra `http://localhost:3000/nav`.

Para o mesmo piloto com Claude, configure também `ANTHROPIC_API_KEY` no backend e use `python3 analysis/piloto_gpt.py --executar --modelo claude-opus-5-5`.
