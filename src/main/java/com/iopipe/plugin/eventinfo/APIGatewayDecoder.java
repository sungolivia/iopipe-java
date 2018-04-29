package com.iopipe.plugin.eventinfo;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

/**
 * This class implements the decoder for API Gateway events.
 *
 * @since 2018/04/22
 */
public final class APIGatewayDecoder
	implements EventInfoDecoder
{
	/**
	 * {@inheritDoc}
	 * @since 2018/04/22
	 */
	@Override
	public final Class<?> decodes()
	{
		return APIGatewayProxyRequestEvent.class;
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2018/04/23
	 */
	@Override
	public final String eventType()
	{
		return "apiGateway";
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2018/04/29
	 */
	@Override
	public final Rule[] rules()
	{
		throw new Error("TODO");
	}
}

