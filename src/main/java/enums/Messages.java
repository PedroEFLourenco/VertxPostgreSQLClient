package enums;
/**
 * 
 * Enum created to store text constants corresponding to the messages being placed in the responses from the application, as well as the logs.
 * 
 * @author pedrolourenco
 *
 */
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
