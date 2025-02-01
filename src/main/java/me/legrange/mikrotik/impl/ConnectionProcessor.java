package me.legrange.mikrotik.impl;

import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import me.legrange.mikrotik.impl.exceptions.ApiCommandException;
import me.legrange.mikrotik.impl.exceptions.ApiDataException;
import me.legrange.mikrotik.impl.responses.ApiResponse;
import me.legrange.mikrotik.impl.responses.DoneResponse;
import me.legrange.mikrotik.impl.responses.ErrorResponse;
import me.legrange.mikrotik.impl.responses.ResultResponse;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Class that takes received strings and processes it into Result objects
 */
class ConnectionProcessor {

    private ApiConnectionImpl apiConnection;
    private List<String> lines;
    private String line;

    ConnectionProcessor(ApiConnectionImpl apiConnection) {
        this.apiConnection = apiConnection;
        this.lines = new LinkedList<>();
    }

    public void process() {
        ApiResponse res;
        try {
            res = unpack();
        } catch (ApiCommandException ex) {
            String tag = ex.getTag();
            if (tag == null) {
                return;
            }

            res = new ErrorResponse(tag, ex.getMessage(), ex.getCategory());
        } catch (MikrotikApiException ex) {
            return;
        }

        if (res.getTag() == null) {
            apiConnection.nextTag();
            return;
        }

        ResultListener l = apiConnection.getListener(res.getTag());
        if (l == null) {
            return;
        }

        if (res instanceof ResultResponse) {
            l.receive((ResultResponse) res);
        } else if (res instanceof DoneResponse) {
            if (l instanceof SyncListener) {
                ((SyncListener) l).completed((DoneResponse) res);
            } else {
                l.completed();
            }

            apiConnection.removeListener(res.getTag());
        } else if (res instanceof ErrorResponse) {
            l.error(new ApiCommandException((ErrorResponse) res));
        }
    }

    private void nextLine() throws ApiConnectionException, ApiDataException
    {
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

    private ApiResponse unpack() throws MikrotikApiException {
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

    private ResultResponse unpackRe() throws ApiDataException, ApiConnectionException {
        nextLine();
        ResultResponse res = new ResultResponse();
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

    private DoneResponse unpackDone() throws MikrotikApiException {
        DoneResponse done = new DoneResponse(null);
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

    private ErrorResponse unpackError() throws MikrotikApiException {
        nextLine();
        ErrorResponse err = new ErrorResponse();
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
}
