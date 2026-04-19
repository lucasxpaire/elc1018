package elc1018.grpc.chat.ui;

import com.google.protobuf.Timestamp;
import elc1018.grpc.chat.client.ClientChat;
import elc1018.grpc.chat.protos.Ack;
import elc1018.grpc.chat.protos.ChatMessage;
import elc1018.grpc.chat.protos.RegisterResponse;
import elc1018.grpc.chat.protos.User;
import io.grpc.stub.StreamObserver;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;

public class UIChat extends JFrame {

    private final ClientChat clienteChat;
    private String nomeUsuario;

    private JTextArea areaMensagens;
    private JTextField campoMensagem;

    public UIChat(ClientChat clienteChat) {
        this.clienteChat = clienteChat;
        configurarJanela();
    }

    private void configurarJanela() {
        setTitle("Chat gRPC");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("Desconectando do servidor...");
                clienteChat.encerrarConexao();
                System.exit(0);
            }
        });
        setLayout(new BorderLayout());

        areaMensagens = new JTextArea();
        areaMensagens.setEditable(false);
        areaMensagens.setLineWrap(true);
        areaMensagens.setFont(new Font("SansSerif", Font.PLAIN, 14));
        add(new JScrollPane(areaMensagens), BorderLayout.CENTER);

        JPanel painelInferior = new JPanel(new BorderLayout());
        campoMensagem = new JTextField();

        JButton botaoEnviar;
        botaoEnviar = new JButton("Enviar");
        painelInferior.add(campoMensagem, BorderLayout.CENTER);
        painelInferior.add(botaoEnviar, BorderLayout.EAST);
        add(painelInferior, BorderLayout.SOUTH);

        ActionListener acaoEnviar = e -> enviarMensagem();
        botaoEnviar.addActionListener(acaoEnviar);
        campoMensagem.addActionListener(acaoEnviar);
    }

    public void iniciarRegistro() {
        boolean registrado = false;
        while (!registrado) {
            nomeUsuario = JOptionPane.showInputDialog(this, "Qual é o seu nome de usuário?", "Registro", JOptionPane.QUESTION_MESSAGE);
            if (nomeUsuario == null || nomeUsuario.trim().isEmpty()) {
                System.exit(0);
            }

            RegisterResponse resposta = clienteChat.registrarUsuario(nomeUsuario);
            if (resposta.getSuccess()) {
                registrado = true;
                setTitle("Chat gRPC - " + nomeUsuario);
                iniciarRecepcaoDeMensagens();
                setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "Nome de usuário já em uso. Tente outro.", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

    }

    public void enviarMensagem() {
        String textoMensagem = campoMensagem.getText().trim();
        if (!textoMensagem.isEmpty()) {
            long milissegundos = Instant.now().toEpochMilli();
            Timestamp instanteMensagem = Timestamp.newBuilder()
                    .setSeconds(milissegundos / 1000)
                    .setNanos((int) ((milissegundos % 1000) * 1000000))
                    .build();

            ChatMessage mensagem = ChatMessage.newBuilder()
                    .setFrom(nomeUsuario)
                    .setContent(textoMensagem)
                    .setTimestamp(instanteMensagem)
                    .build();

            Ack resposta = clienteChat.enviarMensagem(mensagem);
            if (resposta.getSuccess()) {
                campoMensagem.setText("");
            } else {
                JOptionPane.showMessageDialog(this, "Erro de conexão: A mensagem não foi entregue.", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void iniciarRecepcaoDeMensagens() {
        User usuario = User.newBuilder().setUsername(nomeUsuario).build();

        clienteChat.receberMensagens(usuario, new StreamObserver<>() {
            @Override
            public void onNext(ChatMessage mensagem) {
                SwingUtilities.invokeLater(() -> areaMensagens.append("[" + mensagem.getFrom() + "]: " + mensagem.getContent() + "\n"));
            }

            @Override
            public void onError(Throwable t) {
                SwingUtilities.invokeLater(() -> areaMensagens.append("Erro na rede: " + t.getMessage() + "\n"));
            }

            @Override
            public void onCompleted() {
                SwingUtilities.invokeLater(() -> areaMensagens.append("Servidor encerrou a conexão.\n"));
            }
        });
    }

    public static void main(String[] args) {
        ClientChat clienteChat = new ClientChat("localhost", 8080);
        UIChat interfaceChat = new UIChat(clienteChat);
        interfaceChat.iniciarRegistro();
    }
}
