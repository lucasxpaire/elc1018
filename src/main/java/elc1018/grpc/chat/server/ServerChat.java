package elc1018.grpc.chat.server;

import elc1018.grpc.chat.protos.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.io.IOException;

public class ServerChat {
    public static void main(String[] args) throws IOException, InterruptedException {
        Server servidorGrpc = ServerBuilder.forPort(8080)
                .addService(new ChatServiceImpl())
                .build();
        servidorGrpc.start();
        servidorGrpc.awaitTermination();
    }

    private static class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
        private final RoomChat salaChat = new RoomChat();

        @Override
        public void register(User requisicao, StreamObserver<RegisterResponse> observadorResposta) {
            String nomeUsuario = requisicao.getUsername();
            boolean sucesso = salaChat.registrarUsuario(nomeUsuario);
            RegisterResponse resposta = RegisterResponse.newBuilder()
                    .setSuccess(sucesso)
                    .setUsername(nomeUsuario)
                    .build();
            observadorResposta.onNext(resposta);
            observadorResposta.onCompleted();
        }

        @Override
        public void sendMessage(ChatMessage requisicao, StreamObserver<Ack> observadorResposta) {
            salaChat.encaminharMensagemParaTodos(requisicao);
            Ack resposta = Ack.newBuilder().setSuccess(true).build();
            observadorResposta.onNext(resposta);
            observadorResposta.onCompleted();
        }

        @Override
        public void receiveMessages(User requisicao, StreamObserver<ChatMessage> observadorResposta) {
            String nomeUsuario = requisicao.getUsername();
            salaChat.registrarObservadorMensagens(nomeUsuario, observadorResposta);
            if (observadorResposta instanceof ServerCallStreamObserver<ChatMessage> canalCliente) {
                canalCliente.setOnCancelHandler(() -> {
                    System.out.println(nomeUsuario + " desconectou.");
                    salaChat.removerUsuario(nomeUsuario);
                });
            }
        }
    }
}
