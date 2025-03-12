package me.legrange.mikrotik.exceptions;

import me.legrange.mikrotik.MikrotikApiException;

/**
 * Exception thrown if the parser encounters an error while parsing a command line.
 * @author GideonLeGrange
 */
public class ParseException extends MikrotikApiException {

    public ParseException(String msg) {
        super(msg);
    }

    ParseException(String msg, Throwable err) {
        super(msg, err);
    }
    
    
    
}
