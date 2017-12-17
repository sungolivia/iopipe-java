package com.iopipe;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * This class contains methods which are used to build requests which would
 * be sent to a server.
 *
 * @since 2017/12/15
 */
public final class IOPipeRequestBuilder
{
	/** Is this a Linux system? */
	private static final boolean _IS_LINUX =
		"linux".compareToIgnoreCase(
			System.getProperty("os.name", "unknown")) == 0;
	
	/** The system properties to copy in the environment report. */
	private static final List<String> _COPY_PROPERTIES =
		Collections.<String>unmodifiableList(Arrays.<String>asList(
			"java.version", "java.vendor", "java.vendor.url",
			"java.vm.specification.version",
			"java.vm.specification.vendor", "java.vm.specification.name",
			"java.vm.version", "java.vm.vendor", "java.vm.name",
			"java.specification.version", "java.specification.vendor",
			"java.specification.name", "java.class.version",
			"java.compiler", "os.name", "os.arch", "os.version",
			"file.separator", "path.separator"));
	
	/**
	 * Not used.
	 *
	 * @since 2017/12/15
	 */
	private IOPipeRequestBuilder()
	{
	}
	
	/**
	 * Builds a report with the specified metrics.
	 *
	 * @param __c The context to report for.
	 * @param __m The result of execution.
	 * @return The value to be sent to the server.
	 * @throws NullPointerException On null arguments.
	 * @since 2017/12/15
	 */
	public static JsonObject ofMetrics(IOPipeContext __c, IOPipeMetrics __m)
		throws NullPointerException
	{
		if (__c == null || __m == null)
			throw new NullPointerException();
		
		JsonObjectBuilder rv = Json.createObjectBuilder();
		__fillCommon(__c, rv);
		
		long duration = __m.getDuration();
		if (duration >= 0)
			rv.add("duration", duration);
		
		Throwable thrown = __m.getThrown();
		if (thrown != null)
		{
			throw new Error("TODO");
		}
		
		return rv.build();
	}
	
	/**
	 * Builds a report of a timeout.
	 *
	 * @param __c The context which timed out.
	 * @param __t The thread where the timeout occurred
	 * @return The value to be sent to the server.
	 * @throws NullPointerException On null arguments.
	 * @since 2017/12/15
	 */
	public static JsonObject ofTimeout(IOPipeContext __c, Thread __t)
		throws NullPointerException
	{
		if (__c == null || __t == null)
			throw new NullPointerException();
		
		// Generate exception and replace the stack trace of it so that the
		// error seems to have been generated by the thread which timed out
		Throwable fail = new IOPipeTimeOutException(__t.toString());
		fail.setStackTrace(__t.getStackTrace());
		
		// Use temporary metrics because at this point, the code is the same
		// as the other parts
		IOPipeMetrics metrics = new IOPipeMetrics();
		metrics.setThrown(fail);
		return IOPipeRequestBuilder.ofMetrics(__c, metrics);
	}
	
	/**
	 * Fills a common JSON object with parameters to be sent to the remote
	 * server.
	 *
	 * @param __c The execution context.
	 * @param __o The object to write base information into.
	 * @throws NullPointerException On null arguments.
	 * @since 2017/12/15
	 */
	private static void __fillCommon(IOPipeContext __c, JsonObjectBuilder __o)
		throws NullPointerException
	{
		if (__c == null || __o == null)
			throw new NullPointerException();
		
		IOPipeConfiguration config = __c.config();
		
		// The first report that gets generated gets the coldstart flag, but
		// after that point all other reports are considered thawed
		__o.add("coldstart", !IOPipeService._THAWED.getAndSet(true));
		
		__o.add("client_id", config.getProjectToken());
		__o.add("installMethod", Objects.toString(config.getInstallMethod(),
			"unknown"));
		
		__o.add("aws", __generateAws(__c));
		__o.add("memory", __generateMemory());
		__o.add("environment", __generateEnvironment());
		
		// System provided information
		RuntimeMXBean runtimemx = ManagementFactory.getRuntimeMXBean();
		__o.add("processId", runtimemx.getName());
		__o.add("timestamp", runtimemx.getStartTime() / 1000);
	}
	
	/**
	 * Generates the Amazon web service report.
	 *
	 * @param __c The context to use.
	 * @return The AWS information object.
	 * @since 2017/12/15
	 */
	private static JsonObject __generateAws(IOPipeContext __c)
		throws NullPointerException
	{
		if (__c == null)
			throw new NullPointerException();
		
		Context awscontext = __c.context();
		JsonObjectBuilder rv = Json.createObjectBuilder();
		
		rv.add("functionName", awscontext.getFunctionName());
		rv.add("functionVersion", awscontext.getFunctionVersion());
		rv.add("awsRequestId", awscontext.getAwsRequestId());
		rv.add("invokedFunctionArn", awscontext.getInvokedFunctionArn());
		rv.add("logGroupName", awscontext.getLogGroupName());
		rv.add("logStreamName", awscontext.getLogStreamName());
		rv.add("memoryLimitInMB", awscontext.getMemoryLimitInMB());
		rv.add("getRemainingTimeInMillis",
			awscontext.getRemainingTimeInMillis());
		rv.add("traceId", Objects.toString(
			System.getenv("_X_AMZN_TRACE_ID"), "unknown"));
		
		return rv.build();
	}
	
