package me.legrange.mikrotik.impl;

import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Thread to take the received strings and process it into Result objects
 */
class Processor extends Thread {

    private final ApiConnectionImpl apiConnection;

    Processor(ApiConnectionImpl apiConnection) {
        super("Mikrotik API Result Processor");
        this.apiConnection = apiConnection;
    }

    @Override
    public void run() {
        while (apiConnection.isConnected()) {
            Response res;
            try {
                res = unpack();
            } catch (ApiCommandException ex) {
                String tag = ex.getTag();
                if (tag != null) {
                    res = new Error(tag, ex.getMessage(), ex.getCategory());
                } else {
                    continue;
                }
            } catch (MikrotikApiException ex) {
                continue;
            }
            if (res.getTag() != null) {
                ResultListener l = apiConnection.getListener(res.getTag());
                if (l != null) {
                    if (res instanceof Result) {
                        l.receive((Result) res);
                    } else if (res instanceof Done) {
                        if (l instanceof SyncListener) {
                            ((SyncListener) l).completed((Done) res);
                        } else {
                            l.completed();
                        }
                        apiConnection.removeListener(res.getTag());
                    } else if (res instanceof Error) {
                        l.error(new ApiCommandException((Error) res));
                    }
                }
            } else {
                apiConnection.nextTag();
            }
        }
    }

    private void nextLine() throws ApiConnectionException, ApiDataException {
        if (lines.isEmpty()) {
            String block = apiConnection.getReader().take();
            String[] parts = block.split("\n");
            lines.addAll(Arrays.asList(parts));
        }
        line = lines.remove(0);
    }

    private boolean hasNextLine() {
        return !lines.isEmpty() || !apiConnection.getReader().isEmpty();
    }

    private String peekLine() throws ApiConnectionException, ApiDataException {
        if (lines.isEmpty()) {
            String block = apiConnection.getReader().take();
            String[] parts = block.split("\n");
            lines.addAll(Arrays.asList(parts));
        }
        return lines.get(0);
    }

    private Response unpack() throws MikrotikApiException {
        if (line == null) {
            nextLine();
        }
        switch (line) {
            case "!re":
                return unpackRe();
            case "!done":
                return unpackDone();
            case "!trap":
            case "!halt":
                return unpackError();
            case "":
            default:
                throw new ApiDataException(String.format("Unexpected line '%s'", line));
        }
    }

    private Result unpackRe() throws ApiDataException, ApiConnectionException {
        nextLine();
        Result res = new Result();
        while (!line.startsWith(("!"))) {
            if (line.startsWith(("="))) {
                String[] parts = line.split("=", 3);
                if (parts.length == 3) {
                    if (!parts[2].endsWith("\r")) {
                        res.put(parts[1], unpackResult(parts[2]));
                    } else {
                        final StringBuilder sb = new StringBuilder();
                        sb.append(parts[2]);
                        while (!lines.isEmpty()) {
                            nextLine();
                            sb.append(line);
                        }
                        res.put(parts[1], sb.toString());
                    }
                } else {
                    throw new ApiDataException(String.format("Malformed line '%s'", line));
                }
            } else if (line.startsWith(".tag=")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    res.setTag(parts[1]);
                }
            } else {
                throw new ApiDataException(String.format("Unexpected line '%s'", line));
            }
            if (hasNextLine()) {
                nextLine();
            } else {
                line = null;
                break;
            }
        }
        return res;
    }

    private String unpackResult(String first) throws ApiConnectionException, ApiDataException {
        StringBuilder buf = new StringBuilder(first);
        line = null;

        while (hasNextLine()) {
            String peek = peekLine();
            if (!(peek.startsWith("!") || peek.startsWith("=") || peek.startsWith(".tag="))) {
                nextLine();
                buf.append("\n");
                buf.append(line);
            } else {
                break;
            }
        }
        return buf.toString();
    }

    private Done unpackDone() throws MikrotikApiException {
        Done done = new Done(null);
        if (hasNextLine()) {
            nextLine();

            while (!line.startsWith("!")) {
                if (line.startsWith(".tag=")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        done.setTag(parts[1]);
                    }
                } else if (line.startsWith(("=ret"))) {
                    String[] parts = line.split("=", 3);
                    if (parts.length == 3) {
                        done.setHash(parts[2]);
                    } else {
                        throw new ApiDataException(String.format("Malformed line '%s'", line));
                    }
                }
                if (hasNextLine()) {
                    nextLine();
                } else {
                    line = null;
                    break;
                }
            }
        }
        return done;
    }

    private Error unpackError() throws MikrotikApiException {
        nextLine();
        Error err = new Error();
        if (hasNextLine()) {
            while (!line.startsWith("!")) {
                if (line.startsWith(".tag=")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        err.setTag(parts[1]);
                    }
                } else if (line.startsWith("=message=")) {
                    err.setMessage(line.split("=", 3)[2]);
                }
                else if (line.startsWith("=category=")) {
                    err.setCategory(Integer.parseInt(line.split("=", 3)[2]));
                }
                if (hasNextLine()) {
                    nextLine();
                } else {
                    line = null;
                    break;
                }
            }
        }
        return err;
    }

    private final List<String> lines = new LinkedList<>();
    private String line;
}
