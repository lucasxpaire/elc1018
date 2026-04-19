# Fluxo de Execução do Chat gRPC e Relação com os Requisitos do Trabalho

## 1. Objetivo deste documento
Este documento explica o fluxo de execução das principais classes do projeto de chat distribuído com gRPC e Protocol Buffers, relacionando cada parte da implementação com os requisitos funcionais descritos no enunciado.

A ideia é mostrar:
- como o servidor é iniciado e mantém o estado da sala;
- como o cliente se registra, envia mensagens e recebe o fluxo contínuo de mensagens;
- como cada classe contribui para os requisitos de registro, sala única, envio, recepção e notificações.

---

## 2. Visão geral da arquitetura
O sistema é dividido em dois processos independentes:

- **`ServerChat`**: inicializa o servidor gRPC e publica o serviço `ChatService`.
- **`ClientChat` + `UIChat`**: representam o lado do cliente, combinando a comunicação gRPC com a interface gráfica em Swing.
- **`RoomChat`**: concentra o estado da sala única, armazenando usuários ativos e os observadores que recebem mensagens em tempo real.

### Contrato gRPC
O arquivo `src/main/proto/contrato-chat.proto` define os RPCs usados pela aplicação:

- `Register(User) returns (RegisterResponse)`
- `SendMessage(ChatMessage) returns (Ack)`
- `ReceiveMessages(User) returns (stream ChatMessage)`

Esse contrato não é alterado pelo código Java. A implementação apenas segue o que o `.proto` determina.

---

## 3. Fluxo do lado do servidor

### 3.1 `ServerChat`
A classe `ServerChat` é o ponto de entrada do servidor.

#### Fluxo principal
1. O método `main` cria um `Server` na porta `8080`.
2. O servidor adiciona a implementação do serviço gRPC (`ChatServiceImpl`).
3. O servidor é iniciado com `start()`.
4. O processo fica aguardando requisições com `awaitTermination()`.

#### Responsabilidade
`ServerChat` não contém a regra de negócio da sala. Ele apenas sobe a infraestrutura gRPC e delega a lógica para a classe interna `ChatServiceImpl`.

#### Relação com o trabalho
- **RFA02 – Sala única:** o servidor é o dono da instância central da sala.
- **RFA03 – Envio de mensagens:** o servidor recebe mensagens e as encaminha para todos os usuários.
- **RFA05 – Recepção via stream:** o servidor mantém conexões ativas de streaming com os clientes.

---

### 3.2 Classe interna `ChatServiceImpl`
Essa classe implementa os três RPCs definidos no contrato.

#### a) `register(User requisicao, StreamObserver<RegisterResponse> observadorResposta)`
Fluxo:
1. O cliente envia um `User` com o nome desejado.
2. O servidor extrai o nome com `getUsername()`.
3. O nome é encaminhado para `RoomChat.registrarUsuario(nomeUsuario)`.
4. Se o nome ainda não existir, o registro é aceito.
5. O servidor devolve `RegisterResponse` com `success` e `username`.

Relação com o trabalho:
- **RFA01 – Registro de utilizador único:** nomes duplicados são rejeitados.

---

#### b) `sendMessage(ChatMessage requisicao, StreamObserver<Ack> observadorResposta)`
Fluxo:
1. O cliente envia uma `ChatMessage` já preenchida com remetente, conteúdo e timestamp.
2. O servidor chama `salaChat.encaminharMensagemParaTodos(requisicao)`.
3. A mensagem é distribuída para todos os observadores ativos.
4. O servidor retorna um `Ack` confirmando o recebimento da requisição.

Relação com o trabalho:
- **RFA03 – Envio de mensagens:** a mensagem deve ser reencaminhada para todos os membros da sala.
- **RFA04 – Estrutura da mensagem:** o tipo `ChatMessage` preserva remetente, conteúdo e horário.

---

#### c) `receiveMessages(User requisicao, StreamObserver<ChatMessage> observadorResposta)`
Fluxo:
1. O cliente informa o nome de usuário que deseja associar ao stream.
2. O servidor registra o observador daquele cliente em `RoomChat`.
3. A conexão fica aberta em modo server streaming.
4. Se o cliente desconectar, o handler de cancelamento remove o usuário da sala.
5. A saída do usuário gera uma notificação para todos os demais.

