package com.whylag.core;

/**
 * The game badge's picture of a cause (contract 3.2, P2.7): one of the five pictures of set A in
 * {@code game-icons.js}, or {@link #NONE}, which means the status shape alone. {@link Answer} gives every cause and
 * card state its icon; the badge's painter loads {@code badge-<file>-<state>.png} for the WARN and BAD states only,
 * so OK and NO_DATA always draw a shape.
 */
public enum Icon
{
	/** No picture: the status shape alone (smooth, measuring, waiting, not logged in). */
	NONE(""),
	/** The planet: the world is slow. */
	WORLD("world"),
	/** The Wi-Fi sign: the line between this PC and the world. */
	LINE("line"),
	/** The monitor: this PC, or the client on it. */
	PC("pc"),
	/** The memory stick: the client's memory. */
	MEMORY("memory"),
	/** The question mark: a lag whose cause cannot be told. */
	UNKNOWN("unknown");

	private final String file;

	Icon(String file)
	{
		this.file = file;
	}

	/**
	 * The icon's word in its file names, {@code badge-<file>-<state>.png}: "world", "line", "pc", "memory",
	 * "unknown"; "" for {@link #NONE}, which has no file.
	 */
	public String file()
	{
		return file;
	}
}
