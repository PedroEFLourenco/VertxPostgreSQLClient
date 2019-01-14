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
import utils.VertxJsonValidator;
import utils.Messages;
import utils.StatusCodes;
import utils.SQLStringOperations;

public class InsertDataHandler implements Handler<RoutingContext> {


	private JDBCClient jdbc;
	private Logger logger;

	public InsertDataHandler(JDBCClient jdbc, Logger logger) 
	{
		this.jdbc = jdbc;
		this.logger = logger;
	}

	@Override
	public void handle(RoutingContext context) 
	{
		logger.info("InsertDataHandler - Handling Data Insert Request");
		logger.debug("InsertDataHandler - Request Body: " + context.getBodyAsString());

		jdbc.getConnection(connection -> {
			if (connection.succeeded())
			{
				Future<JsonObject> sqlQueryFuture = Future.future();
				insertIntoTable(connection.result(),context.request().getParam("schema"), context.request().getParam("name"), context.getBodyAsString(), sqlQueryFuture);

				Future<Void> responseFuture = Future.future();

				sqlQueryFuture.compose(queryResults -> {
					handleStatementResults(context, queryResults);
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

	private void handleStatementResults(RoutingContext context, JsonObject queryResults) 
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


	private void insertIntoTable(SQLConnection result, String schemaName, String tableName,String requestBody, Future<JsonObject> sqlQueryFuture) 
	{
		SQLConnection conn = result;

		String sqlStatement = buildSQL(schemaName, tableName, requestBody);
		if (statementIsValid(sqlStatement))
		{
			logger.info("InsertDataHandler - SQL Insert Statement: \n" + sqlStatement);
			conn.query(sqlStatement, queryResult -> 
			{
				if(queryResult.succeeded())
				{
					logger.info("InsertDataHandler - " + Messages.QUERY_EXECUTION_SUCCESS.getValue());
					sqlQueryFuture.handle(Future.succeededFuture(new JsonObject().put("results", Messages.QUERY_EXECUTION_SUCCESS.getValue())));
				}
				else
				{
					logger.error("InsertDataHandler - " + Messages.QUERY_EXECUTION_ERROR.getValue() + queryResult.cause());
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

	private boolean statementIsValid(String insertStatement)
	{
		if (!Optional.ofNullable(insertStatement).isPresent())
		{
			return false;
		}
		return true;
	}

	private String buildSQL(String tableSchema, String tableName, String requestBody)
	{
		String columnsToInsert;
		String insertValues;

		if (requestBody.length() != 0 && VertxJsonValidator.isValidJSON(requestBody))
		{
			JsonObject bodyAsJson = new JsonObject(requestBody);
			columnsToInsert = Optional.ofNullable(bodyAsJson.getString("columns")).orElse("");
			insertValues = SQLStringOperations.valuesToSQLString(
					Optional.ofNullable(bodyAsJson.getJsonArray("values"))
					.orElse(new JsonArray()));

			if(columnsToInsert.isEmpty() || insertValues.isEmpty())
			{
				logger.error("InsertDataHandler - " + Messages.INVALID_BODY_ERROR.getValue());
				return null;
			}

			return "INSERT INTO " + tableSchema + "." + tableName + " (" + columnsToInsert + ") VALUES " +  insertValues + ";";
		}
		else
		{
			logger.info("InsertDataHandler - " + Messages.INVALID_BODY_ERROR.getValue());
			return null;
		}
	}
}
