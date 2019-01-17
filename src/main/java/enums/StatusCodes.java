package enums;

/**
 * 
 * Enum created to store the status codes to be included in the responses from the application.
 * Currently only supporting codes 200 and 500.
 * 
 * @author pedrolourenco
 *
 */
public enum StatusCodes {

	SUCCEEDED(200),
	FAILED(500);

	private final int value;

	StatusCodes(final int newValue) 
	{
		value = newValue;
	}

	public int getValue() 
	{ 
		return value; 
	}


}
