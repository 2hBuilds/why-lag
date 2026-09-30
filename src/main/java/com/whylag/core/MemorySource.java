package com.whylag.core;

/**
 * Where memory readings come from (contract 3.2, 7 L7). {@link #MANAGEMENT} reads real collection pauses from
 * Java's management beans; {@link #RUNTIME} is the fallback, which only sees the heap and INFERS a collection from
 * a fall, with no length.
 */
public enum MemorySource
{
	MANAGEMENT,
	RUNTIME
}
