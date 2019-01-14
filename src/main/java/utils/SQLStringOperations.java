package utils;

import java.util.Optional;

import io.vertx.core.json.JsonArray;

public class SQLStringOperations 
{


	public static String valuesToSQLString(JsonArray valuesAsJsonArray) 
	{
		StringBuffer sqlString = new StringBuffer();
		for(int i = 0; i < valuesAsJsonArray.size(); i++)
		{
			sqlString.append("(");
			JsonArray row = valuesAsJsonArray.getJsonArray(i);
			for (int j = 0; j < row.size(); j++)
			{
				sqlString.append(SQLStringOperations.getFormattedValueForSQL(row.getValue(j)));

				if (j != row.size() - 1)
				{
					sqlString.append(",");
				}
			}
			if (i != valuesAsJsonArray.size() - 1)
			{
				sqlString.append("),\n");
			}
			else
			{
				sqlString.append(")\n");
			}
		}
		return sqlString.toString();
	}	

	private static String getFormattedValueForSQL(Object value)
	{
		if(Optional.ofNullable(value).isPresent())
		{
			if(value.getClass() == String.class)
			{
				return ("'"+value+"'");
			}
			return value.toString();
		}
		return new String("null");

	}
}
