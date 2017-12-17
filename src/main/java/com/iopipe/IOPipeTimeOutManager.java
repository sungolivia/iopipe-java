package com.iopipe;

import com.amazonaws.services.lambda.runtime.Context;
import com.iopipe.http.RemoteConnection;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This class is used to ensure that timeouts during execution are properly
 * handled. This class keeps track of its own tghread
 *
 * @since 2017/12/14
 */
public final class IOPipeTimeOutManager
{
	/** The connection to the server. */
	protected final RemoteConnection connection;
	
	/** Current contexts which are being tracked. */
	private final Map<IOPipeContext, Active> _actives =
		new IdentityHashMap<>();
	
	/**
	 * Not publically initialized.
	 *
	 * @param __connection The connection to the service.
	 * @throws NullPointerException On null arguments.
	 * @since 2017/12/15
	 */
	IOPipeTimeOutManager(RemoteConnection __connection)
		throws NullPointerException
	{
		if (__connection == null)
			throw new NullPointerException();
		
		this.connection = __connection;
	}
	
	/**
	 * This is used to indicate that the specified execution has finished
	 * and that it should not have a timeout reported for it.
	 *
	 * @param __exec The execution number of the context.
	 * @return {@code true} if this actually timed out before this method
	 * was called.
	 * @throws NullPointerException On null arguments.
	 * @since 2017/12/15
	 */
	public boolean finished(IOPipeContext __c, int __exec)
		throws NullPointerException
	{
		if (__c == null)
			throw new NullPointerException("NARG");

		Map<IOPipeContext, Active> actives = this._actives;
		synchronized (actives)
		{
			// If the active is not actually registered this is a double call
			// but it can safely be ignored
			Active rv = actives.get(__c);
			if (rv == null)
				return false;
			
			// Remove the active because it will not be useable after the
			// thread is destroyed
			if (rv.__finished(__exec))
				actives.remove(__c);
			
			return rv._generated.get();
		}
	}
	
	/**
	 * This will register a new context which if it is left executing for an
	 * extended period of time, it will register a timeout before the AWS
	 * service terminates the context.
	 *
	 * @param __c The context to register.
	 * @param __t The thread executing this method, used to obtain the stack
	 * trace in the event of timeout.
	 * @param __exec The current execution count for the context.
	 * @throws NullPointerException On null arguments.
	 * @since 2017/12/15
	 */
	public Active register(IOPipeContext __c, int __exec, Thread __t)
		throws NullPointerException
	{
		if (__c == null || __t == null)
			throw new NullPointerException("NARG");
		
		Map<IOPipeContext, Active> actives = this._actives;
		synchronized (actives)
		{
			Active rv = actives.get(__c);
			if (rv == null)
				actives.put(__c, (rv = new Active(__c, __exec, __t)));
			else
				rv.__register(__exec, __t);
			
			return rv;
		}
	}
	
	/**
	 * This is used to indicate active contexts to be logged for timeout.
	 *
	 * @since 2017/12/15
	 */
	public final class Active
	{
		/** The execution context. */
		protected final IOPipeContext context;
		
		/** Thread which checks for timeout. */
		protected final Thread thread;
		
		/** Current executions which are active. */
		private final Map<Integer, Thread> _execs =
			new TreeMap<>();
		
		/** Has a report been generated? */
		final AtomicBoolean _generated =
			new AtomicBoolean();
		
		/** Set to true when execution has been terminated, exit the thread. */
		private volatile boolean _terminated;
		
		/**
		 * Initializes the context timeout manager.
		 *
		 * @param __context The context to track executions for.
		 * @param __exec The initial execution to register.
		 * @param __t The thread of the execution context.
		 * @throws NullPointerException On null arguments.
		 * @since 2017/12/15
		 */
		private Active(IOPipeContext __context, int __exec, Thread __t)
			throws NullPointerException
		{
			if (__context == null)
				throw new NullPointerException();
			
			this.context = __context;
			
			// Setup thread which will constantly poll for timeouts
			Thread thread = new Thread(this::__pollingLoop,
				"IOPipe-Timeout-" + System.identityHashCode(__context));
			thread.setDaemon(true);
			this.thread = thread;
			
			// Register the first execution
			__register(__exec, __t);
			
			// Start thread after setting the initial execution because when
			// execution finishes this will be terminated
			thread.start();
		}
		
