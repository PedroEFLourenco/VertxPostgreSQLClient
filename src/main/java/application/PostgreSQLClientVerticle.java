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
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
/**
 * 
 * Entry Verticle for the application.
 * Creates a JDBC client, configures the application and starts the WebServer.
 * 
 * 
 * 
 * @author pedrolourenco
 *
 */
public class PostgreSQLClientVerticle extends AbstractVerticle
{
	private JDBCClient jdbc;

	/**
	 * 
	 * Central method responsible for starting the application. 
	 * General logic is:
	 * 1- Tries to connect to the DB
	 * 2- If successful, Starts the Web Application
	 * Finally - Reports on the log that the application is running
	 * 
	 * 
	 * Configurations come by default from the file specified by the -conf parameter from the launch command.
	 * 
	 */
	public void start(Future<Void> fut)
	{

		Logger logger = LogManager.getLogger("Application");
		jdbc = JDBCClient.createShared(vertx, config(), "PostgreSQL");

		//Test the connection -> then Start the WebApp
		testConnection((result) -> startWebApp((result), logger, fut), logger,fut);

	}

	/**
	 * 
	 * Method to test the connection to the database.
	 * If connectivity does not exist, future is failed, otherwise calls .handle on the handler parameter with a succeeded future.
	 * 
	 * @param next Handler for the result from this method.
	 * @param logger Logger instance to be used by the method.
	 * @param fut Future instance for this method to work with.
	 * 
	 *
	 */
	private void testConnection(Handler<AsyncResult<Void>> next,Logger logger, Future<Void> fut) {
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

				next.handle(Future.succeededFuture());
			}
		});
	}
	/**
	 * 
	 * Method responsible for:
	 * 1- Configuring all supported routes and providing handlers for each one.
	 * 2- Starting the HTTP Server that hosts the application with the configurations.
	 * 3- Reporting on the success or failure of the startup
	 * 
	 * Any request not matching the routes here defined will receive a "resource not found" response by default.
	 * 
	 * 
	 * @param previous Result from the previous method - This parameter exists only because of the scope in which this method was designed to live: In sequential execution and dependency from the previous step on the startup order.
	 * @param logger Logger instance to be used by the method
	 * @param fut Future instance for this method to work with.
	 */
	private void startWebApp(AsyncResult<Void> previous, Logger logger, Future<Void> fut)
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

	/**
	 * 
	 * This method simply logs if the startup was successful or not. 
	 * That information comes from the status on the result parameter.
	 * 
	 * @param result Result from the previous method - This parameter exists only because of the scope in which this method was designed to live: In sequential execution and dependency from the previous step on the startup order.
	 * @param logger Logger instance to be used by the method.
	 * @param fut Future instance for this method to work with.
	 */
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
