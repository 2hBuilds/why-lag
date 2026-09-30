package com.whylag.core;

/**
 * Every cause of the build plan, by its id (contract 3.2). {@link #shortName()} is the phrase the X verdict names
 * ("It was memory clean-up or lost packets."). Only the causes of wave one's fifteen rules, plus
 * {@link #WARMING_UP}, answer {@link #inWaveOne()}: the rest are named so their ids stay fixed, and no wave-one
 * rule gives them.
 *
 * <p>{@link #WARMING_UP} is the one wave-one cause that no rule gives: it is the cause of each of the three states
 * of the card that are not verdicts (contract 5.1) - "Not logged in", "Still measuring" and "Waiting for the game" -
 * each with the confidence {@link Confidence#CANT_TELL} and the level {@link Level#NO_DATA}. They differ by their
 * headline.
 *
 * <p>{@link #DELIVERY_GAP} (N6) is new with skeptic C2: a delivery gap whose cause is unknown. {@link #TICKS_STOPPED}
 * (D3) has no rule in wave one; N6 covers its trace.
 */
public enum Cause
{
	GC_PAUSE("G1", Group.MEMORY, "memory clean-up"),
	HEAP_CAP_LOW("G2", Group.MEMORY, "the memory limit"),
	HEAP_FILLING("G3", Group.MEMORY, "memory filling up"),
	MAP_LOAD("S1", Group.FRAME_RATE, "map loading"),
	EVENT_STALL("S2", Group.FRAME_RATE, "a plugin"),
	CLIENT_BUSY("S3", Group.FRAME_RATE, "a client stall"),
	CLIENT_WAITING("S4", Group.FRAME_RATE, "the client waiting"),
	OTHER_PROGRAM("S5", Group.FRAME_RATE, "another program"),
	FRAME_CAP("F1", Group.FRAME_RATE, "a frame cap"),
	SLOW_DRAWING("F2", Group.FRAME_RATE, "slow drawing"),
	CROWD("F3", Group.FRAME_RATE, "this place"),
	SCALES_BADLY("F4", Group.FRAME_RATE, "this place"),
	PING_HIGH("N1", Group.CONNECTION, "high ping"),
	PING_JUMPY("N2", Group.CONNECTION, "your connection"),
	UPLOAD_LOSS("N3", Group.CONNECTION, "lost packets"),
	PERIODIC_SPIKE("N4", Group.CONNECTION, "a ping spike"),
	MAC_WIFI("N5", Group.CONNECTION, "Wi-Fi"),
	DELIVERY_GAP("N6", Group.UNSURE, "your line or the server"),
	SLOW_WORLD("W1", Group.WORLD, "the world"),
	WORLD_ONLY("W2", Group.WORLD, "the world"),
	ROUTE("W3", Group.WORLD, "the route"),
	YOUR_LINE("W4", Group.CONNECTION, "your line"),
	DISCONNECT("D1", Group.CONNECTION, "a disconnect"),
	WEEKLY_UPDATE("D2", Group.WORLD, "the weekly update"),
	TICKS_STOPPED("D3", Group.UNSURE, "your line or the server"),
	VSYNC_HINT("V1", Group.NONE, "V-Sync"),
	ALL_CLEAR("V2", Group.NONE, ""),
	NOT_SURE("X", Group.UNSURE, ""),
	WARMING_UP("-", Group.NONE, "");

	private final String id;
	private final Group group;
	private final String shortName;

	Cause(String id, Group group, String shortName)
	{
		this.id = id;
		this.group = group;
		this.shortName = shortName;
	}

	/** The plan's id: "G1", "N6", "X", "-". */
	public String id()
	{
		return id;
	}

	/** What an event with this cause is counted under. */
	public Group group()
	{
		return group;
	}

	/** The phrase the X verdict uses for this cause; "" where X never names it. */
	public String shortName()
	{
		return shortName;
	}

	/**
	 * True for exactly sixteen causes: those of the fifteen rules of section 6 (D1, G1, S1, N3, N2, W1 - whose
	 * condition form W1c reuses it - S3, S4, N6, F1, N1, F2, G2, V2, X) and {@link #WARMING_UP}.
	 */
	public boolean inWaveOne()
	{
		switch (this)
		{
			case DISCONNECT:
			case GC_PAUSE:
			case MAP_LOAD:
			case UPLOAD_LOSS:
			case PING_JUMPY:
			case SLOW_WORLD:
			case CLIENT_BUSY:
			case CLIENT_WAITING:
			case DELIVERY_GAP:
			case FRAME_CAP:
			case PING_HIGH:
			case SLOW_DRAWING:
			case HEAP_CAP_LOW:
			case ALL_CLEAR:
			case NOT_SURE:
			case WARMING_UP:
				return true;
			default:
				return false;
		}
	}
}
