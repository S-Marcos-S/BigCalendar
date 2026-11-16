# Diretrizes e Regras do Projeto Big Calendar

## Regra Obrigatória: Design Minimalista, Compacto e Responsivo
- **Tudo no aplicativo DEVE ser feito de forma estritamente minimalista e compacta.**
- **Sem textos desnecessários** ou prolixos, e **sem botões com texto grande**.
- **Adaptação Responsiva:** Todos os cards, modais, painéis e elementos devem caber e se adaptar perfeitamente ao tamanho da tela do dispositivo do usuário.
- **Sem Quebra de Linha em Botões:** É terminantemente proibido botões com nomes quebrados ou truncados por não caberem na linha. Os títulos e ações devem ser concisos, objetivos e calculados para manter integridade visual em qualquer largura de tela.
- **Proibição Absoluta de Textos Hardcoded:** É terminantemente proibido colocar qualquer texto de interface hardcoded no código. Todas as strings visíveis ao usuário DEVEM OBRIGATORIAMENTE ser extraídas para `app/src/main/res/values/strings.xml` (e recursos correspondentes como `values-en/strings.xml`, etc.).

## Regra Obrigatória: Validação Prévia em Branch de Testes (Proteção da Branch Main)
Para evitar poluir a branch `main` com commits que possam quebrar a compilação ou falhar na geração de APKs:
- **Toda alteração de código ou que envolva nova build deve ser testada primeiro em branch de testes (`test` ou `termux`):** É estritamente proibido enviar commits de novas implementações ou correções diretamente para a `main` sem validação prévia.
- **Commits triviais ou de documentação pura:** Mudanças que afetam apenas documentação (`README.md`, `documentation/`, etc.) e que não alteram código funcional nem disparam nova build podem ser commitadas normalmente.
- **Fluxo de Trabalho Obrigatório para Builds:**
  1. Desenvolva as mudanças e utilize a branch de testes (`git checkout -b test` ou `git switch test` ou branch ativa de desenvolvimento).
  2. Faça o bump de versão em `app/build.gradle.kts` e atualize `CHANGELOG_LATEST.md`.
  3. Realize o commit contendo a versão na mensagem e faça o push para a branch de testes (`git push origin test`).
  4. O GitHub Actions executará automaticamente o workflow de build (compilação e empacotamento).
  5. Acompanhe a execução da build até a conclusão (`gh run watch` ou `./build-release.sh --remote`). Caso ocorra qualquer falha, investigue e corrija na própria branch de testes.
  6. **SOMENTE APÓS CONFIRMAÇÃO DO USUÁRIO:** promova as alterações para a branch `main`, incremente para a próxima versão pública oficial `X.(Y+1).0` (ou `(X+1).0.0` se Y=9), incremente o `versionCode`, consolide o `CHANGELOG_LATEST.md` e envie o push para gerar a Release e APK oficial estável:
     ```bash
     git checkout main
     git merge test
     # Definir próximo versionName X.Y.0 e incrementar versionCode em app/build.gradle.kts
     # Atualizar CHANGELOG_LATEST.md
     git commit -am "release: vX.Y.0"
     git push origin main
     # Sincronizar branch test com main
     git checkout test
     git merge main --ff-only
     git push origin test
     ```

## Regra Obrigatória: Estratégia Padronizada de Versionamento (Releases Públicas vs. Builds de Teste)
Para garantir clareza entre releases estáveis para o público e builds de teste beta, adota-se rigorosamente a convenção:
- **Releases Públicas na Branch `main` (Sempre terminadas em `.0`):**
  - Formato estrito: `X.Y.0` (exemplo: `1.1.0`, `1.2.0`, `1.3.0`, etc.).
  - A cada nova release pública na `main`, incrementa-se o dígito menor (minor): `1.1.0` -> `1.2.0` -> `1.3.0` ... -> `1.9.0`.
  - **Virada para Versão Maior (major):** Ao atingir a versão `1.9.0`, a próxima release pública salta obrigatoriamente para `2.0.0` (reiniciando minor e patch para `0`). E assim sucessivamente (`2.9.0` -> `3.0.0`).
  - Releases na `main` geram tags como `vX.Y.0` e a tag flutuante `latest`, sendo publicadas como releases oficiais (`prerelease: false`).
- **Builds de Teste na Branch `test` (Sempre incrementando o patch `Z >= 1`):**
  - Formato estrito: `X.Y.Z` onde Z >= 1 (exemplo: partindo da pública `1.2.0`, as builds de teste em `test` serão `1.2.1`, `1.2.2`, `1.2.3`, etc.).
  - As tags no GitHub Actions na branch `test` recebem o sufixo `-beta` (exemplo: `v1.2.1-beta`) e são marcadas como `prerelease: true`.
  - NUNCA use terminação `.0` para builds na branch `test`.
- **Transição e Promoção de `test` para `main` (Release Oficial):**
  1. Quando as builds de teste forem validadas e o usuário aprovar a publicação oficial:
  2. Calcula-se a próxima versão pública na `main`: se a última foi `X.Y.0`, a nova será `X.(Y+1).0` (ou `(X+1).0.0` se Y = 9).
  3. Incrementa-se o `versionCode` (`versionCode = max(main, test) + 1`).
  4. Consolida-se o `CHANGELOG_LATEST.md`.
  5. Commita-se com a mensagem contendo a versão (ex: `release: vX.Y.0`) e faz-se o push para a branch `main`.
  6. Acompanha-se a execução do workflow `build.yml` na `main` até a publicação com sucesso.
  7. Sincroniza-se a branch `test` com a `main` (`git checkout test && git merge main --ff-only && git push origin test`) para que o próximo ciclo de testes parta de `X.Y.0` e sua primeira build de teste seja `X.Y.1`.

