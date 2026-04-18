# Chat Distribuído com gRPC e Protobuf

**Disciplina:** ELC1018 - Sistemas Distribuídos  
**Tecnologias:** Java 21, gRPC, Protocol Buffers, Maven

## 📖 Descrição do Projeto
Aplicação distribuída de Chat com suporte a uma única sala (room do servidor). O sistema explora diferentes modelos de comunicação (unary e server-side streaming) e garante interoperabilidade através de um contrato estrito definido em Protocol Buffers. O sistema é composto por dois processos distintos: `ServerChat` e `ClientChat`.

---

## Requisitos Funcionais Absolutos (RFAs)

- [ ] **RFA01 (Registro):** Um utilizador deve registar-se com um nome único através do método Unary `Register(User) returns (RegisterResponse)`. Nomes duplicados devem ser rejeitados.
- [ ] **RFA02 (Sala Única):** A sala deve ser criada pelo servidor, existindo apenas uma única sala que aceita múltiplos utilizadores.
- [ ] **RFA03 (Envio de Mensagens):** As mensagens devem ser reencaminhadas a todos os utilizadores da sala via chamada Unary `SendMessage(ChatMessage) returns (Ack)`.
- [ ] **RFA04 (Estrutura da Mensagem):** Cada `ChatMessage` deve conter o remetente (`from`), o conteúdo (`content`) e a data/hora (`timestamp`).
- [ ] **RFA05 (Receção via Stream):** Para receber mensagens, cada utilizador deve manter uma conexão ativa com o servidor através do método Server Streaming `ReceiveMessages(User) returns (stream ChatMessage)`.
- [ ] **RFA06 (Ordenação):** As mensagens recebidas devem preservar a ordem por remetente (ordem de envio do emissor).
- [ ] **RFA07 (Eventos de Notificação):** A aplicação deve emitir eventos de notificação (envio de `ChatMessage`) a todos os membros quando um utilizador entra ou sai da sala.

### Restrições do Projeto
- Não é permitido utilizar Sockets diretamente.
- O ficheiro `contrato-chat.proto` não pode ser alterado.
- Não é permitido utilizar REST/HTTP como alternativa.
- Proibido o uso de bibliotecas externas de chat.

---

## Como Compilar e Executar

### 1. Compilar o Contrato e Gerar Classes (Stubs/Skeletons)
Execute o comando Maven para gerar o código Java a partir do ficheiro `.proto`:
```bash
mvn clean compile