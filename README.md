# Documentação - Sistema de Chat gRPC


## Sumário

- [Visão Geral](#visão-geral)
- [Como Executar](#como-executar)
- [Fluxo de Funcionamento](#fluxo-de-funcionamento)
- [Arquitetura do Sistema](#arquitetura-do-sistema)
- [Descrição dos Arquivos](#descrição-dos-arquivos)
  - [contrato-chat.proto](#1-contrato-chatproto)
  - [ClientChat.java](#2-clientchatjava)
  - [RoomChat.java](#3-roomchatjava)
  - [ServerChat.java](#4-serverchatjava)
  - [UIChat.java](#5-uichatjava)


## Visão Geral

Este projeto implementa um sistema de chat em tempo real utilizando gRPC em Java. O sistema permite que múltiplos usuários se comuniquem através de uma sala de chat centralizada, com suporte a mensagens bidirecionais via streaming.

## Como Executar

### Pré-requisitos

1. **Java Development Kit (JDK)**: Versão 11 ou superior
2. **Protobuf Compiler (protoc)**: Para compilar o arquivo .proto
3. **Dependências gRPC**: Adicionar ao classpath ou usar gerenciador de dependências (Maven)

### Passos para Compilação

#### 1. Compilar o arquivo .proto

```bash
protoc --java_out=./src/main/java --grpc-java_out=./src/main/java \
  --plugin=protoc-gen-grpc-java=/path/to/protoc-gen-grpc-java \
  contrato-chat.proto
```

Isso gerará as classes Java no pacote `elc1018.grpc.chat.protos`.

#### 2. Compilar os arquivos Java

Usando javac (ou IDE):

```bash
javac -cp ".:grpc-libs/*" elc1018/grpc/chat/**/*.java
```

Ou usando Maven:

```bash
mvn clean compile
```

### Passos para Execução

#### 1. Iniciar o Servidor

```bash
java -cp ".:grpc-libs/*" elc1018.grpc.chat.server.ServerChat
```

Ou usando Maven:

```bash
mvn exec:java -Dexec.mainClass="elc1018.grpc.chat.server.ServerChat"
```

O servidor iniciará na porta 8080 e aguardará conexões.

**Saída esperada**:
```
(Servidor aguardando conexões silenciosamente)
```

#### 2. Iniciar Clientes

Em terminais separados, execute:

```bash
java -cp ".:grpc-libs/*" elc1018.grpc.chat.ui.UIChat
```

Ou usando Maven:

```bash
mvn exec:java -Dexec.mainClass="elc1018.grpc.chat.ui.UIChat"
```

**Passos na interface gráfica**:

1. Ao abrir, será solicitado um nome de usuário
2. Digite um nome único (não pode ser "SISTEMA" ou um nome já em uso)
3. Após registro bem-sucedido, a janela do chat será exibida
4. Digite mensagens no campo inferior e pressione Enter ou clique em "Enviar"
5. Mensagens de todos os usuários aparecerão na área superior

#### 3. Testar com Múltiplos Clientes

Execute o comando do cliente múltiplas vezes em diferentes terminais/máquinas para simular vários usuários.

### Configuração de Rede

Para conectar clientes remotos, altere a linha em UIChat.java:

```java
ClientChat clienteChat = new ClientChat("localhost", 8080);
```

Para:

```java
ClientChat clienteChat = new ClientChat("IP_DO_SERVIDOR", 8080);
```

Certifique-se de que a porta 8080 está aberta no firewall do servidor.

## Fluxo de Funcionamento

### 1. Registro de Usuário

1. Cliente abre interface gráfica
2. Usuário digita nome
3. Cliente envia requisição `Register` ao servidor
4. Servidor valida nome (não pode ser "SISTEMA" ou duplicado)
5. Servidor retorna sucesso ou falha
6. Se sucesso, cliente inicia stream de mensagens

### 2. Envio de Mensagem

1. Usuário digita mensagem e pressiona Enter
2. Cliente cria `ChatMessage` com timestamp
3. Cliente envia via `SendMessage` (RPC unário)
4. Servidor distribui para todos os usuários via `RoomChat`
5. Servidor retorna `Ack` de confirmação

### 3. Recebimento de Mensagens

1. Cliente estabelece stream via `ReceiveMessages`
2. Servidor registra observer do cliente em `RoomChat`
3. Quando qualquer usuário envia mensagem, servidor chama `onNext()` de todos os observers
4. Interface gráfica recebe mensagem e renderiza no HTML

### 4. Desconexão

1. Usuário fecha janela
2. Stream é cancelado automaticamente
3. Servidor detecta cancelamento via `setOnCancelHandler`
4. Servidor remove usuário e notifica outros sobre saída
5. Cliente encerra canal gRPC

## Arquitetura do Sistema

O sistema é composto por três componentes principais:

- **Servidor**: Gerencia a sala de chat e coordena a comunicação entre clientes
- **Cliente**: Estabelece conexão com o servidor e gerencia o envio/recebimento de mensagens
- **Interface Gráfica**: Fornece uma interface visual para interação do usuário

## Descrição dos Arquivos

### 1. contrato-chat.proto

**Propósito**: Define o contrato de comunicação entre cliente e servidor usando Protocol Buffers.

**Estrutura**:

- **Serviço ChatService**: Define três operações RPC:
  - `Register`: Registra um usuário na sala (unário)
  - `SendMessage`: Envia uma mensagem para todos os usuários (unário)
  - `ReceiveMessages`: Recebe mensagens em tempo real (streaming do servidor)

- **Mensagens**:
  - `User`: Contém o nome de usuário
  - `ChatMessage`: Representa uma mensagem com remetente, conteúdo e timestamp
  - `RegisterResponse`: Resposta do registro indicando sucesso e nome do usuário
  - `Ack`: Confirmação de operação bem-sucedida

**Configurações**:
- `java_multiple_files = true`: Gera classes Java separadas para cada mensagem
- `java_package = "elc1018.grpc.chat.protos"`: Define o pacote Java das classes geradas

### 2. ClientChat.java

**Propósito**: Implementa o cliente gRPC que se conecta ao servidor de chat.

**Localização**: `elc1018.grpc.chat.client.ClientChat`

**Componentes**:

- **canalGrpc**: Canal de comunicação gRPC gerenciado
- **stubSincrono**: Stub bloqueante para operações síncronas (Register, SendMessage)
- **stubAssincrono**: Stub não-bloqueante para operações assíncronas (ReceiveMessages)

**Métodos principais**:

- `ClientChat(String host, int porta)`: Construtor que estabelece conexão com o servidor
  - Cria canal gRPC sem criptografia (plaintext)
  - Inicializa stubs síncrono e assíncrono

- `registrarUsuario(String nomeUsuario)`: Registra o usuário no servidor
  - Retorna `RegisterResponse` indicando sucesso ou falha

- `enviarMensagem(ChatMessage mensagem)`: Envia mensagem para o servidor
  - Retorna `Ack` confirmando o envio

- `receberMensagens(User usuario, StreamObserver<ChatMessage> observadorDeMensagens)`: Inicia recebimento de mensagens via streaming
  - Utiliza padrão Observer para processar mensagens assíncronas

- `encerrarConexao()`: Desliga o canal gRPC gracefully

### 3. RoomChat.java

**Propósito**: Gerencia a sala de chat no lado do servidor, mantendo usuários conectados e distribuindo mensagens.

**Localização**: `elc1018.grpc.chat.server.RoomChat`

**Estruturas de dados**:

- **usuarios**: `Set<String>` thread-safe (ConcurrentHashMap.newKeySet()) armazenando nomes de usuários registrados
- **observadoresMensagens**: `Map<String, StreamObserver<ChatMessage>>` que mantém os streams ativos de cada usuário

**Métodos principais**:

- `registrarUsuario(String nomeUsuario)`: Adiciona usuário ao conjunto
  - Retorna `true` se o nome era único, `false` se já existia

- `registrarObservadorMensagens(String nomeUsuario, StreamObserver<ChatMessage> observadorMensagens)`: Registra o stream do usuário
  - Notifica todos os usuários sobre a entrada do novo usuário

- `removerUsuario(String nomeUsuario)`: Remove usuário da sala
  - Remove do conjunto de usuários e do mapa de observers
  - Notifica outros usuários sobre a saída
  - Completa o stream do usuário removido

- `encaminharMensagemParaTodos(ChatMessage mensagem)`: Distribui mensagem para todos os usuários conectados
  - Itera sobre todos os observers ativos
  - Remove usuários se a conexão falhar

- `notificarEntradaSaida(String conteudo)`: Envia mensagem do sistema
  - Cria mensagem com remetente "SISTEMA"
  - Inclui timestamp atual

### 4. ServerChat.java

**Propósito**: Ponto de entrada do servidor gRPC e implementação dos serviços definidos no proto.

**Localização**: `elc1018.grpc.chat.server.ServerChat`

**Classe principal: ServerChat**

- `main()`: Inicia o servidor gRPC na porta 8080
  - Registra o serviço ChatServiceImpl
  - Aguarda término com `awaitTermination()`

**Classe interna: ChatServiceImpl**

Estende `ChatServiceGrpc.ChatServiceImplBase` e implementa os métodos RPC:

- `register(User requisicao, StreamObserver<RegisterResponse> observadorResposta)`:
  - Valida se o nome não é "sistema" (reservado)
  - Registra usuário na sala
  - Retorna resposta de sucesso/falha

- `sendMessage(ChatMessage requisicao, StreamObserver<Ack> observadorResposta)`:
  - Encaminha mensagem para todos os usuários via RoomChat
  - Retorna confirmação de sucesso

- `receiveMessages(User requisicao, StreamObserver<ChatMessage> observadorResposta)`:
  - Registra o observer do usuário na sala
  - Configura handler para detectar desconexão do cliente
  - Mantém stream aberto para envio contínuo de mensagens

**Observação importante**: Usa `ServerCallStreamObserver` para detectar quando o cliente cancela a conexão, removendo-o automaticamente da sala.

### 5. UIChat.java

**Propósito**: Interface gráfica Swing para o cliente de chat.

**Localização**: `elc1018.grpc.chat.ui.UIChat`

**Componentes da interface**:

- **areaMensagens**: `JTextPane` com renderização HTML para exibir mensagens formatadas
- **campoMensagem**: `JTextField` para entrada de texto
- **botaoEnviar**: `JButton` para enviar mensagens

**Métodos principais**:

- `configurarJanela()`: Configura a janela principal
  - Tamanho 800x600
  - Layout BorderLayout
  - Área de mensagens centralizada com scroll
  - Painel inferior com campo de texto e botão

- `iniciarRegistro()`: Gerencia o processo de registro do usuário
  - Exibe dialog para entrada do nome
  - Valida nome com o servidor
  - Impede uso do nome "SISTEMA"
  - Trata nomes duplicados

- `enviarMensagem()`: Envia mensagem digitada pelo usuário
  - Cria `ChatMessage` com timestamp atual
  - Envia via cliente gRPC
  - Limpa campo de texto após sucesso

- `iniciarRecepcaoDeMensagens()`: Configura recebimento assíncrono de mensagens
  - Cria `StreamObserver` para processar mensagens recebidas
  - `onNext()`: Formata e exibe mensagem na interface
  - `onError()`: Exibe mensagem de erro de rede
  - `onCompleted()`: Notifica encerramento da conexão

- `adicionarMensagemAoChat(String html)`: Adiciona HTML ao documento da área de mensagens
  - Usa HTMLEditorKit para renderização
  - Mantém scroll na posição mais recente

- `obterCorUsuario(String nome)`: Define cores para cada usuário
  - Azul para o próprio usuário
  - Cinza para mensagens do sistema
  - Cores aleatórias para outros usuários

**Formatação de mensagens**:
- Timestamp em formato HH:mm:ss
- Nome do remetente colorido
- Nome do usuário atual em negrito
- Mensagens do sistema em cinza

