package test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

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
public class InsertDataTests {

	private static Vertx vertx;
	private static JDBCClient jdbc;


	private final String validRequestBody1 ="{\n" + 
			"    \"columns\": \"column1,column2\",\n" + 
			"    \"values\": [\n" + 
			"        [\n" + 
			"            \"String1ForColumn1\",\n" + 
			"            true\n" + 
			"        ],\n" + 
			"        [\n" + 
			"            \"String2ForColumn1\",\n" + 
			"            null\n" + 
			"        ]\n" + 
			"    ]\n" + 
			"}";

	private final String validRequestBody2 ="{\n" + 
			"    \"columns\": \"column1,column2\",\n" + 
			"    \"values\": [\n" + 
			"        [\n" + 
			"            \"String3ForColumn1\",\n" + 
			"            true\n" + 
			"        ],\n" + 
			"        [\n" + 
			"            \"String4ForColumn1\",\n" + 
			"            null\n" + 
			"        ]\n" + 
			"    ]\n" + 
			"}";

	private final String validRequestBody3 ="{\n" + 
			"    \"columns\": \"column1,column2\",\n" + 
			"    \"values\": [\n" + 
			"        [\n" + 
			"            \"String5ForColumn1\",\n" + 
			"            true\n" + 
			"        ],\n" + 
			"        [\n" + 
			"            \"String6ForColumn1\",\n" + 
			"            null\n" + 
			"        ]\n" + 
			"    ]\n" + 
			"}";

	private final String requestBodyForGeneralSelect ="{}";

	private final String InvalidIncompleteRequestBody =" {\"values\": [\n" + 
			"        [\n" + 
			"            \"String1ForColumn1\",\n" + 
			"            true\n" + 
			"        ],\n" + 
			"        [\n" + 
			"            \"String2ForColumn1\",\n" + 
			"            null\n" + 
			"        ]\n" + 
			"    ]\n" + 
			"}";
	private final String InvalidEmptyRequestBody= "";

	private final static  String testDataGeneration = "CREATE TABLE public.insert_data_test \n" + 
			"(\n" + 
			"column1 varchar,\n" + 
			"column2 boolean,\n" + 
			"primary key (column1)\n" + 
			");\n";
	private final static  String footPrintElimination = "DROP TABLE public.insert_data_test;";

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
	public void InsertDataStatusCodeTest1(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/insert/public/insert_data_test")
		.as(BodyCodec.string())
		.sendJson(new JsonObject(InvalidIncompleteRequestBody),(resp -> {

			assertTrue(resp.result().statusCode() == 500);

			async.complete();
		}));
	}

	@Test
	public void InsertDataStatusCodeTest2(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/insert/public/insert_data_test")
		.as(BodyCodec.string())
		.sendJson(InvalidEmptyRequestBody,(resp -> {

			assertTrue(resp.result().statusCode() == 500);

			async.complete();
		}));
	}

	@Test
	public void InsertDataStatusCodeTest3(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/insert/public/insert_data_test")
		.as(BodyCodec.string())
		.sendJson(new JsonObject(validRequestBody2),(resp -> {

			assertTrue(resp.result().statusCode() == 200);

			async.complete();
		}));
	}

	@Test
	public void insertDataContentTest1(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/insert/public/insert_data_test")
		.as(BodyCodec.string())
		.sendJson(new JsonObject(validRequestBody1),(resp -> {
			String results = new JsonObject(resp.result().body()).getString("results");

			assertTrue(Optional.ofNullable(results).isPresent());

			async.complete();
		}));
	}

	@Test
	public void insertDataContentTest2(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/insert/public/insert_data_test")
		.as(BodyCodec.string())
		.sendJson(new JsonObject(InvalidIncompleteRequestBody),(resp -> {
			String error = new JsonObject(resp.result().body()).getString("error");
			System.out.println(new JsonObject(resp.result().body()).encodePrettily());
			assertTrue(Optional.ofNullable(error).isPresent());

			async.complete();
		}));
	}

	@Test
	public void insertDataComposedTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();

		webClient.post(80, "localhost", "/select/public/insert_data_test")
		.as(BodyCodec.string())
		.sendJson(new JsonObject(requestBodyForGeneralSelect),(resp -> {

			int prior = new JsonObject(resp.result().body()).getJsonArray("results").size();

			webClient.post(80, "localhost", "/insert/public/insert_data_test")
			.as(BodyCodec.string())
			.sendJson(new JsonObject(validRequestBody3), resp2 -> {

				webClient.post(80, "localhost", "/select/public/insert_data_test")
				.as(BodyCodec.string())
				.sendJson(new JsonObject(requestBodyForGeneralSelect),(resp3 -> {

					int after = new JsonObject(resp3.result().body()).getJsonArray("results").size();
					assertTrue( prior == (after - 2));
					async.complete();
				}));
			});
		}));
	}



}
