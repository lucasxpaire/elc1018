package elc1018.grpc.chat.client;

import elc1018.grpc.chat.protos.ChatServiceGrpc;
import elc1018.grpc.chat.protos.RegisterResponse;
import elc1018.grpc.chat.protos.User;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class ClientChatService {

    private final ManagedChannel canal;
    private final ChatServiceGrpc.ChatServiceBlockingStub bloqueadorStub;
    private final ChatServiceGrpc.ChatServiceStub stubAssincrono;

    public ClientChatService (String host, int porta) {
        this.canal = ManagedChannelBuilder.forAddress(host, porta)
                .usePlaintext()
                .build();
        this.bloqueadorStub = ChatServiceGrpc.newBlockingStub(canal);
        this.stubAssincrono = ChatServiceGrpc.newStub(canal);
    }

    public RegisterResponse registrar(String nomeUsuario) {
        User usuario = User.newBuilder().setUsername(nomeUsuario).build();
        return bloqueadorStub.register(usuario);
    }

    public void fecharConexao() {
        canal.shutdown();
    }
}
