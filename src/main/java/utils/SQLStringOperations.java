package utils;

import java.util.Optional;

import io.vertx.core.json.JsonArray;
/**
 * 
 * Class designed to store the necessary String operations in order to make SQL compliant strings.
 * Operations such as: 
 * - Formatting a value accordingly to its type.
 * - Converting a JsonArray of values to a SQL array of values.
 * 
 * These operations are mainly used in the Insert Handler.
 * 
 * @author pedrolourenco
 *
 */
public class SQLStringOperations 
{

	/**
	 * This method receives a JsonArray containing JsonArrays and converts it into the form of values for insertion in a table.
	 * Basically, each of the inner arrays being a "row" for the table, it will be converted into: (valueInPos1, valueInPos2, valueInPos3, ...).
	 * Each of the rows will then be comma separated forming a perfect "values statement" content.
	 * 
	 * 
	 * @param valuesAsJsonArray JsonArray containing the rows of values to be converted into "(value1,value2,value3)" form. It is expected that this parameter is a JsonArray of JsonArrays, 
	 * each of the inner Arrays being a row of values.
	 * @return String of values formatted for insert statement.
	 */
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

	/**
	 * This method formats a value for usage in a sql statement, depending on it's type.
	 * It is fairly simple as if you are using a date or a string, it must be used as a String with quotes, which means that it will reach this method already represented as a String. 
	 * In which case, the method just adds the quotes to the string and returns the formatted value.
	 * If the value reaches this method in any type that is not a string, we rely on the toString() implementation for its type.
	 * 
	 * @param value value formatted for SQL inserting according to it's type.
	 * @return value formatted for SQL usage.
	 */
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
