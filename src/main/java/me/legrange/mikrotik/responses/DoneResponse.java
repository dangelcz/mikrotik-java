package me.legrange.mikrotik.responses;

/**
 * Internal representation of !done
 * @author GideonLeGrange
 */
public class DoneResponse extends ApiResponse
{
    public DoneResponse(String tag) {
        super(tag);
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
    
    public String getHash() {
        return hash;
    }
    
    private String hash;
    
}
