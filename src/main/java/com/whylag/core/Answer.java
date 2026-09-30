package com.whylag.core;

/**
 * The big two-line answer of every cause and every card state (contract 3.6): the ONE source of those words. The
 * panel's answer card, the game badge, the badge's tooltip and the chat line all print it, so they can never
 * disagree. Immutable; every instance is a constant of this class, and {@link #of(Verdict)} and {@link #of(Cause)}
 * answer one of them and allocate nothing, so a caller may compare answers by identity.
 *
 * <p>{@code line1} is the cause, or the state, and is never ""; {@code line2} says whose side it is, "" when the
 * answer has one line; {@code icon} is the badge's picture ({@link Icon#NONE}: the status shape alone);
 * {@code oneLine} is {@code line1 + " - " + line2} with the FIRST letter of {@code line2} in lower case ("World lag -
 * not you", "Low FPS - your PC", "Client froze - a plugin?"), or {@code line1} alone when {@code line2} is "". The
 * LEVEL is not part of the answer: it is the verdict's, so W1 (an event, BAD) and W1c (a condition, WARN) share one
 * pair and differ by their shape.
 *
 * <table>
 * <caption>The answers of the wave-one causes and the three card states</caption>
 * <tr><th>Cause (rule)</th><th>Line 1</th><th>Line 2</th><th>Icon</th></tr>
 * <tr><td>ALL_CLEAR (V2)</td><td>Smooth</td><td>-</td><td>NONE</td></tr>
 * <tr><td>SLOW_WORLD (W1, W1c)</td><td>World lag</td><td>Not you</td><td>WORLD</td></tr>
 * <tr><td>PING_JUMPY (N2)</td><td>Ping lag</td><td>Your internet</td><td>LINE</td></tr>
 * <tr><td>PING_HIGH (N1)</td><td>High ping</td><td>Your internet</td><td>LINE</td></tr>
 * <tr><td>UPLOAD_LOSS (N3)</td><td>Packet loss</td><td>Line or world</td><td>LINE</td></tr>
 * <tr><td>DISCONNECT (D1)</td><td>Disconnected</td><td>Line or world</td><td>LINE</td></tr>
 * <tr><td>DELIVERY_GAP (N6)</td><td>No ticks</td><td>Line or world</td><td>UNKNOWN</td></tr>
 * <tr><td>SLOW_DRAWING (F2)</td><td>Low FPS</td><td>Your PC</td><td>PC</td></tr>
 * <tr><td>FRAME_CAP (F1)</td><td>FPS capped</td><td>Your setting</td><td>PC</td></tr>
 * <tr><td>CLIENT_BUSY (S3)</td><td>Client froze</td><td>A plugin?</td><td>PC</td></tr>
 * <tr><td>CLIENT_WAITING (S4)</td><td>Client waited</td><td>An overlay?</td><td>PC</td></tr>
 * <tr><td>MAP_LOAD (S1)</td><td>Map loading</td><td>Just the map</td><td>PC</td></tr>
 * <tr><td>GC_PAUSE (G1)</td><td>Memory stall</td><td>The client</td><td>MEMORY</td></tr>
 * <tr><td>HEAP_CAP_LOW (G2)</td><td>Low memory</td><td>Your setting</td><td>MEMORY</td></tr>
 * <tr><td>NOT_SURE (X)</td><td>Lag</td><td>Can't tell why</td><td>UNKNOWN</td></tr>
 * <tr><td>WARMING_UP, headline {@link #HEAD_MEASURING}</td><td>Measuring</td><td>-</td><td>NONE</td></tr>
 * <tr><td>WARMING_UP, headline {@link #HEAD_NOT_LOGGED_IN}</td><td>Not logged in</td><td>-</td><td>NONE</td></tr>
 * <tr><td>WARMING_UP, headline {@link #HEAD_WAITING}</td><td>Waiting</td><td>-</td><td>NONE</td></tr>
 * </table>
 *
 * <p>A cause that is NOT in wave one answers its GROUP's pair, so {@code of} never fails: CONNECTION "Ping lag" /
 * "Your internet"; FRAME_RATE "Low FPS" / "Your PC"; MEMORY "Memory stall" / "The client"; WORLD "World lag" / "Not
 * you"; UNSURE "Lag" / "Can't tell why"; NONE "Smooth" - the same constants as the wave-one causes of those pairs.
 *
 * <p><b>Every line fits</b> (measured on 2026-09-29 with {@code FontMetrics} on the fonts of {@code client} 1.12.37,
 * text anti-aliasing off): in RuneScape Bold at 32 px the widest is "Can't tell why", 178 px, the card's whole big
 * line; at 16 px in RuneScape regular, the badge's face, "Disconnected", 76 px. No line is longer than 14
 * characters, and every one is plain ASCII ({@code AnswerTest}). Three lines of the user's pictures are changed on
 * purpose (contract 10.3): "Memory pause" (190 px) is "Memory stall", and a disconnect, lost packets and a delivery
 * gap say "Line or world", because the client cannot tell which end failed.
 */
