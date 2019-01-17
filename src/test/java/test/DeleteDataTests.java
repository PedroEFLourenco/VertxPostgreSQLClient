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
public class DeleteDataTests 
{
	private static Vertx vertx;
	private static JDBCClient jdbc;

	private static final String testDataGeneration = "CREATE TABLE public.delete_data_test \n" + 
			"(\n" + 
			"column1 varchar,\n" + 
			"column2 date,\n" + 
			"primary key (column1)\n" + 
			");\n" + 
			"\n" + 
			"insert into public.delete_data_test (column1,column2)\n" + 
			"values\n" + 
			"('value1','01/02/03'),\n" + 
			"('value2','02/02/03'),\n" + 
			"('value3','02/02/03'),\n" + 
			"('value4','02/02/03'),\n" + 
			"('value5','03/02/03');";

	private static final String footPrintElimination = "DROP TABLE public.delete_data_test;";

	private final String validRequestBody1="{\n" + 
			"	\"where\": \"column1 = 'value1'\"\n" + 
			"}";

	private final String validRequestBody2 ="{\n" + 
			"	\"where\": \"column1 = 'value2' or column1 = 'value3'\"\n" + 
			"}\n";

	private final String validRequestBody3="{\n" + 
			"	\"where\": \"\"\n" + 
			"}";

	private final String requestBodyForGeneralSelect ="{}";

	private final String InvalidIncompleteRequestBody ="{}";
	private final String InvalidEmptyRequestBody= "";


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
				connection.result().query(footPrintElimination, queryResult -> {});
				connection.result().close();
			}
		});
		vertx.close(context.asyncAssertSuccess());
	}

	@Test
	public void deleteDataStatusCodeTest1(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/delete/public/delete_data_test")
		.as(BodyCodec.string())
		.sendJson(new JsonObject(InvalidIncompleteRequestBody),(resp -> {

			assertTrue(resp.result().statusCode() == 500);

			async.complete();
		}));
	}

	@Test
	public void deleteDataStatusCodeTest2(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/delete/public/delete_data_test")
		.as(BodyCodec.string())
		.sendJson(InvalidEmptyRequestBody,(resp -> {

			assertTrue(resp.result().statusCode() == 500);

			async.complete();
		}));
	}

	@Test
	public void deleteDataStatusCodeTest3(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/delete/public/delete_data_test")
		.as(BodyCodec.string())
		.sendJson(new JsonObject(validRequestBody1),(resp -> {

			assertTrue(resp.result().statusCode() == 200);

			async.complete();
		}));
	}

	@Test
	public void deleteDataContentTest1(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/delete/public/delete_data_test")
		.as(BodyCodec.string())
		.sendJson(new JsonObject(validRequestBody2),(resp -> {
			String results = new JsonObject(resp.result().body()).getString("results");

			assertTrue(Optional.ofNullable(results).isPresent());

			async.complete();
		}));
	}

	@Test
	public void deleteDataContentTest2(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/delete/public/delete_data_test")
		.as(BodyCodec.string())
		.sendJson(new JsonObject(InvalidIncompleteRequestBody),(resp -> {
			String results = new JsonObject(resp.result().body()).getString("error");

			assertTrue(Optional.ofNullable(results).isPresent());

			async.complete();
		}));
	}

	@Test
	public void deleteDataComposedTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.post(80, "localhost", "/select/public/delete_data_test")
		.as(BodyCodec.string())
		.sendJson(new JsonObject(requestBodyForGeneralSelect),(resp -> {

			int prior = new JsonObject(resp.result().body()).getJsonArray("results").size();

			webClient.post(80, "localhost", "/delete/public/delete_data_test")
			.as(BodyCodec.string())
			.sendJson(new JsonObject(validRequestBody3), resp2 -> {

				webClient.post(80, "localhost", "/select/public/delete_data_test")
				.as(BodyCodec.string())
				.sendJson(new JsonObject(requestBodyForGeneralSelect),(resp3 -> {

					int after = new JsonObject(resp3.result().body()).getJsonArray("results").size();
					System.out.println(prior);
					System.out.println(after);
					assertTrue( prior == (after + prior));
					async.complete();
				}));
			});
		}));
	}




}
