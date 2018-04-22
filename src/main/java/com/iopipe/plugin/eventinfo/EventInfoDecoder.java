package com.iopipe.plugin.eventinfo;

/**
 * This interface represents a decoder for event types being input into the
 * executed method and contains the information that is needed to parse
 * fields accordingly.
 *
 * @since 2018/04/22
 */
public interface EventInfoDecoder
{
	/**
	 * Returns the class this implements a decoder for.
	 *
	 * @return The class this provides a decoder for.
	 * @since 2018/04/22
	 */
	public abstract Class<?> decodes();
}

