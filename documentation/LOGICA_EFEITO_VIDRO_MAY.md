# Arquitetura e Logica do Efeito de Vidro (Glassmorphism & Liquid Glass) - MAY Task Manager

Este documento descreve detalhadamente a engenharia, os algoritmos matematicos, os shaders graficos e a integracao com o Jetpack Compose responsaveis pelo efeito de vidro translucido e refrativo (Liquid Glass / Frosted Glass) utilizado no MAY Task Manager.

---

## 1. Visao Geral da Arquitetura

O sistema de efeito de vidro do MAY foi projetado para simular o comportamento optico real de lentes e placas de vidro sobrepostas ao papel de parede do dispositivo, com iluminacao especular nas bordas, dispersao cromatica e refracao liquida.

A arquitetura e dividida em quatro camadas principais:

1. **WallpaperGlassProvider**: Gerenciador singleton responsavel pela extracao, cache, redimensionamento e sincronizacao da textura do papel de parede do sistema em segundo plano.
2. **GlassShader**: Motor matematico e de renderizacao que implementa o shader AGSL de refracao liquida (`RuntimeShader` no Android 13+), o algoritmo de desfoque por caixa triplo (`Box Blur`) em CPU para fallbacks, e o calculo de sombras e contornos.
3. **GlassStyle**: Modelo de dados que parametriza as propriedades fisicas e visuais do vidro (profundidade, refracao, dispersao, rim lighting, saturacao, bordas e escurecimento).
4. **GlassSurface e GlassCard**: Componentes do Jetpack Compose que mapeiam as coordenadas relativas de cada elemento na tela (coordenadas de janela) diretamente sobre a textura global do wallpaper.

---

## 2. Captura e Extracao da Textura de Fundo (Wallpaper Extraction)

Para que cada card simule transparencia real, o aplicativo precisa ter acesso a imagem exata do papel de parede configurado no sistema operacional. Em sistemas Android modernos, o acesso a essa textura sofre diversas restricoes de seguranca e diferencas de fabricantes.

O `WallpaperGlassProvider` implementa uma cadeia de fallback de 5 niveis para obter o papel de parede:

1. **WallpaperManager.getWallpaperFile (API 24+)**: Tenta obter o descritor de arquivo direto do wallpaper do sistema (`FLAG_SYSTEM`).
2. **WallpaperManager.drawable / peekDrawable**: Tenta capturar o drawable do papel de parede atual atraves da API publica do framework.
3. **Leitura direta do sistema de arquivos**: Caso a API publica falhe, verifica caminhos padrao do Android (`/data/system/users/0/wallpaper`, `/data/system/users/0/wallpaper_orig` e `/data/system/users/0/wallpaper_lock`).
4. **Extracao via Root (su)**: Como o MAY e um gerenciador de tarefas avançado com suporte a Root, se os metodos convencionais forem bloqueados por permissoes de sandbox, executa um comando de baixa sobrecarga via `su` para copiar a textura crua do sistema para o cache local do aplicativo.
5. **Wallpaper procedural de contingencia (`createFallbackWallpaper`)**: Se nenhum wallpaper estiver disponivel (ou em ambientes restritos), gera em tempo real um bitmap gradiente adaptativo com gradientes lineares e radiais projetados especificamente para combinar com o modo claro ou escuro.

### Reatividade a Mudancas de Wallpaper
O provedor registra um `OnColorsChangedListener` via `WallpaperManager.addOnColorsChangedListener` (API 27+). Sempre que o usuario altera o papel de parede do Android, o evento e capturado e um contador reativo (`_wallpaperTick`) e incrementado via `StateFlow`, forçando a re-renderizacao automatica e instantanea de todas as superficies de vidro ativas no aplicativo.

---

## 3. Pre-processamento e Algoritmo de Desfoque (Box Blur em CPU)

Para manter 60/120 FPS sem travar a interface durante a rolagem de listas (LazyColumn), o wallpaper capturado e processado assincronamente em uma thread de IO (`Dispatchers.IO`) e dividido em duas texturas cacheadas:

1. **Textura Nitida (`cachedSharp`)**: Redimensionada para uma largura de referencia de 720px, mantendo a proporcao de tela. Utilizada como entrada para o shader refrativo AGSL.
2. **Textura Desfocada (`cachedBlurred`)**: Reduzida para 180px e submetida a um algoritmo otimizado de desfoque por caixa triplo (`GlassShader.boxBlur`). Utilizada para dispositivos sem suporte a AGSL ou como textura difusa de base.

