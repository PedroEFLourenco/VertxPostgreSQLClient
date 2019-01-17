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
 * - /tables/schemaName
 * - /tables
 * 
 * If a schema is not provided, the response contains the list of all tables for all schemas.
 * If a schema is provided, the response contains the list of all tables on that schema.
 * 
 * @author pedrolourenco
 *
 */
public class GetTablesHandler implements Handler<RoutingContext> {

	private JDBCClient jdbc;
	private Logger logger;

	/**
	 * 
	 * @param jdbc JDBC client to get connections from 
	 * @param logger Logger Instance for the class to work with.
	 */
	public GetTablesHandler(JDBCClient jdbc, Logger logger) 
	{
		this.jdbc = jdbc;
		this.logger = logger;
	}

	/**
	 * 
	 * Central method to the management of requests to the routes this Handlers manages.
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
				logger.debug("GetTablesHandler - Schema being fetched -> " + context.request().getParam("param0"));
				queryTable(connection.result(),context.request().getParam("param0"),sqlQueryFuture);

				Future<Void> responseFuture = Future.future();

				sqlQueryFuture.compose(queryResults -> {
					handleQueryResults(context, queryResults);
				},responseFuture);
				responseFuture.complete();
			}
			else
			{
				logger.error("GetTablesHandler - " + Messages.DB_CONNECTION_ERROR.getValue() + connection.cause());
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
		context.response()
		.putHeader("content-type", "application/json")
		.setStatusCode(status.getValue())
		.end(valueFromSQLQuery.encodePrettily());
	}

	/**
	 * 
	 * Method responsible for interacting with the database.
	 * If the statement sent to the DB executed successfully, creates a JsonObject with the results.
	 * If the statement sent to the DB failed its execution, creates a JsonObject with the cause for the failure.
	 * 
	 * In the case of failure, the JsonObject contains the "error" key, which will then be used in handleQueryResults to set the correct status code for the response.
	 * 
	 * @param result SQLConnection to be used for interaction with the database.
	 * @param schemaName schema to to be used in the query, for filtering purposes.
	 * @param sqlQueryfuture Future to store the results from this method. 
	 */
	private void queryTable(SQLConnection result,String schemaName, Future<JsonObject> sqlQueryfuture) 
	{
		JsonArray tableList = new JsonArray();
		SQLConnection conn = result;

		String sqlQuery = buildSQL(schemaName);

		logger.debug(sqlQuery);

		conn.query(sqlQuery, queryResult -> 
		{
			if(queryResult.succeeded())
			{
				for (JsonArray ja : queryResult.result().getResults())
				{
					JsonObject tableInfo = new JsonObject();
					tableInfo.put("schema", ja.getString(0));
					tableInfo.put("name", ja.getString(1));
					tableList.add(tableInfo);
				}
				logger.error("GetTablesHandler -  " + Messages.QUERY_EXECUTION_SUCCESS.getValue());
				sqlQueryfuture.handle(Future.succeededFuture(new JsonObject().put("results",tableList)));
			}
			else
			{
				logger.error("GetTablesHandler -  " + Messages.QUERY_EXECUTION_ERROR.getValue() + queryResult.cause());
				sqlQueryfuture.handle(Future.succeededFuture(new JsonObject().put("error",Messages.QUERY_EXECUTION_ERROR.getValue())));
			}
		});
		conn.close();
	}

	
	/**
	 * 
	 * This method creates the SQL Statement to be sent to the database for execution.
	 * It receives the schemaName parameter that will affect the final form of the statement:
	 * If a schema name is present a new condition is added to the where clause to guarantee that only table from that schema are retrieved, otherwise, the clause is closed.
	 * 
	 * @param schemaName name of the schema to be used for filtering
	 * @return String Object with the SQL Statement ready to be passed to the DB for execution
	 */
	private String buildSQL(String schemaName)
	{
		String baseStatement = 	"SELECT * FROM pg_catalog.pg_tables\n" + 
				"			WHERE\n" + 
				"			schemaname != 'pg_catalog'\n" + 
				"			AND schemaname != 'information_schema'\n"; 

		if (!schemaName.isEmpty())
		{
			baseStatement = baseStatement.concat("AND LOWER(schemaname) = '" + schemaName.toLowerCase() +"';");
		}
		else
		{
			baseStatement = baseStatement.concat(";");
		}

		return baseStatement;
	}
}
