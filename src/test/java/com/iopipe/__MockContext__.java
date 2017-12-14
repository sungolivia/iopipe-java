package com.iopipe;

import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

/**
 * This provides a fake context for testing which provides a basic information
 * set for execution.
 *
 * @since 2017/12/13
 */
final class __MockContext__
	implements Context
{
	/** The name of the function being executed. */
	protected final String functionname;
	
	/**
	 * Initializes the context with the given parameters.
	 *
	 * @param __funcname The name of the function being invoked.
	 * @throws NullPointerException On null arguments.
	 * @since 2017/12/13
	 */
	__MockContext__(String __funcname)
		throws NullPointerException
	{
		if (__funcname == null)
			throw new NullPointerException();
		
		this.functionname = __funcname;
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final String getAwsRequestId()
	{
		throw new Error("TODO");
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final ClientContext getClientContext()
	{
		// This is only valid if the context is called from the mobile SDK
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final String getFunctionName()
	{
		return this.functionname;
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final String getFunctionVersion()
	{
		return "1.0";
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final CognitoIdentity getIdentity()
	{
		// This is only valid if the context is called from the mobile SDK
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final String getInvokedFunctionArn()
	{
		throw new Error("TODO");
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final LambdaLogger getLogger()
	{
		throw new Error("TODO");
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final String getLogGroupName()
	{
		throw new Error("TODO");
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final String getLogStreamName()
	{
		throw new Error("TODO");
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final int getMemoryLimitInMB()
	{
		throw new Error("TODO");
	}
	
	/**
	 * {@inheritDoc}
	 * @since 2017/12/13
	 */
	@Override
	public final int getRemainingTimeInMillis()
	{
		throw new Error("TODO");
	}
}

