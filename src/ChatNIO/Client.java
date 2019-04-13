package ChatNIO;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class Client extends JFrame implements ActionListener {
    private JPanel inputPanel = new JPanel();
    private JTextField inputField = new JTextField(25);
    private JButton sendButton = new JButton("Send");
    private JTextArea chatArea = new JTextArea();

    private SocketChannel clientChannel;
    private Charset charset = Charset.forName("UTF-8");

    private String nickname;



    Client(String ip, int port) {
        if (registration()) {
            if (connectAndConfigure(ip, port)) {
                configureGUI();
                closeOperation();
                getDisplayMessage();
            }
        }


    }

    boolean registration() {
        while (true) {
            String nickName = JOptionPane.showInputDialog("Enter your Nick name(max 24 symbols)");
            if (nickName != null) {
                if (nickName.getBytes().length > 24 || nickName.length() == 0) {
                    int answer = JOptionPane.showConfirmDialog(null, "Invalid nick name. Try again?", "Try again?",
                            JOptionPane.YES_NO_OPTION);
                    if (answer == JOptionPane.CLOSED_OPTION || answer == JOptionPane.NO_OPTION)
                        return false;
                } else {
                    nickname = nickName;
                    return true;
                }
            }
            return false;
        }
    }

    boolean connectAndConfigure(String ip, int port) {
        try {
            clientChannel = SocketChannel.open(new InetSocketAddress(ip, port));
            clientChannel.write(charset.encode(nickname));
            clientChannel.configureBlocking(false);
            return true;
        } catch (IOException e) {
            System.out.println("Unknown host");
            return false;
        }
    }

    void configureGUI() {
        inputField.addActionListener(this);
        sendButton.addActionListener(this);
        inputPanel.add(inputField);
        inputPanel.add(sendButton);

        chatArea.setLineWrap(true);
        chatArea.setEditable(false);
        JScrollPane scrollChat = new JScrollPane(chatArea);

        add(inputPanel, BorderLayout.SOUTH);
        add(scrollChat);

        setSize(Toolkit.getDefaultToolkit().getScreenSize().width / 3,
                Toolkit.getDefaultToolkit().getScreenSize().height / 3);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    //executes when user press enter or send button
    @Override
    public void actionPerformed(ActionEvent e) {
        //we're not interesting in empty messages
        if (!inputField.getText().trim().isEmpty()) {
            String msg = inputField.getText();
            ByteBuffer outBuff = charset.encode(msg);
            try {
                if (clientChannel.isOpen()) {
                    clientChannel.write(outBuff);
                    outBuff.clear();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    //reading data from server and update text area
    void getDisplayMessage() {
        new Thread(() -> {
            while (true) {
                ByteBuffer msgBuffer = ByteBuffer.allocate(2048);
                try {
                    if (clientChannel.isOpen()) {
                        clientChannel.read(msgBuffer);
                        msgBuffer.flip();
                        updateTextArea(msgBuffer);
                    } else
                        return;
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    serverDisconnected();
                }
            }
        }).start();
    }

    void closeOperation() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                try {
                    clientChannel.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
    }

    //executes when client lose connection with server
    void serverDisconnected() {
        try {
            JOptionPane.showMessageDialog(null, "Server disconnected", "Connection error",
                    JOptionPane.INFORMATION_MESSAGE);
            clientChannel.close();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    void updateTextArea(ByteBuffer msg) {
        SwingUtilities.invokeLater(() -> {
            String msgTxt = new String(charset.decode(msg).array());
            if(!msgTxt.equals("")) {
                chatArea.append(msgTxt);
                chatArea.append("\n");
            }
        });
    }

    public static void main(String[] args) {
        new Client(args[0], Integer.parseInt(args[1]));
    }


}
