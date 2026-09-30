package com.whylag.core;

/**
 * How a status reads (contract 3.2 and 5). Each level is drawn with its own shape as well as its colour - a filled
 * circle, a triangle, a square, a hollow ring - so no status rests on colour alone.
 *
 * <p>The ORDINAL is stored: {@link Strip#levels} holds one ordinal per column. Do not reorder.
 */
public enum Level
{
	/** Filled circle, green. */
	OK,
	/** Filled triangle, amber. */
	WARN,
	/** Filled square, red. */
	BAD,
	/** Hollow ring, grey. */
	NO_DATA
}
