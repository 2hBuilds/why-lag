package com.whylag.core;

/**
 * Which renderer draws the game (contract 3.2, 3.7): 117 HD when it is active, else the GPU plugin when it is
 * active, else the client's own CPU renderer. {@link #UNKNOWN} until the settings have been read.
 */
public enum Renderer
{
	CPU("CPU"),
	GPU("GPU"),
	HD("117 HD"),
	UNKNOWN("unknown");

	private final String label;

	Renderer(String label)
	{
		this.label = label;
	}

	/** The report's word: "CPU", "GPU", "117 HD", "unknown". */
	public String label()
	{
		return label;
	}
}
