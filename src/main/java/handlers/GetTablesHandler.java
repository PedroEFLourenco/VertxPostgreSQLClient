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

public class GetTablesHandler implements Handler<RoutingContext> {

	private JDBCClient jdbc;
	private Logger logger;

	public GetTablesHandler(JDBCClient jdbc, Logger logger) 
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
