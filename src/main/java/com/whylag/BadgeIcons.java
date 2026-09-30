package com.whylag;

import com.whylag.core.Icon;
import com.whylag.core.Level;
import java.awt.image.BufferedImage;
import java.util.function.Function;
import net.runelite.client.util.ImageUtil;

/**
 * The game badge's eight pictures (contract P2.7): 24 x 24, one per cause and state, {@code badge-<icon>-<state>.png}
 * with icon {@code world}, {@code line}, {@code pc}, {@code unknown} ({@link Icon#file()}) and state
 * {@code warn} or {@code bad}. They are our own pixel art, made by {@code docs/handoff/lab/tools/whylag_icons.py} from
 * set A of {@code game-icons.js} and committed under {@code src/main/resources/com/whylag/}; nothing is downloaded.
 * The status shape of the state is already in each file's bottom right corner, and every icon but the globe is
 * tinted by the state.
 *
 * <p>All eight are loaded ONCE, in the constructor ({@code WhyLagPlugin.startUp}), with
 * {@link ImageUtil#loadImageResource}; {@link #get} only looks one up, so the overlay's {@code render} never loads
 * a picture (T20). OK and NO_DATA have no picture: those states draw a status shape alone.
 *
 * <p>Choice: fileName answers null where there is no file: Icon.NONE, OK, NO_DATA, or a null argument.
 * <p>Choice: a file that fails to load stays null, so get answers null and the painter draws the status shape.
 * <p>Choice: a package-private constructor takes the loader, so a test can count the eight loads.
 */
public final class BadgeIcons
{
	/** The two states that have a picture, in the order of the {@code pictures} slots. */
	private static final Level[] STATES = {Level.WARN, Level.BAD};

	/** One slot per icon and state: {@code icon.ordinal() * 2 + (BAD ? 1 : 0)}; NONE's two stay null. */
	private final BufferedImage[] pictures = new BufferedImage[Icon.values().length * STATES.length];

	/** Loads the eight pictures from this class's package with {@link ImageUtil#loadImageResource}. */
	public BadgeIcons()
	{
		this(name -> ImageUtil.loadImageResource(BadgeIcons.class, name));
	}

	/** Loads the eight pictures with {@code loader}, each file once; a load that throws leaves its slot null. */
	BadgeIcons(Function<String, BufferedImage> loader)
	{
		for (Icon icon : Icon.values())
		{
			for (Level level : STATES)
			{
				final String name = fileName(icon, level);
				if (name == null)
				{
					continue;
				}
				try
				{
					pictures[slot(icon, level)] = loader.apply(name);
				}
				catch (RuntimeException e)
				{
					pictures[slot(icon, level)] = null;
				}
			}
		}
	}

	/**
	 * The 24 x 24 picture of a cause in a state, the same object on every call; null for {@link Icon#NONE}, for OK
	 * and NO_DATA (a shape alone), for a null, and for a file that did not load.
	 */
	public BufferedImage get(Icon icon, Level level)
	{
		return fileName(icon, level) == null ? null : pictures[slot(icon, level)];
	}

	/**
	 * The file of a cause in a state, "badge-world-bad.png"; null when there is none: {@link Icon#NONE}, the levels
	 * OK and NO_DATA, or a null argument.
	 */
	public static String fileName(Icon icon, Level level)
	{
		if (icon == null || icon == Icon.NONE || (level != Level.WARN && level != Level.BAD))
		{
			return null;
		}
		return "badge-" + icon.file() + "-" + (level == Level.WARN ? "warn" : "bad") + ".png";
	}

	private static int slot(Icon icon, Level level)
	{
		return icon.ordinal() * STATES.length + (level == Level.BAD ? 1 : 0);
	}
}
