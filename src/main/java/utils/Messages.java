package utils;

public enum Messages {
	
	QUERY_EXECUTION_ERROR("SQL statement execution not successful: "),
	INVALID_BODY_ERROR("Request Body is not valid"),
	DB_CONNECTION_ERROR("Failed to get JDBC Connection: "),
	QUERY_EXECUTION_SUCCESS("SQL Statement successfully executed ");

	private final String value;

	Messages(final String newValue) 
	{
		value = newValue;
	}

	public String getValue() 
	{ 
		return value; 
	}

}
