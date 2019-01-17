package test;

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
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

@RunWith(VertxUnitRunner.class)
public class CheckAliveTest 
{
	private static Vertx vertx;

	@BeforeClass
	public static void before(TestContext context)
	{
		vertx = Vertx.vertx();

		DeploymentOptions options = new DeploymentOptions();

		byte[] encoded;
		try {
			encoded = Files.readAllBytes(Paths.get("src/main/resources/config.json"));
			options.setConfig(new JsonObject(new String(encoded, Charset.defaultCharset())));
		} catch 
		(IOException e) 
		{
			e.printStackTrace();
		}

		vertx.deployVerticle(PostgreSQLClientVerticle.class.getName(), options, context.asyncAssertSuccess());
	}

	@AfterClass
	public static void after(TestContext context) 
	{
		vertx.close(context.asyncAssertSuccess());
	}



	@Test
	public void checkAliveStatusCodeTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/")
		.as(BodyCodec.string())
		.send(resp -> {
			assertTrue(resp.result().statusCode() == 200);
			async.complete();
		});
	}

	@Test
	public void checkAliveContentTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/")
		.as(BodyCodec.string())
		.send(resp -> {
			assertTrue(new JsonObject(resp.result().body()).containsKey("live-check"));
			async.complete();
		});
	}

	@Test
	public void checkAliveFormatTest(TestContext testContext) 
	{
		WebClient webClient = WebClient.create(vertx);
		final Async async = testContext.async();
		webClient.get(80, "localhost", "/")
		.as(BodyCodec.string())
		.send(resp -> {
			assertTrue(utils.VertxJsonValidator.isValidJSON(resp.result().body()));
			async.complete();
		});
	}
}
