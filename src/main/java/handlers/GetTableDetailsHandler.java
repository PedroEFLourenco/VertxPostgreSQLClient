package handlers;

import java.util.Optional;

import org.apache.logging.log4j.Logger;

import enums.Messages;
import enums.StatusCodes;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;


/**
 * 
 * Handler to manage the routes:
 * - /tables/schema/name
 * 
 * A request will only be forwarded to this handler if its URL exactly matches this structure.
 * 
 * The idea behind this route is to provide some superficial details about a table namely:
 * 
 *  - Schema name
 *  - Table name 
 *  - Table owner
 *  - Tablespace
 *  - If it has indexes
 *  - If it has rules
 *  - If it has triggers
 * 
 * @author pedrolourenco
 *
 */
public class GetTableDetailsHandler implements Handler<RoutingContext> {

	private JDBCClient jdbc;
	private Logger logger;

	/**
	 * 
	 * @param jdbc JDBC client to get connections from 
	 * @param logger Logger Instance for the class to work with.
	 */
	public GetTableDetailsHandler(JDBCClient jdbc, Logger logger) 
	{
		this.jdbc = jdbc;
		this.logger = logger;
	}

	/**
	 * 
	 * Central method to the management of requests made to the route this Handlers manages.
	 * The general logic is:
	 * 1 - Query database for the information requested (passing the eventually present parameter) (queryTable)
	 * 2 - Handle the results from said query and populate the response accordingly (handleQueryResults)
	 * 
	 * Sequential execution of the steps is guaranteed via the usage of Futures.
	 * The overall logic applied is that the futures never fail because, even if there is some issue in querying the database.
	 * This was the design of choice because that would be an issue external to the application. Meaning, that the application's job is to bridge the caller and the database, even if the results are not the desired.
	 * That being said, the response sent back to the caller will reflect if the operation went as expected or not.
	 * 
	 * The response body will always contain:
	 * 1- In case of success:
	 *    JsonObject, as body, with the desired results and a Status Code of 200.
	 * 2- In case of failure:
	 *    JsonObject, as body, with the a hint for the reason behind the failure and a Status Code of 500.
	 *    
	 */
	@Override
	public void handle(RoutingContext context) 
	{
		jdbc.getConnection(connection -> {
			if (connection.succeeded())
			{
				Future<JsonObject> sqlQueryFuture = Future.future();
				getTableInfo(connection.result(),context.request().getParam("schema"), context.request().getParam("name"), sqlQueryFuture);

				Future<Void> responseFuture = Future.future();

				sqlQueryFuture.compose(queryResults -> {
					handleQueryResults(context, queryResults);
				},responseFuture);
				responseFuture.complete();
			}
			else
			{
				logger.error("GetTablesDetailsHandler - " + Messages.DB_CONNECTION_ERROR.getValue() + connection.cause());
				sendBackResponse(context, new JsonObject().put("error", Messages.DB_CONNECTION_ERROR.getValue()),StatusCodes.FAILED);
			}
		});
	}

	/**
	 * 
	 * This method validates the results from the query passed to the database and orders the response to be sent with a status code matching the success or failure of the procedure.
	 * 
	 * @param context - Context from the request 
	 * @param queryResults - Results from the query passed to the database
	 */
	private void handleQueryResults(RoutingContext context, JsonObject queryResults) 
	{
		if(Optional.ofNullable(queryResults.getValue("error")).isPresent()) 
		{
			sendBackResponse(context, queryResults, StatusCodes.FAILED);
		}
		else
		{
			sendBackResponse(context, queryResults, StatusCodes.SUCCEEDED);
		}
	}

	/**
	 * 
	 * This method sends the response back to the entity that made the request to this application.
	 * It build the response with the correct header for a JSON response, status code and results printed prettily.
	 * 
	 * @param context Context from the request 
	 * @param valueFromSQLQuery  Results from the query passed to the database
	 * @param status Status code to be included in the response
	 */
	private void sendBackResponse(RoutingContext context, JsonObject valueFromSQLQuery, StatusCodes status) 
	{
		String listOfTables = valueFromSQLQuery.encodePrettily();
		context.response()
		.putHeader("content-type", "application/json")
		.setStatusCode(status.getValue())
		.end(listOfTables);
	}

	/**
	 * 
	 * Method responsible for interacting with the database.
	 * There is no need to validate if the values of tableSchema and tableName are null because a request will only reach this handler if it matches the hard structure defined in the route.
	 * The construction of the SQL Statement was included in this method because there is no specific logic associated with it, it is simply a matter of included the provided parameters.
	 * 
	 * If the statement sent to the DB executed successfully, creates a JsonObject with the results.
	 * If the statement sent to the DB failed its execution, creates a JsonObject with the cause for the failure.
	 * 
	 * In the case of failure, the JsonObject contains the "error" key, which will then be used in handleQueryResults to set the correct status code for the response.
	 * 
	 * @param result SQLConnection to be used for interaction with the database.
	 * @param schemaName schema to to be used in the query, for filtering purposes.
	 * @param tableName table to be used in the query, for filtering purposes.
	 * @param sqlQueryfuture Future to store the results from this method. 
	 */
	private void getTableInfo(SQLConnection result, String tableSchema, String tableName, Future<JsonObject> sqlQueryFuture) 
	{
		JsonObject tableDetails = new JsonObject();
		SQLConnection conn = result;

		String sqlQuery = "SELECT * FROM pg_catalog.pg_tables\n" + 
				"			WHERE\n" + 
				"			LOWER(schemaname) = '" + tableSchema.toLowerCase() + "'\n" + 
				"			AND LOWER(tablename) = '" + tableName.toLowerCase() + "';";

		conn.query(sqlQuery, queryResult -> 
		{
			if(queryResult.succeeded())
			{
				for (JsonArray ja : queryResult.result().getResults())
				{
					tableDetails.put("tableSchema",ja.getString(0));
					tableDetails.put("tableName",ja.getString(1));
					tableDetails.put("tableOwner",ja.getString(2));
					tableDetails.put("tableSpace",Optional.ofNullable(ja.getString(3)).orElse("null"));
					tableDetails.put("hasIndexes",ja.getBoolean(4));
					tableDetails.put("hasRules",ja.getBoolean(5));
					tableDetails.put("hasTriggers",ja.getBoolean(6));
				}
				logger.info("GetTableDetailsHandler -  " + Messages.QUERY_EXECUTION_SUCCESS.getValue());
				sqlQueryFuture.handle(Future.succeededFuture(new JsonObject().put("results",tableDetails)));
			}
			else
			{
				logger.error("GetTableDetailsHandler -  " + Messages.QUERY_EXECUTION_ERROR.getValue() + queryResult.cause());
				sqlQueryFuture.handle(Future.succeededFuture(new JsonObject().put("error",Messages.QUERY_EXECUTION_ERROR.getValue())));
			}
		});
		conn.close();
	}

}
