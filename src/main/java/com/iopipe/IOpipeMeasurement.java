package com.iopipe;

import com.amazonaws.services.lambda.runtime.Context;
import com.iopipe.http.RemoteException;
import com.iopipe.http.RemoteRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.stream.JsonGenerator;

/**
 * This class is used to keep track of measurements during execution.
 *
 * @since 2017/12/15
 */
public final class IOpipeMeasurement
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
	
	/** The configuration. */
	protected final IOpipeConfiguration config;
	
	/** The context this is taking the measurement for. */
	protected final Context context;
	
	/**
	 * Performance entries which have been added to the measurement, this
	 * field is locked since multiple threads may be adding entries.
	 */
	private final Set<TracePerformanceEntry> _perfentries =
		new TreeSet<>();
	
	/** The exception which may have been thrown. */
	private volatile Throwable _thrown;
	
	/** The duration of execution in nanoseconds. */
	private volatile long _duration =
		Long.MIN_VALUE;
	
	/** Is this execution one which is a cold start? */
	private volatile boolean _coldstart;
	
	/**
	 * Initializes the measurement holder.
	 *
	 * @param __config The configuration for the context.
	 * @param __context The context this holds measurements for.
	 * @throws NullPointerException On null arguments.
	 * @since 2017/12/17
	 */
	public IOpipeMeasurement(IOpipeConfiguration __config, Context __context)
		throws NullPointerException
	{
		if (__config == null || __context == null)
			throw new NullPointerException();
		
		this.config = __config;
		this.context = __context;
	}
	
	/**
	 * Adds a single performance entry to the report.
	 *
	 * @param __e The entry to add to the report.
	 * @throws NullPointerException On null arguments.
	 * @since 2018/01/19
	 */
	public void addPerformanceEntry(TracePerformanceEntry __e)
		throws NullPointerException
	{
		if (__e == null)
			throw new NullPointerException();
		
		// Multiple threads could be adding entries
		Set<TracePerformanceEntry> perfentries = this._perfentries;
		synchronized (perfentries)
		{
			perfentries.add(__e);
		}
	}
	
	/**
	 * Builds the request which is sent to the remote service.
	 *
	 * @return The remote request to send to the service.
	 * @throws RemoteException If the request could not be built.
	 * @since 2017/12/17
	 */
	public RemoteRequest buildRequest()
		throws RemoteException
	{
		Context aws = this.context;
		IOpipeConfiguration config = this.config;
		
		// Snapshot system information
		SystemMeasurement sysinfo = new SystemMeasurement();
		
		// The current timestamp
		long nowtimestamp = System.currentTimeMillis();
		
		StringWriter out = new StringWriter();
		try (JsonGenerator gen = Json.createGenerator(out))
		{
			gen.writeStartObject();
			
			gen.write("client_id", config.getProjectToken());
			// UNUSED: "projectId": "s"
			gen.write("installMethod",
				Objects.toString(config.getInstallMethod(), "unknown"));
			
			long duration = this._duration;
			if (duration >= 0)
				gen.write("duration", duration);
			
			gen.write("processId", sysinfo.pid);
			gen.write("timestamp", IOpipeConstants.LOAD_TIME);
			gen.write("timestampEnd", nowtimestamp);
			
			// AWS Context information
			gen.writeStartObject("aws");
			
			gen.write("functionName", aws.getFunctionName());
			gen.write("functionVersion", aws.getFunctionVersion());
			gen.write("awsRequestId", aws.getAwsRequestId());
			gen.write("invokedFunctionArn", aws.getInvokedFunctionArn());
			gen.write("logGroupName", aws.getLogGroupName());
			gen.write("logStreamName", aws.getLogStreamName());
			gen.write("memoryLimitInMB", aws.getMemoryLimitInMB());
			gen.write("getRemainingTimeInMillis",
				aws.getRemainingTimeInMillis());
			gen.write("traceId", Objects.toString(
				System.getenv("_X_AMZN_TRACE_ID"), "unknown"));
			
			gen.writeEnd();
			
			// Memory Usage -- UNUSED
			/*gen.writeStartObject("memory");
			
			gen.write("rssMiB", );
			gen.write("totalMiB", );
			gen.write("rssTotalPercentage", );
			
			gen.writeEnd();*/
			
			// Environment start
			gen.writeStartObject("environment");
			
			// Agent
			gen.writeStartObject("agent");
			
			gen.write("runtime", "java");
			gen.write("version", IOpipeConstants.AGENT_VERSION);
			gen.write("load_time", IOpipeConstants.LOAD_TIME);
			
			gen.writeEnd();
			
			// Java information
			gen.writeStartObject("java");
			
			for (String prop : IOpipeMeasurement._COPY_PROPERTIES)
				gen.write(prop, System.getProperty(prop, ""));
			
			gen.writeEnd();
			
			// Unique operating system boot identifier
			gen.writeStartObject("host");
			
			gen.write("boot_id", SystemMeasurement.BOOTID);
			
			gen.writeEnd();
			
			// Operating System Start
			gen.writeStartObject("os");
			
			long totalmem, freemem;
			gen.write("hostname", SystemMeasurement.HOSTNAME);
			gen.write("totalmem", (totalmem = sysinfo.memorytotalkib));
			gen.write("freemem", (freemem = sysinfo.memoryfreekib));
			gen.write("usedmem", totalmem - freemem);
			
			// Start CPUs
			gen.writeStartArray("cpus");
			
			List<SystemMeasurement.Cpu> cpus = sysinfo.cpus;
			for (int i = 0, n = cpus.size(); i < n; i++)
			{
				SystemMeasurement.Cpu cpu = cpus.get(i);
				
				gen.writeStartObject();
				gen.writeStartObject("times");
				
				gen.write("idle", cpu.idle);
				gen.write("irq", cpu.irq);
				gen.write("sys", cpu.sys);
				gen.write("user", cpu.user);
				gen.write("nice", cpu.nice);
				
				gen.writeEnd();
				gen.writeEnd();
			}
			
			// End CPUs
			gen.writeEnd();
			
			// Linux information
			if (_IS_LINUX)
			{
				// Start Linux
				gen.writeStartObject("linux");
				
				// Start PID
				gen.writeStartObject("pid");
				
				// Start self
				gen.writeStartObject("self");
				
				gen.writeStartObject("stat");
				
				SystemMeasurement.Times times = new SystemMeasurement.Times();
				gen.write("utime", times.utime);
				gen.write("stime", times.stime);
				gen.write("cutime", times.cutime);
				gen.write("cstime", times.cstime);
				
				gen.writeEnd();
				
				gen.writeStartObject("stat_start");
				
				times = IOpipeService._STAT_START;
				gen.write("utime", times.utime);
				gen.write("stime", times.stime);
				gen.write("cutime", times.cutime);
				gen.write("cstime", times.cstime);
				
				gen.writeEnd();
				
				gen.writeStartObject("status");
				
				gen.write("VmRSS", sysinfo.vmrsskib);
				gen.write("Threads", sysinfo.threads);
				gen.write("FDSize", sysinfo.fdsize);
				
				gen.writeEnd();
				
      			// End self
      			gen.writeEnd();
				
				// End PID
				gen.writeEnd();
				
				// End Linux
				gen.writeEnd();
			}
			
			// Operating System end
			gen.writeEnd();
			
			// Environment end
			gen.writeEnd();
			
			Throwable thrown = this._thrown;
			if (thrown != null)
			{
				gen.writeStartObject("errors");
				
				// Write the stack as if it were normally output on the console
				StringWriter trace = new StringWriter();
				try (PrintWriter pw = new PrintWriter(trace))
				{
					thrown.printStackTrace(pw);
			
					pw.flush();
				}
				
				gen.write("stack", trace.toString());
				gen.write("name", thrown.getClass().getName());
				gen.write("message",
					Objects.toString(thrown.getMessage(), ""));
				// UNUSED: "stackHash": "s",
				// UNUSED: "count": "n"
				
				gen.writeEnd();
			}
			
			gen.write("coldstart", this._coldstart);
			
			// Multiple threads may have stored performance entries, so it
			// is possible that the list may be in a state where it is
			// inconsistent due to cache differences
			Set<TracePerformanceEntry> perfentries = this._perfentries;
			synchronized (perfentries)
			{
				if (!perfentries.isEmpty())
				{
					// Entries are stored in an array
					gen.writeStartArray("performanceEntries");
					
					// Write each entry
					for (TracePerformanceEntry e : perfentries)
					{
						gen.writeStartObject();
						
						gen.write("name",
							Objects.toString(e.name(), "unknown"));
						gen.write("startTime", e.startTimeMillis());
						gen.write("duration",
							e.durationNanoTime() / 1_000_000L);
						gen.write("entryType",
							Objects.toString(e.type(), "unknown"));
						gen.write("timestamp", nowtimestamp);
						
						gen.writeEnd();
					}
					
					// End of array
					gen.writeEnd();
				}
			}
			
			// Finished
			gen.writeEnd();
			gen.flush();
		}
		catch (JsonException e)
		{
			throw new RemoteException("Could not build request", e);
		}
		
		return new RemoteRequest(out.toString());
	}
	
	/**
	 * Returns the execution duration.
	 *
	 * @return The execution duration, if this is negative then it is not
	 * valid.
	 * @since 2017/12/15
	 */
	public long getDuration()
	{
		return this._duration;
	}
	
	/**
	 * Returns the thrown throwable.
	 *
	 * @return The throwable which was thrown or {@code null} if none was
	 * thrown.
	 * @since 2017/12/15
	 */
	public Throwable getThrown()
	{
		return this._thrown;
	}
	
	/**
	 * Creates a new mark which represents a single point in time and adds it
	 * to the report.
	 *
	 * @param __name The name of the mark to create.
	 * @return The created mark.
	 * @throws NullPointerException On null arguments.
	 * @since 2018/01/19
	 */
	public TraceMark mark(String __name)
		throws NullPointerException
	{
		if (__name == null)
			throw new NullPointerException();
		
		TraceMark rv = new TraceMark(__name);
		this.addPerformanceEntry(rv);
		return rv;
	}
	
	/**
	 * Creates a new instance of a class which is used to measure how long
	 * a block of code has executed for. The returned object is
	 * {@link AutoCloseable} and it is highly recommended to use
	 * try-with-resources when utilizing it. When the method
	 * {@link AutoCloseable#close()} is called the measurement will be
	 * recorded.
	 *
	 * @param __name The name of the measurement.
	 * @return NullPointerException On null arguments.
	 * @since 2018/01/19
	 */
	public TraceMeasurement measure(String __name)
		throws NullPointerException
	{
		if (__name == null)
			throw new NullPointerException();
		
		return new TraceMeasurement(this, __name);
	}
	
	/**
	 * Creates a measurement between the two marks.
	 *
	 * @param __a The first mark.
	 * @param __b The second mark.
	 * @return A performance entry which defines a measurement between
	 * the two marks.
	 * @throws NullPointerException On null arguments.
	 * @since 2018/01/19
	 */
	public TracePerformanceEntry measure(TraceMark __a, TraceMark __b)
		throws NullPointerException
	{
		if (__a == null || __b == null)
			throw new NullPointerException();
		
		throw new Error("TODO");
	}
	
	/**
	 * Sets whether or not the execution was a cold start. A cold start
	 * indicates that the JVM was started fresh and a previous instance is not
	 * being reused.
	 *
	 * @param __cold If {@code true} then the execution follows a cold start.
	 * @since 2017/12/20
	 */
	void __setColdStart(boolean __cold)
	{
		this._coldstart = __cold;
	}
	
	/**
	 * Sets the duration of execution.
	 *
	 * @param __ns The execution duration in nanoseconds.
	 * @since 2017/12/15
	 */
	void __setDuration(long __ns)
	{
		this._duration = __ns;
	}
	
	/**
	 * Sets the throwable generated during execution.
	 *
	 * @param __t The generated throwable.
	 * @since 2017/12/15
	 */
	void __setThrown(Throwable __t)
	{
		this._thrown = __t;
	}
}

