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

public class GetTableDetailsHandler implements Handler<RoutingContext> {

	private JDBCClient jdbc;
	private Logger logger;

	public GetTableDetailsHandler(JDBCClient jdbc, Logger logger) 
	{
		this.jdbc = jdbc;
		this.logger = logger;
	}

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

	private void sendBackResponse(RoutingContext context, JsonObject valueFromSQLQuery, StatusCodes status) 
	{
		String listOfTables = valueFromSQLQuery.encodePrettily();
		context.response()
		.putHeader("content-type", "application/json")
		.setStatusCode(status.getValue())
		.end(listOfTables);
	}

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
				logger.error("GetTableDetailsHandler -  " + Messages.QUERY_EXECUTION_SUCCESS.getValue());
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
