package application;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import handlers.DeleteDataHandler;
import handlers.GetDataHandler;
import handlers.GetTableDetailsHandler;
import handlers.GetTableStructureHandler;
import handlers.GetTablesHandler;
import handlers.IndexHandler;
import handlers.InsertDataHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
/**
 * 
 * 
 * @author pedrolourenco
 *
 */
public class PostgreSQLClientVerticle extends AbstractVerticle
{
	private JDBCClient jdbc;

	public void start(Future<Void> fut)
	{
		
		Logger logger = LogManager.getLogger("Application");
		jdbc = JDBCClient.createShared(vertx, config(), "PostgreSQL");

		//Test the connection -> then Start the WebApp
		testConnection((connection) -> startWebApp((connection), logger, fut), logger,fut);

	}

	private void testConnection(Handler<AsyncResult<SQLConnection>> next,Logger logger, Future<Void> fut) {
		jdbc.getConnection(result -> 
		{
			if (result.failed()) 
			{
				fut.fail(result.cause());
				logger.error("Failed to get JDBC Connection: " + result.cause());
			} 
			else 
			{
				logger.info("PostgreSQL Connectivity -> [OK]");
				//Closing the connection that was just opened for connection test purpose
				result.result().close();

				next.handle(Future.succeededFuture(result.result()));
			}
		});
	}

	private void startWebApp(AsyncResult<SQLConnection> connection, Logger logger, Future<Void> fut)
	{

		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());
		
		router.route(HttpMethod.GET, "/").handler(new IndexHandler());
		router.routeWithRegex(HttpMethod.GET, "\\/tables\\/?(\\w*)?").handler(new GetTablesHandler(jdbc,logger));
		router.route(HttpMethod.GET, "/tables/:schema/:name").handler(new GetTableDetailsHandler(jdbc,logger));
		router.route(HttpMethod.GET, "/tables/:schema/:name/structure").handler(new GetTableStructureHandler(jdbc,logger));
		router.route(HttpMethod.POST, "/select/:schema/:name").handler(new GetDataHandler(jdbc,logger));
		router.route(HttpMethod.POST, "/insert/:schema/:name").handler(new InsertDataHandler(jdbc,logger));
		router.route(HttpMethod.POST, "/delete/:schema/:name").handler(new DeleteDataHandler(jdbc,logger));

		logger.info("Starting HTTP Server...");
		
		vertx.createHttpServer()
		.requestHandler(request -> router.handle(request))
		.listen(config().getInteger("http.port", 8080), result -> reportServerStartResult(result,logger,fut));
	}

	private void reportServerStartResult(AsyncResult<HttpServer> result, Logger logger, Future<Void> fut) 
	{
		if (result.succeeded()) 
		{
			logger.info("HTTP Server is Running!");
			fut.complete();
		} 
		else 
		{
			logger.error("HTTP initialization failed: " + result.cause());
			fut.fail(result.cause());
		}
	}
}