### O Algoritmo de Desfoque por Caixa Triplo
O algoritmo simula uma curva de desfoque gaussiano atraves de 3 passadas sucessivas de box-blur unidimensional:

```
sigma = sqrt(radius * (radius + 1))
```

Em cada passada, o algoritmo executa:
- Uma passagem horizontal sobre a matriz de pixels.
- Uma passagem vertical sobre a matriz de pixels.
- Uso de uma janela deslizante (sliding window / moving accumulator): em vez de recalcular a soma de todos os pixels vizinhos a cada passo (o que seria O(N * R)), o algoritmo soma o novo pixel que entra na janela e subtrai o pixel que saiu, operando em tempo linear O(N) constante independente do raio do desfoque.

---

## 4. O Mapeamento de Coordenadas de Janela (Window Coordinate Projection)

Este e o segredo fundamental do efeito de vidro no MAY: **coordenadas universais de projecao**.

Se um card apenas pegasse o wallpaper inteiro e o comprimisse dentro de si, a imagem do fundo ficaria distorcida e repetida em cada card. Para parecer vidro transparente real, o card precisa exibir **exatamente a regiao do papel de parede que estaria visivel caso o card nao existisse**.

### Como Funciona a Transformacao Matricial:
1. O composable `GlassSurface` registra sua posicao absoluta na tela utilizando o modificador `.onGloballyPositioned { coords -> coords.positionInWindow() }`.
2. A resolucao fisica real do display e capturada via `WindowManager.currentWindowMetrics` (ou `getRealMetrics`).
3. Uma matriz 2D (`android.graphics.Matrix`) e montada para o `BitmapShader`:
   - Aplica a escala para ajustar a largura do bitmap a largura total da tela (`setScale(scale, scale)`).
   - Centraliza verticalmente o wallpaper na tela (`postTranslate(0f, (screenHeight - scaledHeight) / 2f)`).
   - Aplica a translacao negativa exata da coordenada do card (`postTranslate(-positionInWindow.x, -positionInWindow.y)`).

Resultado: Independentemente de onde o card for colocado, de quanto o usuario rolar a tela em um `LazyColumn`, ou se o card estiver dentro de um dialogo modal, a textura do papel de parede de fundo fica perfeitamente ancorada nas coordenadas do mundo real.

---

## 5. Shader AGSL de Refracao Liquida (Android 13+ / Tiramisu)

Em dispositivos com Android 13 ou superior, o MAY Task Manager executa um shader customizado em tempo real na GPU utilizando AGSL (Android Graphics Shading Language) no arquivo `GlassShader.kt` (`const val LIQUID`).

O pipeline interno do shader executa os seguintes estagios matematicos por pixel (`main(float2 coord)`):

### A. Signed Distance Field (SDF) para Caixas Arredondadas
O shader calcula a distancia euclidiana de cada pixel ate a borda do retangulo arredondado do card:

```glsl
float roundedBox(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
```

Onde:
- `d < 0`: O pixel esta dentro do card.
- `d == 0`: O pixel esta exatamente na borda do card.
- `d > 0`: O pixel esta fora do card.

### B. Calculo de Normais de Superficie
A partir da funcao de distancia SDF, calcula-se a derivada direcional finita para encontrar o vetor normal 2D da superficie na borda curva:

```glsl
normal = float2(
    boxDistance(p + e.xy, halfSize) - boxDistance(p - e.xy, halfSize),
    boxDistance(p + e.yx, halfSize) - boxDistance(p - e.yx, halfSize)
);
normal = normal / max(length(normal), 0.0001);
```

### C. Refracao Optica e Deformacao de Borda
Conforme a distancia se aproxima da borda externa (`d -> 0`), a curvatura da lente de vidro deforma as coordenadas de textura:

```glsl
float band = clamp(1.0 + d / max(width, 1.0), 0.0, 1.0);
float bend = band * band * band * strength;
float2 offset = -normal * refraction * bend;
```

A variavel `offset` empurra a amostragem da textura contra a normal da borda, gerando o efeito de refracao convexa caracteristico de bordas de vidro lapidado e liquidos.

### D. Dispersao Cromatica (Aberracao Prismatica)
Para reproduzir o comportamento de dispersao de luz (onde comprimentos de onda diferentes se refratam em angulos distintos ao passar por um prisma), o shader amostra os canais de cor RGB em offsets ligeiramente separados:

```glsl
color.r = image.eval(coord + offset * (1.0 + dispersion)).r;
color.g = image.eval(coord + offset).g;
color.b = image.eval(coord + offset * (1.0 - dispersion)).b;
```

