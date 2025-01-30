package me.legrange.mikrotik.impl;

import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import me.legrange.mikrotik.impl.responses.DoneResponse;
import me.legrange.mikrotik.impl.responses.ResultResponse;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class SyncListener implements ResultListener {

    @Override
    public synchronized void error(MikrotikApiException ex) {
        this.err = ex;
        notifyAll();
    }

    @Override
    public synchronized void completed() {
        complete = true;
        notifyAll();
    }

    synchronized void completed(DoneResponse done) {
        if (done.getHash() != null) {
            ResultResponse res = new ResultResponse();
            res.put("ret", done.getHash());
            results.add(res);
        }
        complete = true;
        notifyAll();
    }

    @Override
    public void receive(Map<String, String> result) {
        results.add(result);
    }

    List<Map<String, String>> getResults(int timeout) throws MikrotikApiException {
        try {
            synchronized (this) { // don't wait if we already have a result.
                int waitTime = timeout;
                while (!complete && (waitTime > 0)) {
                    long start = System.currentTimeMillis();
                    wait(waitTime);
                    waitTime = waitTime - (int) (System.currentTimeMillis() - start);
                    if ((waitTime <= 0) && !complete) {
                        err = new ApiConnectionException(String.format("Command timed out after %d ms", timeout));
                    }
                }
            }
        } catch (InterruptedException ex) {
            throw new ApiConnectionException(ex.getMessage(), ex);
        }
        if (err != null) {
            throw new MikrotikApiException(err.getMessage(), err);
        }
        return results;
    }

    private final List<Map<String, String>> results = new LinkedList<>();
    private MikrotikApiException err;
    private boolean complete = false;
}
