package me.legrange.mikrotik.responses;

/**
 * Super type of possible API responses
 *
 * @author GideonLeGrange
 */
public abstract class ApiResponse {

    public String getTag() {
        return tag;
    }
    
    @Override
    public String toString() {
        return String.format("%s: tag=%s", getClass().getSimpleName(), tag);
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    protected ApiResponse(String tag) {
        this.tag = tag;
    }
    
    private  String tag;
}
