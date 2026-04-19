package elc1018.grpc.chat.client;

import elc1018.grpc.chat.protos.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class ClientChat {

    private final ManagedChannel canalGrpc;
    private final ChatServiceGrpc.ChatServiceBlockingStub stubSincrono;
    private final ChatServiceGrpc.ChatServiceStub stubAssincrono;

    public ClientChat(String host, int porta) {
        this.canalGrpc = ManagedChannelBuilder.forAddress(host, porta)
                .usePlaintext()
                .build();
        this.stubSincrono = ChatServiceGrpc.newBlockingStub(canalGrpc);
        this.stubAssincrono = ChatServiceGrpc.newStub(canalGrpc);
    }

    public RegisterResponse registrarUsuario(String nomeUsuario) {
        User usuario = User.newBuilder().setUsername(nomeUsuario).build();
        return stubSincrono.register(usuario);
    }

    public void encerrarConexao() {
        canalGrpc.shutdown();
    }

    public Ack enviarMensagem(ChatMessage mensagem) {
        return stubSincrono.sendMessage(mensagem);
    }

    public void receberMensagens(User usuario, StreamObserver<ChatMessage> observadorDeMensagens) {
        stubAssincrono.receiveMessages(usuario, observadorDeMensagens);
    }
}
