package handlers;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import utils.StatusCodes;


/**
 * 
 * Basic Handler for the "/" route.
 * Simply reports on the fact that the application is capable of receiving request and responding to them.
 * 
 */
public class IndexHandler implements Handler<RoutingContext>
{

	/**
	 * Sends a response with status code of 200 and a JSON object reporting that the application is running.
	 */
	@Override
	public void handle(RoutingContext context) 
	{
		HttpServerResponse response = context.response();

		response
		.putHeader("content-type", "application/json")
		.setStatusCode(StatusCodes.SUCCEEDED.getValue())
		.end(new JsonObject()
				.put("live-check", "This Vert.x API is alive and well")
				.encodePrettily()
				);
	}

}