		/**
		 * This is used to indicate that the specified execution has finished
		 * and that it should not have a timeout reported for it.
		 *
		 * @param __exec The execution number of the context.
		 * @return {@code true} if the execution has finished and the thread
		 * is destroyed and no longer active.
		 * @throws NullPointerException On null arguments.
		 * @since 2017/12/15
		 */
		private boolean __finished(int __exec)
			throws NullPointerException
		{
			Set<Integer> execs = this._execs.keySet();
			synchronized (execs)
			{
				// Removing executions which do not actually exist? Ignore
				if (!execs.remove(__exec))
					return false;
				
				// No more executions being performed, may terminate
				if (execs.isEmpty())
				{
					// Cause the polling thread to terminate when it reads this
					this._terminated = true;
					
					// Interrupt the thread so it awakens from the wait state
					// but also notify it so that waiting stops
					this.thread.interrupt();
					execs.notify();
					
					return true;
				}
				
				// More executions are still running
				else
					return false;
			}
		}
		
		/**
		 * Constantly polls the context with executions to determine if any
		 * of them will timeout.
		 *
		 * @since 2017/12/15
		 */
		private void __pollingLoop()
		{
			Map<Integer, Thread> execs = this._execs;
			IOPipeContext context = this.context;
			Context awscontext = context.context();
			IOPipeConfiguration config = context.config();
			
			// The timeout window is always constant
			long windowtime = config.getTimeOutWindow() * 1_000_000L;
			
			boolean reported = false;
			for (;;)
				synchronized (execs)
				{
					// Determine the amount of time to wait on the lock based
					// on the time that is left for execution
					long remtime = (awscontext.getRemainingTimeInMillis() *
						1_000_000L) - windowtime;
					
					// Wait until interrupted or notification occurs
					if (remtime > 0)
						try
						{
							execs.wait(remtime / 1_000_000L,
								(int)(remtime % 1_000_000L));
						}
						catch (InterruptedException e)
						{
						}
					
					// Terminated
					if (this._terminated)
						return;
					
					// Only when the timer reaches zero, this will cause the
					// thread to constantly poll as it very nearly approaches
					// the window
					if (remtime <= 0)
					{
						// Only generate a single report, if execution times
						// out at the exact right time as the method finishes
						// execution then both reports will be generated.
						if (this._generated.getAndSet(true))
							return;
						
						PrintStream debug = config.getDebugStream();
						if (debug != null)
							debug.printf("IOPipe: Time out by %s%n", context);
						
						// Send reports for each execution
						for (Thread t : execs.values())
						{
							// Generate exception and replace the stack trace
							// of it so that the error seems to have been
							// generated by the thread which timed out
							Throwable fail = new IOPipeTimeOutException(
								t.toString());
							fail.setStackTrace(t.getStackTrace());
		
							// Use temporary metrics because at this point,
							// the code is the same as the other parts
							IOPipeMeasurement measurement =
								new IOPipeMeasurement(context);
							measurement.setThrown(fail);
							
							context.__sendRequest(measurement.buildRequest());
						}
						
						// Stop the reporting thread
						return;
					}
				}
		}
		
		/**
		 * Registers the specified execution number.
		 *
		 * @param __exec The execution count to register.
		 * @param __t The thread of this execution for stack trace generation.
		 * @throws NullPointerException On null arguments.
		 * @since 2017/12/15
		 */
		private void __register(int __exec, Thread __t)
			throws NullPointerException
		{
			if (__t == null)
				throw new NullPointerException();
			
			Map<Integer, Thread> execs = this._execs;
			synchronized (execs)
			{
				execs.put(__exec, __t);
			}
		}
	}
}