Relação com o trabalho:
- **RFA05 – Recepção via stream:** o cliente mantém conexão ativa para receber mensagens.
- **RFA07 – Notificações de entrada/saída:** o servidor avisa todos quando alguém entra ou sai.

---

## 4. Fluxo de gerenciamento da sala

### 4.1 `RoomChat`
A classe `RoomChat` é o núcleo de estado da aplicação. Ela mantém duas estruturas principais:

- `usuarios`: conjunto dos nomes registrados;
- `observadoresMensagens`: mapa entre nome do usuário e o `StreamObserver` que recebe as mensagens.

#### Funções principais

##### `registrarUsuario(String nomeUsuario)`
- Tenta inserir o nome no conjunto `usuarios`.
- Se o nome já existir, o método retorna `false`.
- Isso impede duplicidade de registro.

##### `registrarObservadorMensagens(String nomeUsuario, StreamObserver<ChatMessage> observadorMensagens)`
- Associa o nome do usuário ao observador do stream.
- Em seguida, chama `notificarEntradaSaida(...)` para avisar a sala que um novo membro entrou.

##### `encaminharMensagemParaTodos(ChatMessage mensagem)`
- Percorre todos os observadores registrados.
- Para cada usuário, envia a mensagem com `onNext()`.
- Se algum canal falhar, o usuário é removido da sala.

##### `removerUsuario(String nomeUsuario)`
- Remove o usuário do conjunto e o observador do mapa.
- Se a remoção for válida, gera uma notificação de saída.
- Finaliza o fluxo do cliente com `onCompleted()`.

##### `notificarEntradaSaida(String conteudo)`
- Cria uma `ChatMessage` do sistema com remetente `SISTEMA`.
- Preenche o timestamp com o instante atual.
- Reencaminha a mensagem para todos.

#### Relação com o trabalho
- **RFA02 – Sala única:** a instância de `RoomChat` representa a sala central.
- **RFA06 – Ordenação:** o uso do streaming preserva a ordem das mensagens conforme o servidor as distribui.
- **RFA07 – Eventos de notificação:** entradas e saídas são transformadas em mensagens do sistema.

---

## 5. Fluxo do lado do cliente

### 5.1 `ClientChat`
Essa classe encapsula toda a comunicação gRPC do cliente.

#### Fluxo principal
1. O construtor cria um `ManagedChannel` para o endereço do servidor.
2. São criados dois stubs:
   - `stubSincrono` para chamadas unary;
   - `stubAssincrono` para o streaming de mensagens.
3. O cliente usa o stub síncrono para registro e envio.
4. O cliente usa o stub assíncrono para manter a recepção contínua de mensagens.

#### Métodos relevantes

##### `registrarUsuario(String nomeUsuario)`
- Monta um objeto `User`.
- Envia a requisição `register` via stub síncrono.

##### `enviarMensagem(ChatMessage mensagem)`
- Encaminha a mensagem para o RPC `sendMessage`.
- Retorna o `Ack` do servidor.

##### `receberMensagens(User usuario, StreamObserver<ChatMessage> observadorDeMensagens)`
- Abre o stream com `receiveMessages`.
- O observador recebe cada mensagem do servidor em tempo real.

##### `encerrarConexao()`
- Finaliza o canal gRPC quando a interface é fechada.

#### Relação com o trabalho
- **RFA01 – Registro:** o cliente inicia a participação da sala com nome único.
- **RFA03 – Envio de mensagens:** o cliente dispara mensagens para o servidor.
- **RFA05 – Recepção via stream:** o cliente mantém um fluxo contínuo para receber mensagens.

---

### 5.2 `UIChat`
A classe `UIChat` é a interface gráfica do usuário.

#### Fluxo principal da interface
1. A janela é configurada com um painel de mensagens, um campo de texto e um botão de envio.
2. Ao iniciar, o usuário informa o nome desejado.
3. O nome é enviado para registro por meio de `ClientChat.registrarUsuario(...)`.
4. Se o registro der certo, a interface passa a escutar as mensagens da sala.
5. A cada envio, a mensagem recebe timestamp e é transmitida ao servidor.
6. As mensagens recebidas são exibidas na área central da tela.

#### Métodos principais

##### `iniciarRegistro()`
- Solicita o nome do usuário.
- Tenta registrar no servidor até obter sucesso.
- Ao registrar com sucesso, atualiza o título da janela e inicia a recepção de mensagens.

