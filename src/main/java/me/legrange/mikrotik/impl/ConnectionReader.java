package me.legrange.mikrotik.impl;

import com.sun.jmx.remote.internal.ArrayQueue;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.impl.exceptions.ApiDataException;
import me.legrange.mikrotik.impl.parsing.Util;

import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Thread to read data from the socket and process it into Strings
 */
class ConnectionReader extends Thread {

    private ApiConnectionImpl apiConnection;
    private Queue queue;

    ConnectionReader(ApiConnectionImpl apiConnection) {
        super("Mikrotik API Reader");
        this.apiConnection = apiConnection;
        this.queue = new PriorityQueue(40);
    }

    String take() throws ApiConnectionException, ApiDataException {
        Object val = queue.poll();

        if (val instanceof ApiConnectionException) {
            throw (ApiConnectionException) val;
        } else if (val instanceof ApiDataException) {
            throw (ApiDataException) val;
        }

        return (String) val;
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void run() {
        while (apiConnection.isConnected()) {
            try {
                String s = Util.decode(apiConnection.getInputStream());

                // empty response indicates that communication has been closed so end the reading
                if (s.length() == 0) {
                    return;
                }

                put(s);

                while (!isEmpty()) {
                    apiConnection.getProcessor().process();
                }

            } catch (ApiDataException ex) {
                put(ex);
            } catch (ApiConnectionException ex) {
                if (apiConnection != null && apiConnection.isConnected()) {
                    try {
                        put(ex);
                        apiConnection.close();
                    } catch (ApiConnectionException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void put(Object data) {
        queue.add(data);
    }
}
