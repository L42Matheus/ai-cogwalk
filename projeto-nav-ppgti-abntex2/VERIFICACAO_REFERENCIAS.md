# Verificação das referências do AI-CogWalk-NAV (versão no modelo do PPGTI)

Situação em 14/11/2026. As referências novas foram abertas na fonte em 13/11/2026 (página da editora, arXiv, ACL Anthology, PMLR, repositório institucional ou repositório do código).

## Corrigidas nesta revisão

- `jin2026scent`: os autores são Xiaofu Jin, Yunpeng Bai e Antti Oulasvirta. O arquivo anterior trazia "Xiaofei Jin" e "Yu Bai".
- `lu2025uxagent`: lista completa de autores e DOI 10.1145/3706599.3719729.
- `hu2025uitransition`: continua sendo versão preliminar; URL acrescentada.
- Duas chaves `}` sobrando no arquivo `.bib` foram removidas.
- `cns2016res510` e `brasil2018lgpd`: autor institucional em maiúsculas, como pede a ABNT.

## Já conferidas antes (sem mudança)

kuric2026wwgc, kuric2025ia, kuric2025dataset, kuric2025cardsorting, fu2007snifact, blackmon2002cww, blackmon2005autocww, tong2026mango, li2026webtraveler, chowdhury2026amuser, tan2026avenirux, zhong2025syntheticcw, polson1992cw, wharton1994cw, bisante2024cwgpt, deng2023mind2web, zhou2024webarena.

## Novas referências, conferidas na fonte

| Chave | O que foi conferido | Como é usada no texto |
|---|---|---|
| meister2025distributional | NAACL 2025, v. 1, p. 24–49. Resumo e texto. | Distribuição declarada mais fiel que a amostrada, em pesquisas de opinião. No artigo, as amostras saem numa única saída, e o texto diz isso. |
| jang2026cannotsample | arXiv:2607.25292, versão preliminar. Resumo. | Chamadas independentes repetem a mesma resposta; descrever a distribuição reduz o erro. |
| argyle2023outofone | Political Analysis 31(3). | Origem das "amostras de silício". |
| aher2023turing | ICML 2023, PMLR 202. | Distorção de hiperacurácia. |
| dillion2023replace | Trends in Cognitive Sciences 27(7). | Modelos aproximam médias, não a variação entre pessoas. |
| bisbee2024synthetic | Political Analysis 32(4). | Menos variação que a pesquisa real; instabilidade no tempo. |
| park2024diminished | Behavior Research Methods, 2024, DOI conferido. **Volume e páginas não conferidos e deixados em branco.** | Efeito da resposta correta. |
| santurkar2023whose, durmus2023globalopinions | ICML 2023; arXiv:2306.16388. | Validade externa: populações diferentes. |
| sorensen2024pluralistic | ICML 2024, PMLR 235. | Termo "pluralismo distribucional". |
| tian2023justask, xiong2024uncertainty | EMNLP 2023; ICLR 2024. | Probabilidades verbalizadas. Tratam de confiança numa resposta, e o texto diz isso. |
| zhang2025verbalizedsampling | arXiv:2510.01171. | Alternativa futura ao B1-S. |
| lin2025sixfallacies | AMPPS, 2025, DOI conferido. **Volume não conferido e deixado em branco.** | Regra de conferir contra dados humanos (D4). |
| lu2025agentab | arXiv:2504.09723. | Tabela de trabalhos relacionados. |
| chi2001scent, chi2003bloodhound | CHI 2001 e CHI 2003. **Só os metadados; os resumos não estavam disponíveis.** | Citadas só como antecedentes, sem números. |
| teo2012cogtoolexplorer | CHI 2012. Resumo. | 108 tarefas, três leiautes, 63% a 82% da variância. |
| miller2004navigation | Human-Computer Interaction 19(3). Resumo. | A melhor estrutura depende da qualidade dos rótulos. |
| bailey2009firstclick | HCII 2009, LNCS 5619. Texto. | 0,87 contra 0,46. O artigo não informa o tamanho das amostras, e o texto diz isso. |
| obrien2009treetesting | Boxes and Arrows, 2009. Texto profissional, sem revisão por pares. | Origem do tree testing. |
| cameron2008bootstrap | Review of Economics and Statistics 90(3). Resumo. | Poucos agrupamentos. O artigo trata de bootstrap-t, não do percentil. |
| mackinnon2023guide, esarey2019clustered | Journal of Econometrics 232(2); PSRM 7(3). Resumos. | Não confiar num único procedimento. |
| canay2017randomization | Econometrica 85(3). **Só o resumo; o texto integral não abriu.** | Testes de aleatorização com poucos agrupamentos, sem atribuir detalhes ao artigo. |
| good2005permutation, holm1979sequentially | Livro (Springer) e Scand. J. Statistics 6(2). **Só os metadados.** | Citações de método. |
| endres2003metric | IEEE Trans. Inf. Theory 49(7). Resumo. | A raiz da JSD é uma métrica. |
| gneiting2007proper | JASA 102(477). | Regras de pontuação próprias. |
| sainz2023contamination, golchin2024timetravel | Findings of EMNLP 2023; ICLR 2024. | Contaminação e sondagem de memorização. |
| lee2023dissenting | EMNLP 2023, p. 4569–4585. Resumo. | Alinhamento piora onde as pessoas discordam (em inferência em linguagem natural). |
| rigutti2015webparty | PeerJ 3:e828. Texto. **Prenomes dos coautores não conferidos na página.** | Só para a pergunta Q2 (visibilidade). O artigo não trata do significado dos rótulos. |
| brumby2008strategies | Human-Computer Interaction 23(1), p. 1–46, DOI conferido. **O resumo do artigo não abriu.** A afirmação usada (pessoas não examinam todas as opções; depende da relevância dos concorrentes) foi conferida em dois textos dos mesmos autores sobre os mesmos experimentos: a tese de Brumby (Cardiff, 2005) e Brumby e Howes (ICCM 2004). | Justificativa do controle B1-R. Vale abrir o artigo antes de uma submissão. |

## Pontos de atenção

- `kharchenko2026early`: metadados conferem, mas a fonte é pouco confiável. Aparece só na tabela de trabalhos relacionados e não sustenta nenhum argumento.
- `li2026webtraveler`: vale clicar no DOI antes de uma submissão.
- Escritas de memória e ainda não conferidas online (clássicas): lin1991jsd, pirolli1999foraging, reimers2019sbert, wang2020minilm, brier1950, efron1993bootstrap.
- Não entraram por falta de verificação: CoLiDeS (Kitajima, Blackmon e Polson, 2000) e Dillion et al. (2026).

## Regras seguidas

- O B1 é descrito como "inspirado" no protocolo do What Would GPT Click, nunca como reprodução.
- Os dados do Kuric 2025 e os do What Would GPT Click não são misturados.
- Nenhum número foi inventado: todo valor de resultado vem das exportações em `analysis/resultados-2026-11-13/`.
