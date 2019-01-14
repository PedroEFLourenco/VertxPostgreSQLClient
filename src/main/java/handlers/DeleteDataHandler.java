package handlers;

import java.util.Optional;

import org.apache.logging.log4j.Logger;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;
import utils.Messages;
import utils.StatusCodes;
import utils.VertxJsonValidator;

public class DeleteDataHandler implements Handler<RoutingContext> {

	private JDBCClient jdbc;
	private Logger logger;

	public DeleteDataHandler(JDBCClient jdbc, Logger logger) 
	{
		this.jdbc = jdbc;
		this.logger = logger;
	}

	@Override
	public void handle(RoutingContext context) 
	{
		logger.info("DeleteDataHandler - Handling Data Query Request");
		logger.debug("DeleteDataHandler - Request Body: " + context.getBodyAsString());

		jdbc.getConnection(connection -> {
			if (connection.succeeded())
			{


				Future<JsonObject> sqlQueryFuture = Future.future();
				deleteData(connection.result(),context.request().getParam("schema"), context.request().getParam("name"), context.getBodyAsString(), sqlQueryFuture);

				Future<Void> responseFuture = Future.future();

				sqlQueryFuture.compose(queryResults -> {
					handleQueryResults(context, queryResults);
				},responseFuture);
				responseFuture.complete();
			}
			else
			{
				logger.error("DeleteDataHandler - "+ Messages.DB_CONNECTION_ERROR.getValue() + connection.cause());
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

	private void deleteData(SQLConnection result, String tableSchema, String tableName, String requestBody, Future<JsonObject> sqlQueryFuture) 
	{
		SQLConnection conn = result;

		String sqlQuery = buildSQL(tableSchema, tableName, requestBody);

		if (statementIsValid(sqlQuery))
		{
			logger.info("DeleteDataHandler - Query passed to DB: \n" + sqlQuery);

			conn.query(sqlQuery, queryResult -> 
			{
				if(queryResult.succeeded())
				{
					logger.info("InsertDataHandler - " + Messages.QUERY_EXECUTION_SUCCESS.getValue());
					sqlQueryFuture.handle(Future.succeededFuture(new JsonObject().put("results", Messages.QUERY_EXECUTION_SUCCESS.getValue())));
				}
				else
				{
					logger.error("DeleteDataHandler - " + Messages.QUERY_EXECUTION_ERROR.getValue() + queryResult.cause());
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
		String whereCondition;

		if (VertxJsonValidator.isValidJSON(requestBody))
		{
			JsonObject bodyAsJson = new JsonObject(requestBody);
			whereCondition = Optional.ofNullable(bodyAsJson.getString("where")).orElse(";");

			if(!whereCondition.isEmpty() && !whereCondition.contains(";"))
			{
				whereCondition="WHERE " + whereCondition+";";
			}

			return "DELETE FROM \""+ tableSchema.toLowerCase() +"\".\""+ tableName.toLowerCase() + "\" \n " + whereCondition;
		}
		else
		{
			logger.error("DeleteDataHandler - " + Messages.INVALID_BODY_ERROR.getValue());
			return null;
		}
	}
}
