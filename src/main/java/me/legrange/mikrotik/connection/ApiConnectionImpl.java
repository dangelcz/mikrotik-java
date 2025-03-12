package me.legrange.mikrotik.connection;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.SocketFactory;

import me.legrange.mikrotik.exceptions.ApiConnectionException;
import me.legrange.mikrotik.exceptions.ApiDataException;
import me.legrange.mikrotik.exceptions.MikrotikApiException;
import me.legrange.mikrotik.parsing.Command;
import me.legrange.mikrotik.parsing.Parser;
import me.legrange.mikrotik.parsing.Util;

/**
 * The Mikrotik API connection implementation. This is the class used to connect
 * to a remote Mikrotik and send commands to it.
 *
 * @author GideonLeGrange
 */
public final class ApiConnectionImpl extends ApiConnection {

    /**
     * Create a new API connection to the give device on the supplied port
     *
     * @param fact The socket factory used to construct the connection socket.
     * @param host The host to which to connect.
     * @param port The TCP port to use.
     * @param timeOut The connection timeout
     * @return The ApiConnection
     * @throws ApiConnectionException Thrown if there is a
     * problem connecting
     */
    public static ApiConnection connect(SocketFactory fact, String host, int port, int timeOut) throws ApiConnectionException {
        ApiConnectionImpl con = new ApiConnectionImpl();
        con.open(host, port, fact, timeOut);
        return con;
    }

    private Socket sock = null;
    private DataOutputStream out = null;
    private DataInputStream in = null;
    private boolean connected = false;
    private ConnectionReader reader;
    private ConnectionProcessor processor;
    private final Map<String, ResultListener> listeners;
    private final AtomicInteger _tag = new AtomicInteger(0);
    private int timeout = ApiConnection.DEFAULT_COMMAND_TIMEOUT;

    private ApiConnectionImpl() {
        this.listeners = new ConcurrentHashMap<>();
    }

    @Override
    public boolean isConnected() {
        return connected; // && reader.isAlive();
    }

    @Override
    public void login(String username, String password) throws MikrotikApiException
    {
        if (username.trim().isEmpty()) {
            throw new ApiConnectionException("API username cannot be empty");
        }
        Command cmd = new Command("/login");
        cmd.addParameter("name", username);
        cmd.addParameter("password", password);
        List<Map<String, String>> list = execute(cmd, timeout);
        if (!list.isEmpty()) {
            Map<String, String> res = list.get(0);
            if (res.containsKey("ret")) {
                String hash = res.get("ret");
                String chal = Util.hexStrToStr("00") + new String(password.toCharArray()) + Util.hexStrToStr(hash);
                chal = Util.hashMD5(chal);
                execute("/login name=" + username + " response=00" + chal);
            }
        }
    }

    @Override
    public List<Map<String, String>> execute(String cmd) throws MikrotikApiException {
        return execute(Parser.parse(cmd), timeout);
    }

    @Override
    public String execute(String cmd, ResultListener lis) throws MikrotikApiException {
        return execute(Parser.parse(cmd), lis);
    }

    @Override
    public void cancel(String tag) throws MikrotikApiException {
        execute(String.format("/cancel tag=%s", tag));
    }

    @Override
    public void setTimeout(int timeout) throws MikrotikApiException {
        if (timeout > 0) {
            this.timeout = timeout;
        } else {
            throw new MikrotikApiException(String.format("Invalid timeout value '%d'; must be positive", timeout));
        }
    }

    @Override
    public void close() throws ApiConnectionException {
        if (!connected) {
            throw new ApiConnectionException("Not/no longer connected to remote Mikrotik");
        }
        connected = false;
        reader.interrupt();

        try {
            in.close();
            out.close();
            sock.close();
        } catch (IOException ex) {
            throw new ApiConnectionException(String.format("Error closing socket: %s", ex.getMessage()), ex);
        }
    }

    private String closeConnection() {
        String errMsg = "";

        errMsg += closeResource(out);
        errMsg += closeResource(in);
        errMsg += closeResource(sock);

        return errMsg;
    }

    private String closeResource(Closeable c) {
        try {
            c.close();
        } catch (Exception e) {
            return String.format("Error closing : %s\n", e.getMessage());
        }

        return "";
    }

    private List<Map<String, String>> execute(Command cmd, int timeout) throws MikrotikApiException {
        SyncListener l = new SyncListener(this);
        execute(cmd, l);
        return l.getResults(timeout);
    }

    private String execute(Command cmd, ResultListener lis) throws MikrotikApiException {
        String tag = nextTag();
        cmd.setTag(tag);
        listeners.put(tag, lis);
        try {
            Util.write(cmd, out);
        } catch (UnsupportedEncodingException ex) {
            throw new ApiDataException(ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new ApiConnectionException(ex.getMessage(), ex);
        }
        return tag;
    }

    /**
     * Start the API. Connects to the Mikrotik
     */
    private void open(String host, int port, SocketFactory fact, int conTimeout) throws ApiConnectionException {
        try {
            InetAddress ia = InetAddress.getByName(host.trim());
            sock = fact.createSocket();
            sock.setSoTimeout(conTimeout);
            sock.connect(new InetSocketAddress(ia, port), conTimeout);

            in = new DataInputStream(sock.getInputStream());
            out = new DataOutputStream(sock.getOutputStream());

            connected = true;

            processor = new ConnectionProcessor(this);
            reader = new ConnectionReader(this);
            reader.setDaemon(true);
            reader.start();

        } catch (UnknownHostException ex) {
            connected = false;
            throw new ApiConnectionException(String.format("Unknown host '%s'", host), ex);
        } catch (IOException ex) {
            connected = false;
            throw new ApiConnectionException(String.format("Error connecting to %s:%d : %s", host, port, ex.getMessage()), ex);
        }
    }

    synchronized String nextTag() {
        return Integer.toHexString(_tag.incrementAndGet());
    }

    public ResultListener getListener(String tag) {
        return listeners.get(tag);
    }

    public void removeListener(String tag) {
        listeners.remove(tag);
    }

    public ConnectionReader getReader() {
        return reader;
    }

    public InputStream getInputStream() {
        return in;
    }

    public OutputStream getOutputStream() {
        return out;
    }

    public Socket getSocket() {
        return sock;
    }

    public ConnectionProcessor getProcessor() {
        return processor;
    }
}
