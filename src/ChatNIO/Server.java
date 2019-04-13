package ChatNIO;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.lang.Thread.sleep;

public class Server {
    private int port;
    private Selector connectionService;
    private ServerSocketChannel serverChannel;
    private Map<String, Integer> nickMap = new HashMap<>();
    private Charset encoderDecoder = Charset.forName("UTF-8");

    Server(int port) throws IOException {
        this.port = port;
        configureServer();
        monitorConnections();
    }


    public void configureServer() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        connectionService = Selector.open();
        serverChannel.register(connectionService, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");
    }


    //selecting ready for I/O channels and somehow process them
    public void monitorConnections() {
        while (true) {
            try {
                connectionService.select();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Set keys = connectionService.selectedKeys();
            Iterator connectionIter = keys.iterator();
            while (connectionIter.hasNext()) {
                SelectionKey currentKey = (SelectionKey) connectionIter.next();
                try {
                    //someone want to establish connection
                    if (currentKey.isAcceptable()) {
                        acceptAndRegisterNewConnection();
                        connectionIter.remove();
                        continue;
                    }
                    //someone want to send data to chat and ready to get response
                    if (currentKey.isReadable() && currentKey.isWritable()) {
                        ByteBuffer msgBuffer = readMessageFrom(currentKey);
                        if (msgBuffer != null) {
                            sendMsgToAll(connectionService.selectedKeys(), msgBuffer);
                            msgBuffer.clear();
                        }else
                            closeConnectionAndUpdateMap(currentKey, connectionIter);
                    }
                    sleep(50);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void acceptAndRegisterNewConnection() throws IOException {
        SocketChannel newConnection = serverChannel.accept();
        try {
            sleep(100);
            ByteBuffer buffer = ByteBuffer.allocate(24);
            newConnection.configureBlocking(false);
            newConnection.read(buffer);
            buffer.flip();
            String nickName = increaseNickNameInMapAndGet(new String(buffer.array()));
            newConnection.register(connectionService, SelectionKey.OP_READ | SelectionKey.OP_WRITE, nickName);
        } catch (IOException e) {
            try {
                e.printStackTrace();
                newConnection.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public ByteBuffer readMessageFrom(SelectionKey clientKey) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(2048);
        SocketChannel clientChannel = (SocketChannel) clientKey.channel();
        clientChannel.read(buffer);
        buffer.flip();
        return prepareMsg(buffer, (String) clientKey.attachment());
    }

    public void sendMsgToAll(Set<SelectionKey> channels, ByteBuffer msgBuffer) throws IOException {
        Iterator<SelectionKey> clientChannelIter = channels.iterator();
        while (clientChannelIter.hasNext()) {
            SelectionKey currentChannelKey = clientChannelIter.next();
            SocketChannel clientChannel = (SocketChannel) currentChannelKey.channel();
            clientChannel.write(msgBuffer);
            msgBuffer.rewind();
        }
    }
    //extract msg from buffer and prepare it to send to chat users
    public ByteBuffer prepareMsg(ByteBuffer buffer, String nickName) {
        String msg = new String(buffer.array(), 0, buffer.limit());
        if (!msg.equals("")) {
            StringBuilder builder = new StringBuilder();
            builder.append(nickName).append(": ").append(msg);
            return encoderDecoder.encode(builder.toString());
        }
        return null;
    }

    //execute when someone disconnecting
    public void closeConnectionAndUpdateMap(SelectionKey currentKey, Iterator connectionIter) {
        try {

            decreaseNickNameInMap((String) currentKey.attachment());
            SocketChannel channel = (SocketChannel) currentKey.channel();
            channel.close();
            connectionIter.remove();
            currentKey.cancel();
        } catch (IOException ignored) {
        }
    }

    //makes every nickname unique(not always)
    public String increaseNickNameInMapAndGet(String nickName) {
        String newNickname = nickName;
        if (nickMap.containsKey(nickName)) {
            int count = nickMap.get(nickName);
            newNickname = nickName + "(" + count + ")";
            nickMap.put(nickName, count + 1);
        } else
            nickMap.put(nickName, 0);
        return newNickname;
    }

    //we want to sign out user(can provide to bugged nicks in future)
    public void decreaseNickNameInMap(String nickName) {
        String plainNick = nickName;
        if (!nickMap.containsKey(plainNick))
            plainNick = nickName.substring(0, nickName.lastIndexOf("("));
        int count = nickMap.get(plainNick);
        if (count > 0)
            nickMap.put(plainNick, count - 1);
        else
            nickMap.remove(plainNick);
    }

    public static void main(String[] args) {
        try {
            new Server(8080);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

