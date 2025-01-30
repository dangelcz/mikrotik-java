package me.legrange.mikrotik.impl.exceptions;

import me.legrange.mikrotik.MikrotikApiException;

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
