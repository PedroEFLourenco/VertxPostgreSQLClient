package handlers;

import java.util.Optional;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;
import utils.Messages;
import utils.StatusCodes;
import utils.VertxJsonValidator;

public class GetDataHandler implements Handler<RoutingContext> {

	private JDBCClient jdbc;
	private Logger logger;

	public GetDataHandler(JDBCClient jdbc, Logger logger) 
	{
		this.jdbc = jdbc;
		this.logger = logger;
	}

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

	private void sendBackResponse(RoutingContext context, JsonObject message, StatusCodes statusCode)
	{
		context.response()
		.putHeader("content-type", "application/json")
		.setStatusCode(statusCode.getValue())
		.end(message.encodePrettily());

	}

	private void queryTable(SQLConnection result, String tableSchema, String tableName, String requestBody, Future<JsonObject> sqlQueryFuture) 
	{

		JsonArray queryResults = new JsonArray();
		SQLConnection conn = result;

		String sqlQuery = buildSQL(tableSchema, tableName, requestBody);

		if (statementIsValid(sqlQuery))
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

	private boolean statementIsValid(String sqlQuery)
	{
		if (!Optional.ofNullable(sqlQuery).isPresent())
		{
			return false;
		}
		return true;
	}

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