public final class Answer
{
	/** The headline of the card state "Not logged in" (contract 5.1). The verdict engine writes it FROM HERE. */
	public static final String HEAD_NOT_LOGGED_IN = "Not logged in";
	/** The headline of the card's warm-up state (contract 5.1); also {@code NoData.WARMING_UP}'s word. */
	public static final String HEAD_MEASURING = "Still measuring";
	/** The headline of the card state "Waiting for the game": frames stopped while logged in (contract 5.1). */
	public static final String HEAD_WAITING = "Waiting for the game";

	/** All is well: V2, and the pair of the group NONE. */
	public static final Answer SMOOTH = new Answer("Smooth", "", Icon.NONE);
	/** Warming up, a null verdict, and a WARMING_UP verdict whose headline is neither of the other two states'. */
	public static final Answer MEASURING = new Answer("Measuring", "", Icon.NONE);
	/** Not logged in. */
	public static final Answer NOT_LOGGED_IN = new Answer("Not logged in", "", Icon.NONE);
	/** Frames stopped while logged in: waiting for the game. */
	public static final Answer WAITING = new Answer("Waiting", "", Icon.NONE);

	private static final Answer WORLD_LAG = new Answer("World lag", "Not you", Icon.WORLD);
	private static final Answer PING_LAG = new Answer("Ping lag", "Your internet", Icon.LINE);
	private static final Answer HIGH_PING = new Answer("High ping", "Your internet", Icon.LINE);
	private static final Answer PACKET_LOSS = new Answer("Packet loss", "Line or world", Icon.LINE);
	private static final Answer DISCONNECTED = new Answer("Disconnected", "Line or world", Icon.LINE);
	private static final Answer NO_TICKS = new Answer("No ticks", "Line or world", Icon.UNKNOWN);
	private static final Answer LOW_FPS = new Answer("Low FPS", "Your PC", Icon.PC);
	private static final Answer FPS_CAPPED = new Answer("FPS capped", "Your setting", Icon.PC);
	private static final Answer CLIENT_FROZE = new Answer("Client froze", "A plugin?", Icon.PC);
	private static final Answer CLIENT_WAITED = new Answer("Client waited", "An overlay?", Icon.PC);
	private static final Answer MAP_LOADING = new Answer("Map loading", "Just the map", Icon.PC);
	private static final Answer MEMORY_STALL = new Answer("Memory stall", "The client", Icon.MEMORY);
	private static final Answer LOW_MEMORY = new Answer("Low memory", "Your setting", Icon.MEMORY);
	private static final Answer LAG = new Answer("Lag", "Can't tell why", Icon.UNKNOWN);

	/** The answer of every cause, by ordinal; built once, after the constants above. */
	private static final Answer[] BY_CAUSE = byCause();

