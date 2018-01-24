package com.iopipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test engine which performs the bulk of the logic for executing
 * tests.
 *
 * @since 2018/01/23
 */
public abstract class Engine
{
	/** The tests which should be ran through each engine. */
	private static final SingleTestConstructor[] _RUN_TESTS =
		new SingleTestConstructor[]
		{
			__DoEmptyMethod__::new,
			__DoThrowException__::new,
		};
	
	/** The base name for this engine. */
	protected final String basename;
	
	/**
	 * Initializes the base engine.
	 *
	 * @param __bn The base name of the engine.
	 * @throws NullPointerException On null arguments.
	 * @since 2018/01/23
	 */
	public Engine(String __bn)
		throws NullPointerException
	{
		if (__bn == null)
			throw new NullPointerException();
		
		this.basename = __bn;
	}
	
	/**
	 * Generates a new configuration to use for this test.
	 *
	 * @param __s The test being ran, may be used by the engine to change
	 * the connection factory to monitor requests as needed.
	 * @return The generated configuration.
	 * @throws NullPointerException On null arguments.
	 * @since 2018/01/23
	 */
	protected abstract IOpipeConfigurationBuilder generateConfig(Single __s)
		throws NullPointerException;
	
	/**
	 * Returns the function to use for end tests.
	 *
	 * @return The end test function.
	 * @since 2018/01/23
	 */
	protected abstract Consumer<Single> endTestFunction();
	
	/**
	 * Returns the base name of this engine.
	 *
	 * @return The engine base name
	 * @since 2018/01/23
	 */
	public final String baseName()
	{
		return this.basename;
	}
	
	/**
	 * Runs the specified test.
	 *
	 * @param __s The test to run.
	 * @throws NullPointerException On null arguments.
	 * @since 2018/01/23
	 */
	private final void __run(Single __s)
		throws NullPointerException
	{
		if (__s == null)
			throw new NullPointerException("NARG");
		
		// Run it
		IOpipeService sv = null;
		
		// Obtain configuration to use
		IOpipeConfigurationBuilder confbld = this.generateConfig(__s);
		
		// Wrap the connection factory with one where we can tunnel returned
		// results from the remote service to our single handler
		confbld.setRemoteConnectionFactory(new __WrappedConnectionFactory__(
			__s, confbld.getRemoteConnectionFactory()));
		
		// Setup service
		sv = new IOpipeService(confbld.build());
		
		// Has the function body been entered?
		AtomicBoolean enteredbody = new AtomicBoolean();
		
		// Execute service
		try
		{
			sv.<Object>run(new MockContext(__s.fullName()), (__exec) ->
				{
					// Body entered, which should always happen no matter
					// what
					enteredbody.set(true);
				
					// Exceptions could be thrown
					try
					{
						// Run it
						__s.run(__exec);
					
						// No exception expected
						__s.assertFalse(false, "mockexception");
					}
	
					// Threw an exception, which might not be in error
					catch (Throwable t)
					{
						// Mock exception was thrown, treat that as valid
						if (t instanceof MockException)
						{
							__s.assertTrue(true, "mockexception");
						
							// Throw it again so an error is actually generated
							throw (MockException)t;
						}
		
						// Otherwise, this is not good
						else
							throw new RuntimeException(t);
					}
				
					return null;
				});
		}
		
		// Ignore the mock exception
		catch (MockException e)
		{
		}
			
		// The body must have always been entered
		__s.assertTrue(enteredbody.get(), "enteredbody");
		
		// Common end of service
		__s.endCommon();
		
		// And specific tests according to the service
		endTestFunction().accept(__s);
	}
	
	/**
	 * Generates test to be executed according to the specified engine.
	 *
	 * @param __con The constructor for the test engine which are run under
	 * tests.
	 * @return The iterable set of tests to run.
	 * @throws NullPointerException On null arguments.
	 * @since 2018/01/23
	 */
	public static Iterable<DynamicTest> generateTests(Supplier<Engine> __con)
		throws NullPointerException
	{
		if (__con == null)
			throw new NullPointerException();
		
		Collection<DynamicTest> rv = new ArrayList<>();
		
		// Go through all constructors and setup all tests
		for (SingleTestConstructor constructor : Engine._RUN_TESTS)
		{
			// Setup engine instance
			Engine engine = __con.get();
			
			// Setup instance since it needs to be known the test details
			Single instance = constructor.construct(engine);
			
			// Add test
			rv.add(DynamicTest.dynamicTest(instance.fullName(),
				() -> engine.__run(instance)));
		}
		
		return rv;
	}
	
	/**
	 * Constructor for a single test.
	 *
	 * @param <C> The type of test to construct.
	 * @since 2018/01/23
	 */
	@FunctionalInterface
	public static interface SingleTestConstructor<C extends Single>
	{
		/**
		 * Constructs an instance of the given class.
		 *
		 * @param __e The owning engine.
		 * @return The instance of the test.
		 * @since 2018/01/23
		 */
		public abstract C construct(Engine __e);
	}
}

