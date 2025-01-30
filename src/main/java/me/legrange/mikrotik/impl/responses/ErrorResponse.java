package me.legrange.mikrotik.impl.responses;

/**
 * Used to encapsulate API error information. We need to pass both the message and the tag (if one was used).
 *
 * @author GideonLeGrange
 */
public class ErrorResponse extends ApiResponse
{

    private String message;
    private int category;

    public ErrorResponse(String tag, String message, int category) {
        super(tag);
        this.message = message;
    }

    public ErrorResponse() {
        super(null);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getCategory() {
        return category;
    }

    public void setCategory(int category) {
        this.category = category;
    }
}
