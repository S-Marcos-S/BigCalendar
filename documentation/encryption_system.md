# Funcionamento do Sistema de Criptografia

Este documento descreve detalhadamente o funcionamento da criptografia do aplicativo TheBigCalendar. O sistema protege as informações locais e os dados enviados ao Google Drive utilizando algoritmos criptográficos robustos de padrão militar.

---

## 1. Algoritmos e Parâmetros Utilizados
O aplicativo implementa criptografia simétrica utilizando os seguintes algoritmos:
- **AES-GCM (Advanced Encryption Standard - Galois/Counter Mode)** com tamanho de tag de autenticação de 128 bits. GCM garante tanto a confidencialidade quanto a integridade dos dados (criptografia autenticada).
- **PBKDF2 (Password-Based Key Derivation Function 2)** usando o algoritmo pseudo-aleatório `PBKDF2WithHmacSHA256` para derivação de chaves.
- **Parâmetros**:
  - `ITERATIONS` (Número de iterações do PBKDF2): 10.000 iterações (linhas 13).
  - `KEY_LENGTH` (Tamanho da chave AES derivada): 256 bits (linhas 14).
  - `SALT_LENGTH` (Tamanho do Sal gerado aleatoriamente): 16 bytes (linhas 15).
  - `IV_LENGTH` (Vetor de Inicialização / Nonce para o GCM): 12 bytes (linhas 16).

---

## 2. Lógica Criptográfica (`CryptoHelper`)
As funções reais de criptografia são implementadas na camada multi-plataforma compartilhada por Android e Desktop.

### Localização do Código
- **Definição comum**: [CryptoHelper.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/shared/src/commonMain/kotlin/com/mss/thebigcalendar/crypto/CryptoHelper.kt)
- **Implementação Android**: [CryptoHelper.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/shared/src/androidMain/kotlin/com/mss/thebigcalendar/crypto/CryptoHelper.kt)
- **Implementação Desktop**: [CryptoHelper.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/shared/src/desktopMain/kotlin/com/mss/thebigcalendar/crypto/CryptoHelper.kt)

### Fluxo de Criptografia (`encrypt`)
- **Linhas**: 18 a 47
- **Processo**:
  1. Cria um vetor de bytes de sal de 16 bytes aleatórios (`SecureRandom`).
  2. Cria um vetor de bytes IV de 12 bytes aleatórios (`SecureRandom`).
  3. Gera a chave secreta AES de 256 bits através do `SecretKeyFactory` e `PBEKeySpec` informando a senha do usuário, sal e iterações.
  4. Inicializa o `Cipher` no modo `ENCRYPT_MODE` utilizando `AES/GCM/NoPadding` e `GCMParameterSpec`.
  5. Encripta a string em texto claro para bytes através do `doFinal`.
  6. Transforma o sal, IV e texto cifrado para codificação Base64 e monta a resposta em formato JSON:
     ```json
     {"encrypted":true,"salt":"<saltBase64>","iv":"<ivBase64>","ciphertext":"<ciphertextBase64>"}
     ```

### Fluxo de Descriptografia (`decrypt`)
- **Linhas**: 49 a 74
- **Processo**:
  1. Extrai os valores do sal, IV e texto cifrado do JSON utilizando expressão regular simples (`extractJsonValue` - linhas 76 a 80).
  2. Converte as strings Base64 de volta para vetores de bytes.
  3. Deriva a chave secreta AES de 256 bits usando PBKDF2 com a senha fornecida pelo usuário e o sal extraído da mensagem.
  4. Inicializa o `Cipher` no modo `DECRYPT_MODE` utilizando `AES/GCM/NoPadding` e o IV extraído da mensagem.
  5. Executa a descriptografia e verificação de integridade via `doFinal`. Se a senha estiver incorreta ou a mensagem tiver sido alterada, o algoritmo lança uma exceção.

---

## 3. Estado Local e Sincronização da Criptografia
Tanto a versão Android quanto Desktop controlam a senha localmente.

### Localização no Android
- **BackupService.kt** (`syncActivitiesWithCloud` - linhas 1011 a 1056):
  - Verifica se o arquivo baixado do Google Drive está criptografado usando `CryptoHelper.isEncrypted(content)`.
  - Se estiver criptografado, tenta descriptografá-lo usando a senha armazenada localmente (`settingsRepository.encryptionPassword`).
  - Se a descriptografia falhar por senha incorreta ou inválida, lança uma `DecryptionFailedException` que faz com que a interface abra o diálogo para solicitar a nova senha ao usuário.
  - Se o arquivo na nuvem **não** estiver criptografado, desativa a flag local de criptografia chamando `settingsRepository.saveEncryptionSettings(false, "")` se `providedPassword == null`.

### Localização no Desktop
- **DesktopCalendarViewModel.kt** (`syncActivitiesWithCloud` - linhas 1709 a 1740):
  - Executa a mesma lógica do Android, mas as configurações do Desktop atualizam a interface através do `_uiState` e salvam o arquivo de configurações do aplicativo local via `saveData()`.
