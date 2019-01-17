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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

@RunWith(VertxUnitRunner.class)
public class GetTableStructureTests 
{
	private static Vertx vertx;
	private static JDBCClient jdbc;

	private static final String testDataGeneration = "CREATE TABLE public.table_structure_test (\n" + 
			"    column1 varchar,\n" + 
			"    column2 date,\n" + 
			" PRIMARY KEY (column1)\n" +
			");";
	
	private static final String footPrintElimination = "DROP TABLE public.table_structure_test;";


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
	public void getTableStructureStatusCodeTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/tables/public/table_structure_test/structure")
		.as(BodyCodec.string())
		.send(resp -> {

			assertTrue(resp.result().statusCode() == 200);

			async.complete();
		});
	}

	@Test
	public void getTableStructureFormatTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/tables/public/table_structure_test/structure")
		.as(BodyCodec.string())
		.send(resp -> {

			assertTrue(utils.VertxJsonValidator.isValidJSON(resp.result().body()));

			async.complete();
		});
	}

	@Test
	public void getTableStructureContentExistenceTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/tables/public/table_structure_test/structure")
		.as(BodyCodec.string())
		.send(resp -> {
			JsonObject results = new JsonObject(resp.result().body());

			assertFalse(results.isEmpty());

			async.complete();
		});
	}

	@Test
	public void getTableStructureContentTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/tables/public/table_structure_test/structure")
		.as(BodyCodec.string())
		.send(resp -> {
			JsonArray results = new JsonObject(resp.result().body()).getJsonArray("results");
			JsonObject column1 = results.getJsonObject(0);
			JsonObject column2 = results.getJsonObject(1);
			System.out.println(results.encodePrettily());

			assertTrue(column1.getString("columnName").equals("column1") && column1.getInteger("ordinalPosition").equals(1) && column1.getString("isNullable").equals("NO") && column1.getString("dataType").equals("character varying"));
			assertTrue(column2.getString("columnName").equals("column2") && column2.getInteger("ordinalPosition").equals(2) && column2.getString("isNullable").equals("YES") && column2.getString("dataType").equals("date"));

			async.complete();
		});
	}
}
