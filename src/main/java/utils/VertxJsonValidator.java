package utils;


import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class VertxJsonValidator
{

	public static boolean isValidJSON(String test) 
	{
		try 
		{
			new JsonObject(test);
		} 
		catch (DecodeException ex) 
		{
			try 
			{
				new JsonArray(test);
			} 
			catch (DecodeException ex1) 
			{
				return false;
			}
		}
		return true;
	}

}
