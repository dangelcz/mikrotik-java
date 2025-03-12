package me.legrange.mikrotik.exceptions;

/**
 * Thrown if there is a problem unpacking data from the Api. 
 * @author GideonLeGrange
 */
public class ApiDataException extends MikrotikApiException {

    public ApiDataException(String msg) {
        super(msg);
    }

    public ApiDataException(String msg, Throwable err) {
        super(msg, err);
    }

    
    
}
