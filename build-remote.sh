#!/data/data/com.termux/files/usr/bin/bash

# ==============================================================================
# Big Calendar - Remote Build via GitHub Actions
# Compila o APK no GitHub Actions, exibe o progresso em tempo real no terminal,
# faz download automático do APK para outputs e Downloads em caso de sucesso,
# ou extrai o resumo do erro em caso de falha.
# ==============================================================================

set -eo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

OUTPUT_DIR="$PROJECT_DIR/app/build/outputs/apk/release"
FINAL_APK="$OUTPUT_DIR/app-release.apk"
DOWNLOAD_DIR="$HOME/storage/downloads"
WORKFLOW_FILE="build.yml"
ARTIFACT_NAME="BigCalendar-Release-APK"
REPO_PATH="$(git remote get-url origin 2>/dev/null | sed -E 's/.*github\.com[:\/](.+)\.git/\1/' || echo "S-Marcos-S/BigCalendar")"

echo "=========================================================="
echo "      Big Calendar - Build Remota (GitHub Actions)"
echo "=========================================================="
echo

# 1. Verificar se o GitHub CLI (gh) está instalado
if ! command -v gh >/dev/null 2>&1; then
    echo "❌ Erro: GitHub CLI (gh) não está instalado."
    echo "   Instale no Termux com: pkg install gh"
    exit 1
fi

# 2. Verificar autenticação no GitHub CLI
if ! gh auth status >/dev/null 2>&1; then
    echo "⚠️  Você ainda não está autenticado no GitHub CLI."
    echo "   Para autenticar (apenas uma vez), execute:"
    echo "      gh auth login"
    echo "   Ou defina a variável de ambiente: export GH_TOKEN=seu_token"
    echo
    read -rp "Deseja autenticar agora via 'gh auth login'? (s/N): " resp
    if [[ "$resp" =~ ^[sSyY]$ ]]; then
        gh auth login
    else
        exit 1
    fi
fi

CURRENT_BRANCH="$(git branch --show-current 2>/dev/null || echo "termux")"
CURRENT_SHA="$(git rev-parse HEAD 2>/dev/null || echo "")"

echo "🌿 Branch atual: $CURRENT_BRANCH"
echo "📌 Commit atual: ${CURRENT_SHA:0:7}"
echo

# 3. Verificar alterações não commitadas
if ! git diff-index --quiet HEAD -- 2>/dev/null; then
    echo "⚠️  Aviso: Existem alterações locais não commitadas."
    echo "   O GitHub Actions compila o código enviado ao repositório."
    echo "   (Para commitar suas alterações agora, cancele e use 'git commit -am \"sua mensagem\"')"
    echo
fi

# 4. Verificar e enviar commits não enviados
UNPUSHED="$(git log "origin/$CURRENT_BRANCH"..HEAD --oneline 2>/dev/null || true)"
if [ -n "$UNPUSHED" ]; then
    echo "📤 Commits locais pendentes detectados:"
    echo "$UNPUSHED" | sed 's/^/   • /'
    echo
    echo "Enviando commits para origin/$CURRENT_BRANCH..."
    git push origin "$CURRENT_BRANCH"
    CURRENT_SHA="$(git rev-parse HEAD)"
    echo "✅ Push realizado com sucesso!"
    echo
fi

# 5. Localizar ou disparar a execução no GitHub Actions
echo "🔍 Procurando workflow no GitHub Actions para o commit ${CURRENT_SHA:0:7}..."

RUN_ID=""
RUN_URL=""

# Tentar encontrar a run do commit por até 20 segundos
for i in {1..10}; do
    MATCHING_ID="$(gh run list --workflow="$WORKFLOW_FILE" --branch "$CURRENT_BRANCH" --limit 5 --json databaseId,headSha -q ".[] | select(.headSha == \"$CURRENT_SHA\") | .databaseId" 2>/dev/null | head -n 1 || true)"
    
    if [ -n "$MATCHING_ID" ]; then
        RUN_ID="$MATCHING_ID"
        RUN_URL="$(gh run view "$RUN_ID" --json url -q .url 2>/dev/null || echo "")"
        break
    fi
    
    sleep 2
done

# Se não encontrou nenhuma run para o commit atual, dispara manualmente via workflow_dispatch
if [ -z "$RUN_ID" ]; then
    echo "ℹ️  Nenhuma build automática em andamento encontrada para este commit."
    echo "   Disparando build manual via workflow_dispatch..."
    gh workflow run "$WORKFLOW_FILE" --ref "$CURRENT_BRANCH"
    echo "⏳ Aguardando GitHub Actions registrar o início da execução..."
    sleep 4
    for i in {1..10}; do
        RUN_ID="$(gh run list --workflow="$WORKFLOW_FILE" --branch "$CURRENT_BRANCH" --limit 1 --json databaseId,status -q '.[0].databaseId' 2>/dev/null || true)"
        RUN_STATUS="$(gh run list --workflow="$WORKFLOW_FILE" --branch "$CURRENT_BRANCH" --limit 1 --json status -q '.[0].status' 2>/dev/null || true)"
        if [ -n "$RUN_ID" ] && [ "$RUN_STATUS" != "completed" ]; then
            RUN_URL="$(gh run view "$RUN_ID" --json url -q .url 2>/dev/null || echo "")"
            break
        fi
        sleep 2
    done