	/** The cause, or the state; never "". */
	public final String line1;
	/** Whose side it is; "" = the answer has one line. */
	public final String line2;
	/** The badge's picture; {@link Icon#NONE} = the status shape alone. */
	public final Icon icon;
	/** "World lag - not you": line 2's first letter in lower case; {@code line1} alone when {@code line2} is "". */
	public final String oneLine;

	private Answer(String line1, String line2, Icon icon)
	{
		this.line1 = line1;
		this.line2 = line2;
		this.icon = icon;
		this.oneLine = line2.isEmpty()
			? line1
			: line1 + " - " + Character.toLowerCase(line2.charAt(0)) + line2.substring(1);
	}

	/**
	 * The answer of a verdict; never null, allocates nothing. A null verdict answers {@link #MEASURING}. A verdict
	 * whose cause is {@link Cause#WARMING_UP} is told apart by its headline: {@link #HEAD_NOT_LOGGED_IN} answers
	 * {@link #NOT_LOGGED_IN}, {@link #HEAD_WAITING} answers {@link #WAITING}, anything else {@link #MEASURING}. Every
	 * other verdict answers {@link #of(Cause)} of its cause.
	 */
	public static Answer of(Verdict v)
	{
		if (v == null)
		{
			return MEASURING;
		}
		if (v.cause == Cause.WARMING_UP)
		{
			if (HEAD_NOT_LOGGED_IN.equals(v.headline))
			{
				return NOT_LOGGED_IN;
			}
			if (HEAD_WAITING.equals(v.headline))
			{
				return WAITING;
			}
			return MEASURING;
		}
		return of(v.cause);
	}

	/**
	 * The answer of a cause, by the table in the class notes; never null, allocates nothing. A cause outside wave one
	 * answers its group's pair. {@link Cause#WARMING_UP}, which has no headline here, answers {@link #MEASURING}, as
	 * a WARMING_UP verdict with neither state's headline does; a null cause answers {@link #MEASURING} too.
	 */
	public static Answer of(Cause c)
	{
		return c == null ? MEASURING : BY_CAUSE[c.ordinal()];
	}

	private static Answer[] byCause()
	{
		final Cause[] causes = Cause.values();
		final Answer[] out = new Answer[causes.length];
		for (Cause c : causes)
		{
			out[c.ordinal()] = c.inWaveOne() ? ofWaveOne(c) : ofGroup(c.group());
		}
		return out;
	}

	private static Answer ofWaveOne(Cause c)
	{
		switch (c)
		{
			case ALL_CLEAR:
				return SMOOTH;
			case SLOW_WORLD:
				return WORLD_LAG;
			case PING_JUMPY:
				return PING_LAG;
			case PING_HIGH:
				return HIGH_PING;
			case UPLOAD_LOSS:
				return PACKET_LOSS;
			case DISCONNECT:
				return DISCONNECTED;
			case DELIVERY_GAP:
				return NO_TICKS;
			case SLOW_DRAWING:
				return LOW_FPS;
			case FRAME_CAP:
				return FPS_CAPPED;
			case CLIENT_BUSY:
				return CLIENT_FROZE;
			case CLIENT_WAITING:
				return CLIENT_WAITED;
			case MAP_LOAD:
				return MAP_LOADING;
			case GC_PAUSE:
				return MEMORY_STALL;
			case HEAP_CAP_LOW:
				return LOW_MEMORY;
			case NOT_SURE:
				return LAG;
			case WARMING_UP:
				return MEASURING;
			default:
				return ofGroup(c.group());
		}
	}

	/** The pair of a group, for a cause outside wave one. */
	private static Answer ofGroup(Group g)
	{
		switch (g)
		{
			case CONNECTION:
				return PING_LAG;
			case FRAME_RATE:
				return LOW_FPS;
			case MEMORY:
				return MEMORY_STALL;
			case WORLD:
				return WORLD_LAG;
			case UNSURE:
				return LAG;
			default:
				return SMOOTH;
		}
	}
}