## Regra Obrigatória: Bump de Versão/Release Apenas para Mudanças Relevantes (Proibido para Mudanças Bestas/Triviais)
- **Mudanças Bestas / Triviais (NÃO gerar build):**
  - Para alterações pequenas, triviais ou que não alteram o funcionamento do aplicativo em si:
    - **NUNCA** faça bump de versão (`versionCode` e `versionName` em `app/build.gradle.kts`).
    - **NUNCA** altere o `CHANGELOG_LATEST.md`.
    - **NUNCA** inclua número de versão na mensagem do commit.
    - Faça apenas um **commit normal**.
- **Mudanças Relevantes no Aplicativo (GERAR build):**
  - Sempre que for realizado um commit que traga uma **nova funcionalidade/feature**, **correção de bug real**, **refatoração funcional** ou **mudança visual direta no aplicativo**:
    - **DEVE-SE** incrementar o número de release/versão (`versionCode` e `versionName` no arquivo `app/build.gradle.kts`).
    - **DEVE-SE** atualizar o arquivo `CHANGELOG_LATEST.md` (resumido e compacto).
    - **OBRIGATORIAMENTE** incluir o número da versão na mensagem/comentário do commit (exemplo: `1.8.10` ou `v1.8.10`).
    - Seguir rigorosamente o fluxo de validação prévia na branch de testes antes da `main`.

## Regra Obrigatória: Changelogs Resumidas e Compactas Pré-Build
Sempre que forem realizadas novas implementações, correções ou modificações no código, o arquivo `CHANGELOG_LATEST.md` na raiz do projeto **DEVE** ser atualizado antes de efetuar o commit e disparar a build do GitHub Actions.

- **Formato Estrito:** As changelogs **DEVEM SER RESUMIDAS E COMPACTAS**. É estritamente proibido incluir textos longos, parágrafos explicativos prolixos ou detalhes técnicos excessivos. Foque apenas no que mudou de forma direta, clara e concisa (tópicos curtos).

## Regra Obrigatória: Manutenção do ROADMAP.md (NÃO Incluir nos Commits)
- Manter o arquivo `ROADMAP.md` na raiz sempre sincronizado e atualizado conforme novas tarefas e correções forem planejadas e concluídas.
- **Proibição Estrita de Inclusão em Commits:** O arquivo `ROADMAP.md` **NUNCA DEVE ser incluído no commit** nem enviado para o repositório remoto (permanecendo estritamente para acompanhamento local). Nunca execute `git add ROADMAP.md`.

## Regra Obrigatória: Proibição Estrita de Builds Locais e Gradle
NUNCA faça build local nem execute comandos `./gradlew` (tais como `./gradlew assembleDebug`, `./gradlew assembleRelease`, `./gradlew build`, etc.) no ambiente local/Termux. A compilação e geração dos APKs é SEMPRE e EXCLUSIVAMENTE realizada pelo GitHub Actions (podendo ser disparada e acompanhada pelo script `./build-release.sh --remote` ou `build-remote.sh`).

## Regra Obrigatória: Verificação Rigorosa e Estrita de Importações (Zero Tolerância para Imports Ausentes)
Sempre que qualquer arquivo de código for criado ou editado e utilizar qualquer classe, objeto, função, extensão ou recurso externo, **DEVE-SE OBRIGATORIAMENTE conferir a lista de imports do topo do arquivo** antes de salvar e commitar.
- **Tolerância Zero:** Erros por falta de importação são inaceitáveis. Toda referência externa introduzida precisa ter seu respectivo `import` explicitamente verificado e confirmado no topo do arquivo.

## Regra Obrigatória: Proibição Estrita de Emojis
É expressamente proibido o uso de emojis em qualquer documentação, README.md, CHANGELOGs, mensagens de commit, comentários ou textos da interface do Big Calendar. Todas as seções, tópicos e títulos devem ser formatados exclusivamente com texto limpo, claro e profissional, sem ícones unicode ou símbolos decorativos desnecessários.

## Regra Obrigatória: Acompanhamento de Builds do GitHub Actions
Sempre que for realizado um commit/push que inicie uma build no GitHub Actions, a execução do workflow **DEVE OBRIGATORIAMENTE ser acompanhada até o final** (via `./build-release.sh --remote`, `gh run watch`, ou monitoramento da API de workflow runs) para verificar se a compilação, os testes e a geração da Release/APK foram concluídos com sucesso (`completed` / `success`).

## Regra Obrigatória: Idioma Padrao em Workflows, Commits e Comentarios de Codigo (Somente em Ingles)
- **Workflows do GitHub Actions:** Nomes de workflows, jobs, steps, variaveis e descricoes nos arquivos `.github/workflows/*.yml` devem ser escritos estritamente em ingles.
- **Mensagens de Commit:** Todas as mensagens de commit (incluindo escopos convencionais como `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`, `ci:`) devem ser escritas obrigatoriamente e exclusivamente em ingles.
- **Comentarios no Codigo:** Todos os comentarios em arquivos de codigo-fonte (Kotlin, Java, Gradle scripts, etc.) devem ser escritos unicamente em ingles tecnico.
