package utils;

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
