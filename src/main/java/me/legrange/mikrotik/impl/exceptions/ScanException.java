package me.legrange.mikrotik.impl.exceptions;

/**
 * Exception thrown if the scanner encounters an error while scanning a command line.
 * @author GideonLeGrange
 */
public class ScanException extends ParseException {

    public ScanException(String msg) {
        super(msg);
    }

    ScanException(String msg, Throwable err) {
        super(msg, err);
    }
    
}