##### `enviarMensagem()`
- Lê o texto digitado no campo inferior.
- Gera o timestamp atual com `Timestamp`.
- Cria um `ChatMessage` com:
  - remetente (`from`),
  - conteúdo (`content`),
  - timestamp (`timestamp`).
- Envia a mensagem ao servidor.
- Limpa o campo de texto após confirmação.

##### `iniciarRecepcaoDeMensagens()`
- Cria um `User` com o nome registrado.
- Inicia o stream de mensagens recebidas.
- Atualiza a área de chat sempre que uma mensagem chega.

#### Relação com o trabalho
- **RFA01 – Registro de utilizador único:** a interface impede continuar sem um nome aceito.
- **RFA04 – Estrutura da mensagem:** o timestamp é criado antes do envio.
- **RFA05 – Recepção via stream:** as mensagens aparecem em tempo real na janela.
- **RFA07 – Notificações de entrada/saída:** eventos do sistema também são exibidos na interface.

---

## 6. Mapeamento entre requisitos e métodos

| Requisito | Classe/Método Principal | Papel na Implementação |
|---|---|---|
| **RFA01 – Registro de utilizador único** | `ServerChat.ChatServiceImpl.register` / `RoomChat.registrarUsuario` / `UIChat.iniciarRegistro` | Valida nome único e impede duplicidade. |
| **RFA02 – Sala única** | `ServerChat` / `RoomChat` | Mantém uma única instância central da sala no servidor. |
| **RFA03 – Envio de mensagens** | `UIChat.enviarMensagem` / `ClientChat.enviarMensagem` / `ServerChat.ChatServiceImpl.sendMessage` / `RoomChat.encaminharMensagemParaTodos` | Envia uma mensagem para todos os usuários conectados. |
| **RFA04 – Estrutura da mensagem** | `UIChat.enviarMensagem` | Garante remetente, conteúdo e timestamp. |
| **RFA05 – Receção via stream** | `ClientChat.receberMensagens` / `ServerChat.ChatServiceImpl.receiveMessages` / `UIChat.iniciarRecepcaoDeMensagens` | Mantém conexão ativa para receber mensagens continuamente. |
| **RFA06 – Ordenação** | `RoomChat.encaminharMensagemParaTodos` | Reencaminha as mensagens na ordem em que o servidor as processa. |
| **RFA07 – Notificações de entrada/saída** | `RoomChat.registrarObservadorMensagens` / `RoomChat.removerUsuario` / `RoomChat.notificarEntradaSaida` | Emite mensagens do sistema quando alguém entra ou sai. |

---

## 7. Sequência resumida de uso

### Ao iniciar o servidor
1. Executar `ServerChat`.
2. O servidor gRPC sobe na porta configurada.
3. A sala única é preparada para receber usuários e mensagens.

### Ao iniciar um cliente
1. Executar `UIChat`.
2. Informar um nome de usuário.
3. O nome é validado pelo servidor.
4. Se estiver livre, o cliente entra na sala.
5. O stream de mensagens é iniciado.

### Ao enviar uma mensagem
1. O usuário digita a mensagem.
2. A interface cria o `ChatMessage` com timestamp.
3. O cliente envia a mensagem ao servidor.
4. A sala reencaminha a mensagem para todos.
5. Todas as interfaces conectadas exibem a nova mensagem.

### Ao entrar ou sair da sala
1. O servidor registra o observador do usuário.
2. Uma mensagem do sistema é enviada para todos.
3. Se a conexão cair, o usuário é removido.
4. Outra mensagem do sistema informa a saída.

---

## 8. Observações finais
- O contrato `.proto` continua sendo a base da interoperabilidade.
- A lógica de estado fica concentrada em `RoomChat`, o que facilita manutenção.
- A interface `UIChat` não conhece detalhes internos do servidor; ela apenas usa `ClientChat`.
- O uso de streaming garante atualização em tempo real sem sockets manuais.
- Os nomes internos das classes e métodos foram organizados para refletir melhor os termos do trabalho, como `sala`, `usuário`, `mensagem`, `registro` e `notificação`.

---

## 9. Referência rápida dos arquivos
- `src/main/java/elc1018/grpc/chat/server/ServerChat.java`
- `src/main/java/elc1018/grpc/chat/server/RoomChat.java`
- `src/main/java/elc1018/grpc/chat/client/ClientChat.java`
- `src/main/java/elc1018/grpc/chat/ui/UIChat.java`
- `src/main/proto/contrato-chat.proto`

