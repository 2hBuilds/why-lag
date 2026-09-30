package com.whylag.ui;

import com.whylag.core.Answer;
import com.whylag.core.Cause;
import com.whylag.core.Fmt;
import com.whylag.core.Level;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.Verdict;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Block 2, the answer card (contract 5.1): the verdict in two big lines of {@link Answer}, its proof, its fix and
 * when it was, on a {@link Ui#CARD} ground with a 3 px edge in the level's colour ({@link Ui#RULE} at NO_DATA).
 *
 * <p>Top down: the level's shape, 14 x 14 at (10, 16); big line 1 in {@link Ui#RSB32} at x 30, baseline 32, in
 * the level's text colour; big line 2, white, 28 px under it; the proof in RuneScape, its first line's top 8 px
 * under the big block, wrapped on spaces to at most 3 lines of 196 px, 19 px apart, baseline 14 under each top;
 * with a fix, a 1 px {@link Ui#GROUND} divider 8 px under the proof, then 7 px lower the word "Fix" in bold orange,
 * a 4 px gap and the fix in white, at most 2 lines; with a when line, 6 px lower, the when words in RuneScape
 * Small on the left and the confidence word right-aligned at x 206 - on a line of its own under them when the two
 * do not fit in 196 px with 6 px between them. 9 px of padding above and below. The big lines are fixed words
 * that always fit ({@code AnswerCardTest} measures them); a 16 px line that does not fit ends in "...".
 *
 * <p>In a wider card (230 in the client) the ground fills the width, the proof and the fix wrap to {@code width - 17}
 * px (196 at 213), and the when line's confidence word ends 7 px from the right edge; the big lines and the shape
 * do not change. The wrapped lines, the height and the {@link #confidenceOwnLine} choice are made for the card's own
 * width ({@link Ui#widthOf}) and made again when that width changes.
 *
 * <p>The verdict's own headline is not painted: it is the card's tooltip, with {@code ruledOut} after it.
 *
 * <p>Choice: an empty proof takes no room, as the card before the first snapshot shows (46 px high).
 * <p>Choice: the world in the when words is printed as the plain number the verdict carries.
 * <p>Choice: a card that is asked for its height at a width it was not laid out for wraps its text again then, so
 * the panel's layout can size the card to its width first and read the height after.
 */
final class AnswerCard extends JComponent
{
	/** Padding above and below. */
	static final int PAD = 9;
	/** One big line of {@link Ui#RSB32}. */
	static final int BIG_LINE = 28;
	/** From the big block to the proof's first top. */
	static final int PROOF_GAP = 8;
	/** One 16 px line of the proof or the fix. */
	static final int LINE = 19;
	/** A 16 px line's baseline under its top. */
	static final int LINE_BASELINE = 14;
	/** From the proof's last line to the divider. */
	static final int DIVIDER_GAP = 8;
	/** From the divider to the fix's first top. */
	static final int FIX_GAP = 7;
	/** From the proof or the fix to the when line. */
	static final int WHEN_GAP = 6;
	/** One line of the when words. */
	static final int WHEN_LINE = 16;
	/** The when line's baseline under its top. */
	static final int WHEN_BASELINE = 12;
	/** Where the 16 px lines start, and the width they have at 213: x 10 .. 206. */
	static final int TEXT_X = 10;
	static final int ROOM = 196;
	/** What the lines' room is less than the card's width: 10 at the left, 7 at the right. */
	static final int ROOM_LESS = Ui.WIDTH - ROOM;
	/** Where the big lines start and end: x 30 .. 208, 178 px. */
	static final int BIG_X = 30;
	static final int BIG_END = 208;
	static final int LINE1_BASELINE = 32;
	static final int LINE2_BASELINE = 60;
	static final int SHAPE_X = 10;
	static final int SHAPE_Y = 16;
	static final int SHAPE_SIZE = 14;
	static final int EDGE = 3;
	static final int PROOF_LINES = 3;
	static final int FIX_LINES = 2;
	/** The space between the when words and the confidence word when they share a line. */
	static final int CONFIDENCE_GAP = 6;
	static final String FIX_WORD = "Fix";
	/** The fix's first line starts after "Fix" and a 4 px gap: 23 px in the real font. */
	static final int FIX_INDENT = Ui.width(Ui.RSB, FIX_WORD) + 4;

	private Level level = Level.NO_DATA;
	private String line1 = Answer.MEASURING.line1;
	private String line2 = "";
	private String proofText;
	private String fixText;
	private List<String> proof = Collections.emptyList();
	private List<String> fix = Collections.emptyList();
	/** The when words whole, and as cut to the room. */
	private String whenText = "";
	private String when = "";
	private String confidence = "";
	private boolean confidenceOwnLine;
	private String tip;
	private int height;
	/** The width the wrapped lines, {@link #when} and {@link #height} were made for; 0 = not yet. */
	private int laidWidth;

	AnswerCard()
	{
		setOpaque(true);
		ToolTipManager.sharedInstance().registerComponent(this);
		set(null, null);
	}

	/**
	 * The verdict to show and the snapshot it came with (for the clock, "4 min ago" and "this world"). A null
	 * verdict is the card before the first snapshot: "Measuring", the hollow ring, no proof. Answers true when
	 * the card's height changed.
	 */
	boolean set(Verdict v, PanelSnapshot s)
	{
		final Answer answer = Answer.of(v);
		level = v == null || v.level == null ? Level.NO_DATA : v.level;
		line1 = answer.line1;
		line2 = answer.line2;
		final int width = Ui.widthOf(this);
		final int room = width - ROOM_LESS;
		final boolean rewrap = width != laidWidth;
		final String p = v == null ? "" : v.proof;
		if (rewrap || !p.equals(proofText))
		{
			proofText = p;
			proof = Ui.wrap(Ui.RS, p, room, room, PROOF_LINES);
		}
		final String f = v == null ? "" : v.fix;
		if (rewrap || !f.equals(fixText))
		{
			fixText = f;
			fix = Ui.wrap(Ui.RS, f, room - FIX_INDENT, room, FIX_LINES);
		}
		whenText = whenWords(v, s);
		confidence = whenText.isEmpty() || v.confidence == null ? "" : v.confidence.word();
		confidenceOwnLine = ownLine(room);
		when = Ui.fit(Ui.RSS, whenText, room);
		tip = tipOf(v);
		laidWidth = width;
		final int old = height;
		height = measure();
		return height != old;
	}

	/** True when the when words and the confidence word do not fit one line of {@code room} px. */
	private boolean ownLine(int room)
	{
		return !confidence.isEmpty()
			&& Ui.width(Ui.RSS, whenText) + CONFIDENCE_GAP + Ui.width(Ui.RSS, confidence) > room;
	}

	/** Makes the wrapped lines, the when line and the height again when the card's width is not the one they are for. */
	private void refit()
	{
		final int width = Ui.widthOf(this);
		if (width == laidWidth)
		{
			return;
		}
		final int room = width - ROOM_LESS;
		proof = Ui.wrap(Ui.RS, proofText, room, room, PROOF_LINES);
		fix = Ui.wrap(Ui.RS, fixText, room - FIX_INDENT, room, FIX_LINES);
		confidenceOwnLine = ownLine(room);
		when = Ui.fit(Ui.RSS, whenText, room);
		laidWidth = width;
		height = measure();
	}

	/**
	 * The when words of contract 5.1, from the verdict's fields and the snapshot: an event's "21:47:30, world 416,
	 * 4 min ago"; all clear's "Last lag 21:47, this world" (or ", world 302"); a condition's "since 21:40"; "" when
	 * the verdict has no time ({@code whenWallMs} 0).
	 */
	static String whenWords(Verdict v, PanelSnapshot s)
	{
		if (v == null || v.whenWallMs == 0)
		{
			return "";
		}
		final ZoneId zone = s == null ? null : s.zone;
		if (v.eventId >= 0)
		{
			final long now = s == null ? v.whenWallMs : s.wallMs;
			return Fmt.clockSeconds(v.whenWallMs, zone) + ", world " + v.world + ", " + Fmt.ago(v.whenWallMs, now);
		}
		if (v.cause == Cause.ALL_CLEAR)
		{
			final boolean here = s != null && v.world == s.world;
			return "Last lag " + Fmt.clock(v.whenWallMs, zone) + (here ? ", this world" : ", world " + v.world);
		}
		return "since " + Fmt.clock(v.whenWallMs, zone);
	}

	/** The tooltip: the verdict's headline, then a space and {@code ruledOut} when there is one; null = none. */
	private static String tipOf(Verdict v)
	{
		if (v == null)
		{
			return null;
		}
		final String text = v.ruledOut.isEmpty() ? v.headline : (v.headline + " " + v.ruledOut).trim();
		return text.isEmpty() ? null : text;
	}

	private int measure()
	{
		int h = PAD + bigHeight();
		if (!proof.isEmpty())
		{
			h += PROOF_GAP + LINE * proof.size();
		}
		if (!fix.isEmpty())
		{
			h += DIVIDER_GAP + 1 + FIX_GAP + LINE * fix.size();
		}
		if (!when.isEmpty())
		{
			h += WHEN_GAP + WHEN_LINE * (confidenceOwnLine ? 2 : 1);
		}
		return h + PAD;
	}

	private int bigHeight()
	{
		return BIG_LINE * (line2.isEmpty() ? 1 : 2);
	}

	int height()
	{
		refit();
		return height;
	}

	Level level()
	{
		return level;
	}

	String line1()
	{
		return line1;
	}

	String line2()
	{
		return line2;
	}

	List<String> proofLines()
	{
		refit();
		return proof;
	}

	List<String> fixLines()
	{
		refit();
		return fix;
	}

	String when()
	{
		refit();
		return when;
	}

	String confidence()
	{
		return confidence;
	}

	boolean confidenceOwnLine()
	{
		refit();
		return confidenceOwnLine;
	}

	/** The divider's row, -1 when there is no fix. */
	int dividerY()
	{
		refit();
		return fix.isEmpty() ? -1 : PAD + bigHeight() + (proof.isEmpty() ? 0 : PROOF_GAP + LINE * proof.size())
			+ DIVIDER_GAP;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		return tip;
	}

	@Override
	public Dimension getPreferredSize()
	{
		refit();
		return new Dimension(Ui.WIDTH, height);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		refit();
		final int width = Ui.widthOf(this);
		final int room = width - ROOM_LESS;
		final Graphics2D g = Ui.prepare(graphics);
		g.setColor(Ui.CARD);
		g.fillRect(0, 0, width, height);
		g.setColor(Ui.edgeColour(level));
		g.fillRect(0, 0, EDGE, height);
		Shape.paint(g, level, SHAPE_X, SHAPE_Y, SHAPE_SIZE);
		Ui.text(g, line1, BIG_X, LINE1_BASELINE, Ui.RSB32, Ui.textColour(level));
		Ui.text(g, line2, BIG_X, LINE2_BASELINE, Ui.RSB32, Ui.WHITE);

		int top = PAD + bigHeight();
		if (!proof.isEmpty())
		{
			top += PROOF_GAP;
			for (String line : proof)
			{
				Ui.text(g, line, TEXT_X, top + LINE_BASELINE, Ui.RS, Ui.TEXT);
				top += LINE;
			}
		}
		if (!fix.isEmpty())
		{
			top += DIVIDER_GAP;
			g.setColor(Ui.GROUND);
			g.fillRect(TEXT_X, top, room, 1);
			top += 1 + FIX_GAP;
			Ui.text(g, FIX_WORD, TEXT_X, top + LINE_BASELINE, Ui.RSB, Ui.ORANGE);
			for (int i = 0; i < fix.size(); i++)
			{
				Ui.text(g, fix.get(i), i == 0 ? TEXT_X + FIX_INDENT : TEXT_X, top + LINE_BASELINE, Ui.RS, Ui.WHITE);
				top += LINE;
			}
		}
		if (!when.isEmpty())
		{
			top += WHEN_GAP;
			Ui.text(g, when, TEXT_X, top + WHEN_BASELINE, Ui.RSS, Ui.LABEL);
			final int confidenceTop = confidenceOwnLine ? top + WHEN_LINE : top;
			Ui.text(g, confidence, TEXT_X + room - Ui.width(Ui.RSS, confidence), confidenceTop + WHEN_BASELINE,
				Ui.RSS, Ui.LABEL);
		}
	}
}