	/**
	 * Generates the environment information.
	 *
	 * @return The environment information.
	 * @since 2017/12/16
	 */
	private static JsonObject __generateEnvironment()
	{
		JsonObjectBuilder rv = Json.createObjectBuilder();
		
		rv.add("agent", __generateEnvironmentAgent());
		rv.add("host", __generateEnvironmentHost());
		rv.add("java", __generateEnvironmentJava());
		rv.add("os", __generateEnvironmentOs());
		
		return rv.build();
	}
	
	/**
	 * Generates the agent information.
	 *
	 * @return The agent information.
	 * @since 2017/12/26
	 */
	private static JsonObject __generateEnvironmentAgent()
	{
		JsonObjectBuilder rv = Json.createObjectBuilder();
		
		rv.add("runtime", "java");
		rv.add("version", IOPipeService.AGENT_VERSION);
		rv.add("load_time", IOPipeService._LOAD_TIME / 1_000_000L);
		
		return rv.build();
	}
	
	/**
	 * Generates host information.
	 *
	 * @return The host information.
	 * @since 2017/12/17
	 */
	private static JsonObject __generateEnvironmentHost()
	{
		JsonObjectBuilder rv = Json.createObjectBuilder();
		
		if (_IS_LINUX)
		{
			String bootid = __readFirstLine(
				Paths.get("/proc/sys/kernel/random/boot_id"));
			if (bootid != null)
				rv.add("boot_id", bootid);
		}
		
		return rv.build();
	}
	
	/**
	 * Generates the Java VM information.
	 *
	 * @return The VM information.
	 * @since 2017/12/16
	 */
	private static JsonObject __generateEnvironmentJava()
	{
		JsonObjectBuilder rv = Json.createObjectBuilder();
		
		for (String p : IOPipeRequestBuilder._COPY_PROPERTIES)
			rv.add(p, System.getProperty(p, ""));
			
		return rv.build();
	}
	
	/**
	 * Generates Operating System information.
	 *
	 * @return The operating system information.
	 * @since 2017/12/16
	 */
	private static JsonObject __generateEnvironmentOs()
	{
		JsonObjectBuilder rv = Json.createObjectBuilder();
		JsonArrayBuilder cpus = Json.createArrayBuilder();
		
		boolean addedhostname = false;
		
		if (_IS_LINUX)
		{
			// Getting the hostname from /etc/hostname is the most reliable on
			// Linux because the standard Java means does not work that great
			String hostname = __readFirstLine(Paths.get("/etc/hostname"));
			if ((addedhostname = (hostname != null)))
				rv.add("hostname", hostname);
			
			//rv.add("totalmem", ???);
			//rv.add("freemem", ???);
			//rv.add("usedmem", ???);
			//rv.add("cpus", ???);
		}
		
		// Use this as a fallback for determining the hostname, in the VM it
		// is not really reliable at all because it could return the hostname
		// for any interface (such as localhost for 127.0.0.1).
		if (!addedhostname)
			try
			{
				rv.add("hostname", InetAddress.getLocalHost().getHostName());
			}
			catch (IOException e)
			{
				rv.add("hostname", "unknown");
			}
		
		rv.add("cpus", cpus.build());
		
		return rv.build();
	}
	
	/**
	 * Generates the process memory information.
	 *
	 * @return The memory information.
	 * @since 2017/12/16
	 */
	private static JsonObject __generateMemory()
	{
		JsonObjectBuilder rv = Json.createObjectBuilder();
		
		if (_IS_LINUX)
		{
			//rv.add("rssMiB", ???);
			//rv.add("totalMiB", ???);
			//rv.add("rssTotalPercentage", ???);
		}
		
		return rv.build();
	}
	
	/**
	 * Reads the first non-empty line for the given path.
	 *
	 * @param __p The path to read.
	 * @return The first non-empty line or {@code null} if the file could not
	 * be read or has only empty lines.
	 * @throws NullPointerException On null arguments.
	 * @since 2017/12/17
	 */
	private static String __readFirstLine(Path __p)
		throws NullPointerException
	{
		if (__p == null)
			throw new NullPointerException();
		
		try
		{
			for (String l : Files.readAllLines(__p))
			{
				l = l.trim();
				if (!l.isEmpty())
					return l;
			}
			
			return null;
		}
		
		catch (IOException e)
		{
			return null;
		}
	}
}