Essa separacao sutil gera um reflexo prismatico elegante e organico nas quinas arredondadas dos elementos.

### E. Iluminacao Especular Direcional (Specular Rim Lighting)
Duas fontes de luz virtuais sao calculadas sobre a normal:
- **Luz Direcional Superior (`light`)**: Posicionada a noroeste `(-0.5, -0.85)` simulando uma fonte de luz ambiente superior.
- **Sombra Inferior (`shade`)**: Posicionada a sudeste `(0.5, 0.85)` simulando a perda de luz na borda oposta.

```glsl
float rimBand = smoothstep(-2.5, 0.0, d);
float light = clamp(dot(normal, normalize(float2(-0.5, -0.85))), 0.0, 1.0);
float shade = clamp(dot(normal, normalize(float2(0.5, 0.85))), 0.0, 1.0);

color.rgb += half3(rim * rimBand * (0.18 + 0.5 * light));
color.rgb += half3(rim * bend * 0.12 * light);
color.rgb -= half3(rim * bend * 0.06 * shade);
```

### F. Controle de Saturacao e Escurecimento Contextual (Dimming)
A luminosidade perceptual (`luma`) e extraida usando os coeficientes da norma ITU-R BT.709 (`dot(color.rgb, half3(0.2126, 0.7152, 0.0722))`). A saturacao e interpolada linearmente, e a cor e mesclada com a cor de dimming do tema (modo claro ou escuro) para garantir legibilidade perfeita de textos em qualquer situacao.

---

## 6. Camadas de Contorno, Sombra e Tonalizacao

Alem da textura refratada pelo shader, o `WallpaperGlassProvider` adiciona tres camadas adicionais de desenho via Canvas nativo:

1. **Sombra Difusa (`drawShadow`)**:
   - Usa `setShadowLayer` com uma mascara invertida (`canvas.clipOutPath(path)`).
   - O uso de `clipOutPath` garante que a sombra seja projetada exclusivamente **para fora** do card, impedindo que o interior do vidro fique artificialmente escurecido pela propria sombra.
2. **Tonalizacao de Superficie (`tintOpacity`)**:
   - Uma camada com `surfaceColor` e transparencia controlada (geralmente entre 20% e 55%) e aplicada para dar peso e contraste com base na paleta dinamica do Material Theme (Monet).
3. **Contorno Translúcido Lapidado (`drawOutline`)**:
   - Desenha um traço com espessura de 1dp/0.75dp e opacidade de cerca de 18% a 22% em branco puro, posicionado com recuo de meio traço (`inset = width / 2f`), simulando o reflexo da quina polida do vidro.

---

## 7. Variacoes de Estilo Predefinidas (`GlassStyle`)

O aplicativo disponibiliza configuracoes ajustadas para diferentes casos de uso:

- **DEFAULT**: Vidro limpo, refracao 1.0, profundidade 1.0, dispersao 1.0, sombra 1.0. Utilizado para elementos de destaque e paineis.
- **DIALOG**: Refracao 0.85, profundidade 0.95, dispersao 0.4, rim 1.15, dimFactor 1.0. Otimizado para caixas de dialogo e sheets modais, aumentando a legibilidade do texto e o foco visual.
- **ICON**: Refracao 0.85, profundidade 0.85, dispersao 0.7, rim 0.85, sombra 0.0. Otimizado para icones de aplicativos ou chips pequenos, sem sobrecarga de sombras pesadas.

---

## 8. Integracao com Componentes Jetpack Compose

Nos composables do MAY Task Manager, a utilizacao e unificada atraves de `GlassCard` e `GlassSurface`:

```kotlin
GlassCard(
    modifier = Modifier.fillMaxWidth(),
    cornerRadiusDp = 14.dp,
    fallbackColor = dynamicSurfaceColor(alpha = 0.70f, tintFraction = 0.18f),
    border = BorderStroke(1.dp, dynamicBorderColor(alpha = 0.22f, tintFraction = 0.25f)),
) {
    // Conteudo do card
}
```

### Comportamento Hibrido e Resiliente:
Quando o usuario seleciona o tema de Vidro nas configuracoes (`LocalGlassThemeActive.current == true`), o `GlassCard` ativa a renderizacao completa em GPU com `GlassSurface`. Caso o usuario escolha temas convencionais (como Monet Padrao ou OLED Puro), o componente automaticamente desliga os shaders e reverte para superficies com tonalizacao solida/translúcida nativa do Material 3, garantindo zero desperdicio de bateria para quem nao utiliza o efeito visual.
