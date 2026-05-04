package elc1018.grpc.chat.ui;

import com.google.protobuf.util.Timestamps;
import elc1018.grpc.chat.client.ClientChat;
import elc1018.grpc.chat.protos.Ack;
import elc1018.grpc.chat.protos.ChatMessage;
import elc1018.grpc.chat.protos.RegisterResponse;
import elc1018.grpc.chat.protos.User;
import io.grpc.stub.StreamObserver;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UIChat extends JFrame {

    private final ClientChat clienteChat;
    private String nomeUsuario;

    private JTextPane areaMensagens;
    private JTextField campoMensagem;
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    private final java.util.Map<String, String> mapaCores = new java.util.HashMap<>();

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
                clienteChat.encerrarConexao();
                System.exit(0);
            }
        });
        setLayout(new BorderLayout());

        areaMensagens = new JTextPane();
        areaMensagens.setEditable(false);
        areaMensagens.setContentType("text/html");
        areaMensagens.setEditorKit(new HTMLEditorKit());
        areaMensagens.setText("<html><body id='corpo'></body></html>");

        add(new JScrollPane(areaMensagens), BorderLayout.CENTER);

        JPanel painelInferior = new JPanel(new BorderLayout());
        campoMensagem = new JTextField();
        JButton botaoEnviar = new JButton("Enviar");

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
            if (nomeUsuario == null || nomeUsuario.trim().isEmpty()) System.exit(0);

            RegisterResponse resposta = clienteChat.registrarUsuario(nomeUsuario);
            if (resposta.getSuccess()) {
                registrado = true;
                setTitle("Chat gRPC - " + nomeUsuario);
                iniciarRecepcaoDeMensagens();
                setVisible(true);
            } else {
                String mensagemErro = nomeUsuario.equalsIgnoreCase("sistema") ?
                        "O nome 'SISTEMA' é reservado." : "Nome de usuário já em uso.";
                JOptionPane.showMessageDialog(this, mensagemErro, "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void enviarMensagem() {
        String textoMensagem = campoMensagem.getText().trim();
        if (!textoMensagem.isEmpty()) {
            ChatMessage mensagem = ChatMessage.newBuilder()
                    .setFrom(nomeUsuario)
                    .setContent(textoMensagem)
                    .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis()))
                    .build();

            Ack resposta = clienteChat.enviarMensagem(mensagem);
            if (resposta.getSuccess()) {
                campoMensagem.setText("");
            }
        }
    }

    private void adicionarMensagemAoChat(String html) {
        HTMLDocument doc = (HTMLDocument) areaMensagens.getDocument();
        HTMLEditorKit kit = (HTMLEditorKit) areaMensagens.getEditorKit();
        try {
            kit.insertHTML(doc, doc.getLength(), html, 0, 0, null);
            areaMensagens.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void iniciarRecepcaoDeMensagens() {
        User usuario = User.newBuilder().setUsername(nomeUsuario).build();

        clienteChat.receberMensagens(usuario, new StreamObserver<>() {
            @Override
            public void onNext(ChatMessage m) {
                String hora = sdf.format(new Date(Timestamps.toMillis(m.getTimestamp())));
                String cor = obterCorUsuario(m.getFrom());

                String nomeExibicao = m.getFrom().equals(nomeUsuario) ? "<b>" + m.getFrom() + "</b>" : m.getFrom();

                String html = String.format(
                        "<div style='font-family:sans-serif; margin-bottom:3px;'>" +
                                "<span style='color:gray; font-size:10px;'>[%s]</span> " +
                                "<span style='color:%s;'>%s</span>: %s</div>",
                        hora, cor, nomeExibicao, m.getContent()
                );

                SwingUtilities.invokeLater(() -> adicionarMensagemAoChat(html));
            }

            @Override
            public void onError(Throwable t) {
                SwingUtilities.invokeLater(() -> adicionarMensagemAoChat("<i style='color:red;'>Erro na rede.</i>"));
            }

            @Override
            public void onCompleted() {
                SwingUtilities.invokeLater(() -> adicionarMensagemAoChat("<i>Conexão encerrada.</i>"));
            }
        });
    }

    private String obterCorUsuario(String nome) {
        if (nome.equals(this.nomeUsuario)) return "#0056b3";

        if (nome.equalsIgnoreCase("SISTEMA")) return "#666666";

        return mapaCores.computeIfAbsent(nome, k -> {
            java.util.Random rand = new java.util.Random();
            return String.format("#%02x%02x%02x", rand.nextInt(150), rand.nextInt(150), rand.nextInt(150));
        });
    }

    public static void main(String[] args) {
        ClientChat clienteChat = new ClientChat("localhost", 8080);
        new UIChat(clienteChat).iniciarRegistro();
    }
}