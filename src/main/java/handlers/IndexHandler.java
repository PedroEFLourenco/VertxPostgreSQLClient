package handlers;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class IndexHandler implements Handler<RoutingContext>
{

	@Override
	public void handle(RoutingContext context) 
	{
		HttpServerResponse response = context.response();

		response
		.putHeader("content-type", "application/json")
		.end(new JsonObject()
				.put("live-check", "This Vert.x API is alive and well")
				.encodePrettily()
				);
	}

}
