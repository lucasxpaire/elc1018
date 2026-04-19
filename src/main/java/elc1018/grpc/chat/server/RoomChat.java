package elc1018.grpc.chat.server;

import com.google.protobuf.Timestamp;
import elc1018.grpc.chat.protos.ChatMessage;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoomChat {

    private final Set<String> usuarios = ConcurrentHashMap.newKeySet();

    private final Map<String, StreamObserver<ChatMessage>> observadoresMensagens = new ConcurrentHashMap<>();

    public boolean registrarUsuario(String nomeUsuario) {
        return usuarios.add(nomeUsuario);
    }

    public void registrarObservadorMensagens(String nomeUsuario, StreamObserver<ChatMessage> observadorMensagens) {
        observadoresMensagens.put(nomeUsuario, observadorMensagens);
        notificarEntradaSaida(nomeUsuario + " entrou na sala.");
    }

    public void removerUsuario(String nomeUsuario) {
        StreamObserver<ChatMessage> observadorMensagens = observadoresMensagens.remove(nomeUsuario);
        usuarios.remove(nomeUsuario);
        if (observadorMensagens != null) {
            notificarEntradaSaida(nomeUsuario + " saiu da sala.");
            try {
                observadorMensagens.onCompleted();
            } catch (Exception e) {
                System.err.println(nomeUsuario + " já tinha desconectado.");
            }
        }
    }

    public void encaminharMensagemParaTodos(ChatMessage mensagem) {
        for (Map.Entry<String, StreamObserver<ChatMessage>> entry : observadoresMensagens.entrySet()) {
            String nomeUsuario = entry.getKey();
            StreamObserver<ChatMessage> observadorMensagens = entry.getValue();
            try {
                observadorMensagens.onNext(mensagem);
            } catch (Exception e) {
                System.err.println("A ligação com " + nomeUsuario + " caiu. Removendo da sala.");
                removerUsuario(nomeUsuario);
            }
        }
    }

    private void notificarEntradaSaida(String conteudo) {
        Instant instanteAtual = Instant.now();
        Timestamp timestampMensagem = Timestamp.newBuilder()
                .setSeconds(instanteAtual.getEpochSecond())
                .setNanos(instanteAtual.getNano())
                .build();
        ChatMessage mensagemSistema = ChatMessage.newBuilder()
                .setFrom("SISTEMA")
                .setContent(conteudo)
                .setTimestamp(timestampMensagem)
                .build();
        encaminharMensagemParaTodos(mensagemSistema);
    }

}
