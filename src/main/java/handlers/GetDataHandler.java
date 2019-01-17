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
import utils.VertxJsonValidator;
/**
 * 
 * Handler to manage the routes:
 * - /delete/:schema/:name
 * 
 * A request will only be forwarded to this handler if its URL exactly matches this structure.
 * 
 * The idea behind this route is to provide the possibility for data deletion from a table.
 * Also, a request made to the route should include a JSON object specifying the select clause and where condition for the query to be made.
 * 
 * The only real requirement for the body is that it is valid JSON. Meaning that, if it is valid JSON but the expected values are missing, the query defaults to full table select.
 * This implementation path was chosen to facilitate usage in the case of "non-selective" objective. 
 * Meaning that, if you want to select the entire table, no fancy JSON will be needed, just put {} as message body.
 * 
 * @author pedrolourenco
 *
 */
public class GetDataHandler implements Handler<RoutingContext> {

	private JDBCClient jdbc;
	private Logger logger;

	/**
	 * 
	 * @param jdbc JDBC client to get connections from 
	 * @param logger Logger Instance for the class to work with.
	 */
	public GetDataHandler(JDBCClient jdbc, Logger logger) 
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
		logger.info("GetDataHandler - Handling Data Query Request");
		logger.debug("GetDataHandler - Request Body: " + context.getBodyAsString());

		jdbc.getConnection(connection -> {
			if (connection.succeeded())
			{


				Future<JsonObject> sqlQueryFuture = Future.future();
				queryTable(connection.result(),context.request().getParam("schema"), context.request().getParam("name"), context.getBodyAsString(), sqlQueryFuture);

				Future<Void> responseFuture = Future.future();

				sqlQueryFuture.compose(queryResults -> {
					handleQueryResults(context, queryResults);
				},responseFuture);
				responseFuture.complete();
			}
			else
			{
				logger.error("GetDataHandler - "+ Messages.DB_CONNECTION_ERROR.getValue() + connection.cause());
				sendBackResponse(context, new JsonObject(), StatusCodes.FAILED);
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
	private void sendBackResponse(RoutingContext context, JsonObject message, StatusCodes statusCode)
	{
		context.response()
		.putHeader("content-type", "application/json")
		.setStatusCode(statusCode.getValue())
		.end(message.encodePrettily());

	}

	/**
	 * 
	 * Method responsible for interacting with the database.
	 * There is no need to validate if the values of tableSchema and tableName are null because a request will only reach this handler if it matches the hard structure defined in the route.
	 * The request body does need to be validated because it can come empty or invalid from the caller. These two validations are done inside the buildSQL method.
	 * But also, in the "if" condition present in this method, because if the return from buildSQL is null, it means that something was wrong with the request body, and no statement will be submitted to the database.
	 * 
	 * If the statement sent to the DB executed successfully, creates a JsonObject with the results.
	 * If the statement sent to the DB failed its execution, creates a JsonObject with the cause for the failure.
	 * If no statement was sent to the DB due to invalid body request, creates a JsonObject with that note.
	 * 
	 * In the case of failure, the JsonObject contains the "error" key, which will then be used in handleQueryResults to set the correct status code for the response.
	 * 
	 * @param result SQLConnection to be used for interaction with the database.
	 * @param schemaName schema to to be used in the query.
	 * @param tableName table to be used in the query.
	 * @param requestBody Body from the request made to this route/handler, containing the delete conditions.
	 * @param sqlQueryfuture Future to store the results from this method. 
	 * 
	 */
	private void queryTable(SQLConnection result, String tableSchema, String tableName, String requestBody, Future<JsonObject> sqlQueryFuture) 
	{

		JsonArray queryResults = new JsonArray();
		SQLConnection conn = result;

		String sqlQuery = buildSQL(tableSchema, tableName, requestBody);

		if (Optional.ofNullable(sqlQuery).isPresent())
		{
			logger.info("GetDataHandler - Query passed to DB: \n" + sqlQuery);

			conn.query(sqlQuery, queryResult -> 
			{
				if(queryResult.succeeded())
				{
					logger.info("GetDataHandler - number of rows in query results: " +queryResult.result().getResults().size());

					for (JsonArray ja : queryResult.result().getResults())
					{		
						queryResults.add(ja);
					}

					sqlQueryFuture.handle(Future.succeededFuture(new JsonObject().put("results", queryResults)));
				}
				else
				{
					logger.error("GetDataHandler - " + Messages.QUERY_EXECUTION_ERROR.getValue() + queryResult.cause());
					sqlQueryFuture.handle(Future.succeededFuture(new JsonObject().put("error", Messages.QUERY_EXECUTION_ERROR.getValue() + queryResult.cause())));
				}

			});
			conn.close();
		}
		else
		{
			logger.error(Messages.INVALID_BODY_ERROR.getValue());
			sqlQueryFuture.handle(Future.succeededFuture(new JsonObject().put("error", Messages.INVALID_BODY_ERROR.getValue())));
		}
	}

	/**
	 * 
	 * Method responsible for validating the request body for both JSON validity and existence (both through the VertxJsonValidator.isValidJSON method), and creation of the SQL Statement to be passed to the database.
	 * If the body was present and valid JSON, but did not contain columns to select or where condition, the statement will feature default to the most general form (either "select *", no "where" clause, or both).
	 * If the body was present, valid JSON and did contain both conditions, the columns to select and where condition will be added to the statement.
	 * 
	 * @param tableSchema name of the schema for the table
	 * @param tableName name of the table where data will be deleted from
	 * @param requestBody body of the request
	 * @return String with the SQL Statement, or null if the request body was invalid.
	 */
	private String buildSQL(String tableSchema, String tableName, String requestBody)
	{
		String columnsToSelect;
		String whereCondition;

		if (VertxJsonValidator.isValidJSON(requestBody))
		{
			JsonObject bodyAsJson = new JsonObject(requestBody);
			columnsToSelect = Optional.ofNullable(bodyAsJson.getString("select")).orElse("*");
			whereCondition = Optional.ofNullable(bodyAsJson.getString("where")).orElse(";");

			if(columnsToSelect.isEmpty())
			{
				columnsToSelect = "*";
			}

			if(!whereCondition.isEmpty() && !whereCondition.contains(";"))
			{
				whereCondition="WHERE " + whereCondition+";";
			}

			return "SELECT " + columnsToSelect + "\n" + "FROM \""+ tableSchema.toLowerCase() +"\".\""+ tableName.toLowerCase() + "\" \n " + whereCondition;
		}
		else
		{
			logger.error("GetDataHandler - " + Messages.INVALID_BODY_ERROR.getValue());
			return null;
		}
	}
}
