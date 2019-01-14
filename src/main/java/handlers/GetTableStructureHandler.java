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

public class GetTableStructureHandler implements Handler<RoutingContext> {


	private JDBCClient jdbc;
	private Logger logger;

	public GetTableStructureHandler(JDBCClient jdbc, Logger logger) 
	{
		this.jdbc = jdbc;
		this.logger = logger;
	}

	@Override
	public void handle(RoutingContext context) 
	{
		logger.info("GetTableStructureHandler - Handling table structure request.");

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
		JsonArray tableStructure = new JsonArray();

		SQLConnection conn = result;

		String sqlQuery = "SELECT	t.column_name,\n" +
				"					t.ordinal_position,\n" +
				"					t.is_nullable,\n" +
				"					t.data_type,\n"+
				"					t.character_maximum_length,\n"+
				"					(case when t.column_name = kcu.column_name then true \n"+
				"					else false end) as isPk \n"+
				"			FROM    INFORMATION_SCHEMA.columns t \n"+
				"			LEFT JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc \n"+
				"					ON tc.table_catalog = t.table_catalog \n" +
				"					AND tc.table_schema = t.table_schema \n"+
				"					AND tc.table_name = t.table_name \n"+
				"					AND tc.constraint_type = 'PRIMARY KEY' \n"+
				"			LEFT JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu \n"+
				"					ON kcu.table_catalog = tc.table_catalog \n"+
				"					AND kcu.table_schema = tc.table_schema \n"+
				"					AND kcu.table_name = tc.table_name \n" +
				"					AND kcu.constraint_name = tc.constraint_name \n "+
				"			WHERE   LOWER(t.table_schema) = '" + tableSchema.toLowerCase() +"' and LOWER(t.table_name) = '" + tableName.toLowerCase() + "' \n "+
				"			ORDER BY t.table_catalog, \n "+
				"					 t.table_schema, \n "+
				"					 t.table_name, \n "+
				"					 t.ordinal_position;";


		conn.query(sqlQuery, queryResult -> 
		{
			if(queryResult.succeeded())
			{
				logger.info("Size of query result: " +queryResult.result().getResults().size());

				for (JsonArray ja : queryResult.result().getResults())
				{		
					JsonObject columnDetails = new JsonObject();
					columnDetails.put("columnName",ja.getString(0));
					columnDetails.put("ordinalPosition",ja.getInteger(1));
					columnDetails.put("isNullable",ja.getString(2));
					columnDetails.put("dataType",ja.getString(3));
					columnDetails.put("fieldLength",ja.getInteger(4));
					columnDetails.put("isPK",ja.getBoolean(5));
					tableStructure.add(columnDetails);
				}
				logger.error("GetTableDetailsHandler -  " + Messages.QUERY_EXECUTION_SUCCESS.getValue());
				sqlQueryFuture.handle(Future.succeededFuture(new JsonObject().put("results",tableStructure)));
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
