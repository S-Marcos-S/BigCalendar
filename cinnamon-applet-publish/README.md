# Big Calendar Cinnamon Applet

Este é o applet oficial para o Cinnamon que integra o seu painel de desktop com o aplicativo **Big Calendar**.

## Funcionalidades

- **Relógio Dinâmico**: Exibe a hora atual diretamente no seu painel principal.
- **Calendário Mensal Completo**: Clique no applet para abrir uma visão mensal elegante com navegação de meses.
- **Indicadores de Compromissos**: Pontos coloridos abaixo de cada dia no mini-calendário indicam se há compromissos e as suas respectivas prioridades/categorias.
- **Painel de Eventos do Dia**: Listagem completa no lado esquerdo com as tarefas agendadas para o dia selecionado, exibindo títulos, horários e barras com cores de prioridade correspondentes.
- **Integração em Tempo Real**: Os dados são lidos dinamicamente de `~/.thebigcalendar/activities.json` exportados pelo aplicativo Big Calendar.
- **Acesso Rápido**: Botão integrado no menu para abrir o aplicativo desktop completo Big Calendar.

## Estrutura de Instalação Local

Para testar ou instalar localmente sem publicar:

1. Copie a pasta `files/bigcalendar-applet@marcos` para a sua pasta de applets locais do Cinnamon:
   ```bash
   cp -r files/bigcalendar-applet@marcos ~/.local/share/cinnamon/applets/
   ```
2. Habilite o applet nas configurações de "Applets" do Cinnamon ou reinicie o Cinnamon.

## Publicação no Cinnamon Spices

Este repositório está estruturado conforme as diretrizes oficiais do Cinnamon Spices:
- `info.json`: Metadados da página de publicação e autoria.
- `screenshot.png`: Mockup de demonstração do applet em ação.
- `files/bigcalendar-applet@marcos/`: Diretório contendo os arquivos de execução (`applet.js`, `metadata.json`, `icon.png`).
