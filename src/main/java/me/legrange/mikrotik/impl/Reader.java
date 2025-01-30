package me.legrange.mikrotik.impl;

import me.legrange.mikrotik.ApiConnectionException;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * thread to read data from the socket and process it into Strings
 */
class Reader extends Thread {

    private final ApiConnectionImpl apiConnection;

    Reader(ApiConnectionImpl apiConnection) {
        super("Mikrotik API Reader");
        this.apiConnection = apiConnection;
    }

    String take() throws ApiConnectionException, ApiDataException {
        Object val;
        try {
            val = queue.take();
        } catch (InterruptedException ex) {
            throw new ApiConnectionException("Interrupted while reading data from queue.", ex);
        }
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
                put(s);
            } catch (ApiDataException ex) {
                put(ex);
            } catch (ApiConnectionException ex) {
                if (apiConnection.isConnected() || !apiConnection.getSocket().isClosed()) {
                    put(ex);
                }
            }
        }
    }

    private void put(Object data) {
        try {
            queue.put(data);
        } catch (InterruptedException ignored) {
        }
    }

    private final LinkedBlockingQueue queue = new LinkedBlockingQueue(40);
}
