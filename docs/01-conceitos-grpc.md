# 📝 Documentação Técnica: Chat gRPC Distribuído

## 1. Arquitetura do Sistema
A aplicação é dividida em dois processos principais, comunicando-se exclusivamente via chamadas de procedimento remoto (RPC):
- **ServerChat (`ChatServer`):** Responsável por gerenciar o estado da sala, validar registros e despachar mensagens.
- **ClientChat (`ChatClient` & `ChatUI`):** Interface do usuário e lógica de comunicação do lado do cliente.

## 2. O Contrato (`contrato-chat.proto`)
[cite_start]O arquivo `.proto` define o "contrato" rigoroso entre cliente e servidor, garantindo interoperabilidade entre diferentes implementações[cite: 7, 8, 9].

* **Modelos de Comunicação:**
    * `Register` e `SendMessage`: Modelos **Unary** (Requisição-Resposta padrão).
    * [cite_start]`ReceiveMessages`: Modelo **Server Streaming**, permitindo que o servidor "empurre" (push) mensagens para o cliente assim que elas chegam[cite: 7].

## 3. Mapeamento de Requisitos Funcionais (RFAs)

### RFA01 - Registro de Usuário Único
* **Código:** `ChatServiceImpl.register` e `ChatRoom.registrarUsuario`.
* [cite_start]**Por que está aqui:** Para garantir que não existam dois usuários com o mesmo nome na sala[cite: 11, 13].
* [cite_start]**Detalhe Técnico:** Utilizamos um `ConcurrentHashMap.newKeySet()` para armazenar nomes de forma *thread-safe*, garantindo que o método `add()` retorne `false` se o nome já existir, rejeitando o registro[cite: 12].

### RFA02, RFA03 e RFA05 - Gerenciamento da Sala e Mensagens
* **Código:** `ChatRoom.java`.
* [cite_start]**Por que está aqui:** Concentra a lógica da "Sala Única"[cite: 14].
* **Lógica:** * O servidor mantém um `Map` de `StreamObserver`. [cite_start]Quando o `SendMessage` é chamado[cite: 15], o servidor itera por este mapa e replica a mensagem para todos os fluxos ativos via `onNext()`.

### RFA04 - Estrutura da Mensagem
* **Código:** `ChatUI.enviarMensagem`.
* [cite_start]**Por que está aqui:** Toda mensagem deve obrigatoriamente conter remetente, conteúdo e um carimbo de tempo (timestamp)[cite: 16].
* [cite_start]**Detalhe Técnico:** Usamos `google.protobuf.Timestamp` para padronizar a hora entre sistemas com fusos horários diferentes[cite: 26].

### RFA07 - Notificações de Eventos (Entrada/Saída)
[cite_start]Este requisito exige que o sistema avise a todos quando alguém entra ou sai[cite: 19].

#### O Desafio da Desconexão em Sistemas Distribuídos
Em sistemas distribuídos, um cliente pode "desaparecer" (queda de energia, fechamento da janela) sem avisar explicitamente ao servidor.

* **A Solução (O ajuste feito):**
```java
if (observadorDeResposta instanceof ServerCallStreamObserver<ChatMessage> canalDoCliente) {
    canalDoCliente.setOnCancelHandler(() -> {
        System.out.println(nomeUsuario + " desconectou.");
        gerenciador.removerUsuarioECanal(nomeUsuario);
    });
}
```
* **Por que isso é útil:** O gRPC mantém uma conexão HTTP/2 aberta para o streaming. O `setOnCancelHandler` é um gatilho nativo que detecta quando essa conexão é interrompida. Isso permite que o servidor limpe o estado imediatamente, liberando o nome do usuário e notificando a sala em tempo real, sem esperar por uma tentativa falha de envio.

## 4. Padrões de Código (Clean Code)

| Elemento | Razão da Escolha | Relacionamento com a Espec. |
| :--- | :--- | :--- |
| **`ChatRoom`** | Classe de estado (Manager). | Isola a lógica de negócio do protocolo gRPC. |
| **Nomes Declarativos** | Ex: `espalharMensagemParaTodos`. | Facilita a manutenção por terceiros, tornando o código autoexplicativo. |
| **Concorrência** | `ConcurrentHashMap`. | Essencial, pois o gRPC atende requisições em múltiplas threads simultaneamente. |

## 5. Restrições Respeitadas
- [cite_start]**Sem Sockets Diretos:** Toda a camada de transporte é abstraída pelo gRPC[cite: 20].
- [cite_start]**Protocolo Intocado:** O arquivo `.proto` não sofreu alterações, garantindo a conformidade com o contrato original[cite: 20].
- [cite_start]**Interface Gráfica:** Implementada em Java Swing, separada da lógica de comunicação (`ChatClient`)[cite: 2].

---

### Como executar:
1. Inicie o `ChatServer`.
2. Inicie múltiplas instâncias do `ChatUI`.
3. Registre-se com nomes diferentes e observe as notificações de entrada/saída no painel de chat.

---

### Referências Bibliográficas:
* [cite_start]Especificação do Trabalho I - ELC1018 Sistemas Distribuídos [cite: 1-27].
* [cite_start]Documentação Oficial Protocol Buffers (Google)[cite: 22].