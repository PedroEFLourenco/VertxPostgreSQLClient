package test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import application.PostgreSQLClientVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

@RunWith(VertxUnitRunner.class)
public class GetTablesDetailsTests 
{
	private static Vertx vertx;
	private static JDBCClient jdbc;

	private static final String testDataGeneration = "CREATE TABLE public.table_details_test (\n" + 
			"    column1 varchar,\n" + 
			"    column2 int,\n" + 
			" PRIMARY KEY (column1)\n" +
			");";

	private static final String footPrintElimination = "DROP TABLE public.table_details_test;";

	private static void loadTestData(Handler<AsyncResult<Void>> next, JDBCClient jdbc) 
	{
		jdbc.getConnection(connection -> {
			if (connection.succeeded())
			{
				connection.result().query(testDataGeneration, queryResult -> 
				{
					connection.result().close();
					next.handle(Future.succeededFuture());
				});
			}
		});
	}
	private static void deployVerticle(AsyncResult<Void> previousOperation, DeploymentOptions options)
	{
		if (previousOperation.succeeded())
		{
			vertx.deployVerticle(PostgreSQLClientVerticle.class.getName(), options);
		}
	}

	@BeforeClass
	public static void before(TestContext context) 
	{
		vertx = Vertx.vertx();
		DeploymentOptions options = new DeploymentOptions();

		byte[] encoded;
		JsonObject config;

		try {
			encoded = Files.readAllBytes(Paths.get("src/main/resources/config.json"));
			config = new JsonObject(new String(encoded, Charset.defaultCharset()));

			options.setConfig(config);
			jdbc = JDBCClient.createShared(vertx, config , "PostgreSQL");

			loadTestData((result) -> deployVerticle((result), options), jdbc);

			while (true)
			{
				if (vertx.deploymentIDs().size() > 0)
					break;
			}
		} catch 
		(IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	@AfterClass
	public static void after(TestContext context) 
	{
		jdbc.getConnection(connection -> {
			if (connection.succeeded())
			{

				connection.result().query(footPrintElimination, queryResult -> 
				{
					connection.result().close();
				});
			}
		});
		vertx.close(context.asyncAssertSuccess());
	}


	@Test
	public void getTableDetailsStatusCodeTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/tables/public/table_details_test")
		.as(BodyCodec.string())
		.send(resp -> {
			assertTrue(resp.result().statusCode() == 200);
			async.complete();
		});
	}

	@Test
	public void getTableDetailsFormatTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/tables/public/table_details_test")
		.as(BodyCodec.string())
		.send(resp -> {
			assertTrue(utils.VertxJsonValidator.isValidJSON(resp.result().body()));
			async.complete();
		});
	}

	@Test
	public void getTableDetailsContentExistenceTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/tables/public/table_details_test")
		.as(BodyCodec.string())
		.send(resp -> {
			JsonObject results = new JsonObject(resp.result().body());
			assertFalse(results.isEmpty());
			async.complete();
		});
	}

	@Test
	public void getTableDetailsContentTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/tables/public/table_details_test")
		.as(BodyCodec.string())
		.send(resp -> {
			JsonObject results = new JsonObject(resp.result().body()).getJsonObject("results");
			assertTrue(results.getString("tableSchema").equals("public") && results.getString("tableName").equals("table_details_test") && results.getBoolean("hasIndexes").equals(true));
			async.complete();
		});
	}

}