fi

if [ -z "$RUN_ID" ]; then
    echo "❌ Não foi possível obter o ID da execução no GitHub Actions."
    echo "   Verifique se o workflow existe em .github/workflows/$WORKFLOW_FILE."
    exit 1
fi

echo
echo "🚀 Acompanhando execução em tempo real..."
echo "🆔 Run ID: $RUN_ID"
if [ -n "$RUN_URL" ]; then
    echo "🔗 URL: $RUN_URL"
fi
echo "----------------------------------------------------------"
echo

# 6. Acompanhar a execução em tempo real no terminal
set +e
gh run watch "$RUN_ID" --exit-status
WATCH_EXIT_CODE=$?
set -e

echo
echo "----------------------------------------------------------"

# 7. Tratar o resultado da build
if [ $WATCH_EXIT_CODE -eq 0 ]; then
    echo "🎉 SUCESSO! Build concluída com sucesso no GitHub Actions."
    echo
    echo "📥 Baixando artefato '$ARTIFACT_NAME'..."
    mkdir -p "$OUTPUT_DIR"
    
    TMP_DIR="$(mktemp -d)"
    DOWNLOAD_SUCCESS=false

    # 1. Tentar download com barra de progresso visual via curl
    TOKEN="$(gh auth token 2>/dev/null || echo "")"
    ARTIFACT_ID="$(gh api "repos/$REPO_PATH/actions/runs/$RUN_ID/artifacts" --jq ".artifacts[] | select(.name==\"$ARTIFACT_NAME\") | .id" 2>/dev/null || true)"

    if [ -n "$TOKEN" ] && [ -n "$ARTIFACT_ID" ]; then
        PRE_SIGNED_URL="$(curl -s -H "Accept: application/vnd.github+json" \
            -H "Authorization: Bearer $TOKEN" \
            -H "X-GitHub-Api-Version: 2022-11-28" \
            -w "%{redirect_url}" \
            "https://api.github.com/repos/$REPO_PATH/actions/artifacts/$ARTIFACT_ID/zip" 2>/dev/null || true)"

        if [ -n "$PRE_SIGNED_URL" ]; then
            echo "📊 Progresso do download do APK:"
            if curl -# -L -o "$TMP_DIR/artifact.zip" "$PRE_SIGNED_URL"; then
                if unzip -q -o "$TMP_DIR/artifact.zip" -d "$TMP_DIR" 2>/dev/null; then
                    DOWNLOAD_SUCCESS=true
                fi
            fi
            echo
        fi
    fi

    # 2. Fallback via gh run download se o curl não tiver sido usado
    if [ "$DOWNLOAD_SUCCESS" != "true" ]; then
        echo "ℹ️  Baixando via GitHub CLI..."
        if gh run download "$RUN_ID" -n "$ARTIFACT_NAME" -D "$TMP_DIR"; then
            DOWNLOAD_SUCCESS=true
        fi
    fi

    if [ "$DOWNLOAD_SUCCESS" = "true" ]; then
        DOWNLOADED_APK="$(find "$TMP_DIR" -type f -name "*.apk" | head -n 1)"
        if [ -n "$DOWNLOADED_APK" ] && [ -f "$DOWNLOADED_APK" ]; then
            cp -f "$DOWNLOADED_APK" "$FINAL_APK"
            rm -rf "$TMP_DIR"
            
            echo "=========================================================="
            echo "                     APK PRONTO!"
            echo "=========================================================="
            echo "📁 Local do APK gerado:"
            echo "   $FINAL_APK"
            echo
            ls -lh "$FINAL_APK"
            echo
            
            if [ -d "$DOWNLOAD_DIR" ]; then
                cp -f "$FINAL_APK" "$DOWNLOAD_DIR/Big_Calendar-release.apk"
                echo "📱 Copiado para Downloads:"
                echo "   $DOWNLOAD_DIR/Big_Calendar-release.apk"
                echo
            fi
        else
            echo "⚠️  Artefato baixado, mas nenhum arquivo .apk foi encontrado no pacote."
            rm -rf "$TMP_DIR"
        fi
    else
        echo "❌ Falha ao baixar o artefato com o gh."
        rm -rf "$TMP_DIR"
    fi
else
    echo "❌ FALHA NA BUILD DO GITHUB ACTIONS!"
    echo
    echo "=========================================================="
    echo "                 RESUMO DO LOG DE ERRO"
    echo "=========================================================="
    echo
    
    RAW_LOG="$(gh run view "$RUN_ID" --log-failed 2>&1 || true)"
    
    # Filtra os erros mais relevantes do Kotlin/Gradle/Android
    SUMMARY="$(echo "$RAW_LOG" | grep -iE 'e: file://|error:|failure:|what went wrong:|compilation error|unresolved reference|failed with an exception|Task :app:compile' -C 2 | tail -n 40 || true)"
    
    if [ -n "$SUMMARY" ]; then
        echo "$SUMMARY"
    else
        echo "$RAW_LOG" | tail -n 35
    fi
    
    echo
    echo "=========================================================="
    if [ -n "$RUN_URL" ]; then
        echo "🔗 Log completo disponível em:"
        echo "   $RUN_URL"
    fi
    echo
    exit 1
fi
