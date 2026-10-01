package com.whylag.ui;

import com.whylag.core.Answer;
import com.whylag.core.Cause;
import com.whylag.core.Level;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.Drawn;
import static com.whylag.ui.PanelFixtures.Fixture;
import static com.whylag.ui.PanelFixtures.is;
import static com.whylag.ui.PanelFixtures.panel;
import static com.whylag.ui.PanelFixtures.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The answer card (contract 5.1): the two big lines of {@link Answer} fit its 178 px in RuneScape Bold at 32 px,
 * the one derived font; line 1 in the level's colour and line 2 white; the heights 95, 73, 177 and 46; the divider
 * with a fix only; "Fix" in bold orange with no colon; the confidence word beside the when words or on its own line;
 * the when words of an event, a condition and all clear; the headline as the tooltip; the ring and one line of every
 * no-data state; and no wave-one verdict that needs "...".
 */
public class AnswerCardTest
{
	/** Every line of the answer table, measured in RSB32, is 178 px or less and is painted inside x 30 .. 208. */
	@Test
	public void everyBigLineFits()
	{
		final List<Answer> answers = new ArrayList<>();
		for (Cause c : Cause.values())
		{
			answers.add(Answer.of(c));
		}
		answers.add(Answer.SMOOTH);
		answers.add(Answer.MEASURING);
		answers.add(Answer.NOT_LOGGED_IN);
		answers.add(Answer.WAITING);
		int widest = 0;
		for (Answer a : answers)
		{
			for (String line : new String[] {a.line1, a.line2})
			{
				final int w = Ui.width(Ui.RSB32, line);
				assertTrue(line + " is " + w + " px", w <= 178);
				widest = Math.max(widest, w);
			}
		}
		assertEquals("the widest, 'Can't tell why', uses the whole box", 178, widest);

		for (Fixture f : PanelFixtures.answerRows())
		{
			final AnswerCard card = panel(f, false).card();
			final Answer answer = Answer.of(f.snapshot.verdict);
			int big = 0;
			for (Drawn d : record(card))
			{
				if (d.font.equals(Ui.RSB32))
				{
					big++;
					assertEquals(f + ": " + d, 30, d.x);
					assertTrue(f + ": " + d, d.right() <= 208);
				}
			}
			assertEquals(f.name, answer.line2.isEmpty() ? 1 : 2, big);

			// By pixel: in the rows of the big block nothing but the ground lies between the shape and x 30, or
			// right of x 207.
			final BufferedImage img = PanelFixtures.paintUnclipped(card, 260);
			final int bottom = answer.line2.isEmpty() ? 36 : 64;
			for (int y = 4; y < bottom; y++)
			{
				for (int x = 24; x < 30; x++)
				{
					assertTrue(f + " at " + x + "," + y, is(img, x, y, Ui.CARD));
				}
				for (int x = 208; x < 213; x++)
				{
					assertTrue(f + " at " + x + "," + y, is(img, x, y, Ui.CARD));
				}
			}
		}
	}

	/**
	 * RSB32 is the bold face at 32, derived from FontManager's. (That no other font is derived in ui is a source scan:
	 * the probe's {@code AnswerCardSourceTest}.)
	 */
	@Test
	public void theBigFontIsTheBoldFaceAtThirtyTwo()
	{
		final Font bold = FontManager.getRunescapeBoldFont();
		assertEquals(32f, Ui.RSB32.getSize2D(), 0f);
		assertEquals(bold.getFamily(), Ui.RSB32.getFamily());
		assertEquals(bold.getName(), Ui.RSB32.getName());
		assertTrue(Ui.RSB32.isBold());
		assertEquals(bold.deriveFont(32f), Ui.RSB32);
		final FontMetrics fm = Ui.metrics(Ui.RSB32);
		assertEquals("measured: ascent 28", 28, fm.getAscent());
		assertEquals("measured: descent 4", 4, fm.getDescent());
		assertSame("the 16 px faces are FontManager's own", FontManager.getRunescapeFont(), Ui.RS);
		assertSame(FontManager.getRunescapeBoldFont(), Ui.RSB);
		assertSame(FontManager.getRunescapeSmallFont(), Ui.RSS);
	}

	@Test
	public void lineOneTakesTheLevelsColourAndLineTwoIsWhite()
	{
		assertBigColours("answer-v2", Ui.OK);
		assertBigColours("answer-w1", new Color(255, 90, 90));
		assertBigColours("answer-n1", Ui.WARN);
		assertBigColours("not-logged-in", Ui.LABEL);
		assertBigColours("answer-waiting", Ui.LABEL);
		assertEquals("red text is never the stock red", new Color(255, 90, 90), Ui.BAD_TEXT);
	}

	private static void assertBigColours(String fixture, Color line1)
	{
		final List<Drawn> big = big(record(panel(PanelFixtures.named(fixture), false).card()));
		assertEquals(fixture, line1.getRGB(), big.get(0).colour.getRGB());
		assertEquals(fixture, 32, big.get(0).y);
		if (big.size() > 1)
		{
			assertEquals(fixture, Ui.WHITE.getRGB(), big.get(1).colour.getRGB());
			assertEquals(fixture + ": 28 px under line 1", 60, big.get(1).y);
		}
	}

	@Test
	public void smoothHasOneBigLine()
	{
		final AnswerCard card = panel(PanelFixtures.quiet(), false).card();
		assertEquals("Smooth", card.line1());
		assertEquals("", card.line2());
		final List<Drawn> big = big(record(card));
		assertEquals(1, big.size());
		assertEquals("Smooth", big.get(0).text);
		assertEquals(Ui.OK.getRGB(), big.get(0).colour.getRGB());
	}

	/** No wave-one verdict - each cause's longest proof and fix, with the largest values - needs "...". */
	@Test
	public void noWaveOneVerdictNeedsAnEllipsis()
	{
		for (Fixture f : PanelFixtures.all())
		{
			final AnswerCard card = panel(f, false).card();
			for (Drawn d : record(card))
			{
				assertFalse(f + ": " + d, d.text.endsWith("..."));
			}
			assertTrue(f.name, card.proofLines().size() <= 3);
			assertTrue(f.name, card.fixLines().size() <= 2);
		}
		for (Fixture f : PanelFixtures.answerRows())
		{
			final AnswerCard card = panel(f, false).card();
			assertEquals(f.name, f.snapshot.verdict.proof, String.join(" ", card.proofLines()));
			assertEquals(f.name, f.snapshot.verdict.fix, String.join(" ", card.fixLines()));
		}
	}

	/** A big line is drawn whole, as ONE string, however long the verdict's other words are. */
	@Test
	public void aBigLineNeverWraps()
	{
		for (Fixture f : PanelFixtures.answerRows())
		{
			final Answer answer = Answer.of(f.snapshot.verdict);
			final List<Drawn> big = big(record(panel(f, false).card()));
			assertEquals(f.name, answer.line1, big.get(0).text);
			if (!answer.line2.isEmpty())
			{
				assertEquals(f.name, answer.line2, big.get(1).text);
			}
			assertEquals(f.name, answer.line2.isEmpty() ? 1 : 2, big.size());
		}
	}

	/** 5.1's heights: quiet 95, no lag this session 73, the picture's lag 177, before the first snapshot 46. */
	@Test
	public void heightFollowsTheLines()
	{
		assertEquals(95, panel(PanelFixtures.quiet(), false).card().height());
		assertEquals(73, panel(PanelFixtures.clearNone(), false).card().height());
		assertEquals(177, panel(PanelFixtures.lag(), false).card().height());
		assertEquals(46, PanelFixtures.onEdt(AnswerCard::new).height());
		assertEquals(73, panel(PanelFixtures.notLoggedIn(), false).card().height());
		assertEquals("the longest when line puts the confidence on a line of its own", 177 + 16,
			panel(PanelFixtures.longestWhen(), false).card().height());
		for (Fixture f : PanelFixtures.all())
		{
			final AnswerCard card = panel(f, false).card();
			final int big = card.line2().isEmpty() ? 28 : 56;
			final int proof = card.proofLines().isEmpty() ? 0 : 8 + 19 * card.proofLines().size();
			final int fix = card.fixLines().isEmpty() ? 0 : 8 + 1 + 7 + 19 * card.fixLines().size();
			final int when = card.when().isEmpty() ? 0 : 6 + 16 * (card.confidenceOwnLine() ? 2 : 1);
			assertEquals(f.name, 9 + big + proof + fix + when + 9, card.height());
			assertEquals(f.name, card.height(), card.getPreferredSize().height);
		}
	}

	@Test
	public void theDividerStandsOnlyWithAFix()
	{
		final AnswerCard lag = panel(PanelFixtures.lag(), false).card();
		final int y = lag.dividerY();
		assertEquals("8 px under the two-line proof", 9 + 56 + 8 + 38 + 8, y);
		final BufferedImage img = PanelFixtures.paint(lag);
		for (int x = 10; x < 206; x++)
		{
			assertTrue("divider at " + x, is(img, x, y, Ui.GROUND));
		}
		assertTrue("the divider starts at x 10", is(img, 9, y, Ui.CARD));
		assertTrue("and ends before x 206", is(img, 206, y, Ui.CARD));

		for (String name : new String[] {"quiet", "clear-none", "not-logged-in", "warming-up"})
		{
			final AnswerCard card = panel(PanelFixtures.named(name), false).card();
			assertEquals(name, -1, card.dividerY());
			final BufferedImage quiet = PanelFixtures.paint(card);
			for (int row = 0; row < card.height(); row++)
			{
				assertFalse(name + ": a divider at row " + row, is(quiet, 100, row, Ui.GROUND)
					&& is(quiet, 11, row, Ui.GROUND));
			}
		}
	}

	@Test
	public void fixIsOrangeAndBold()
	{
		final List<Drawn> drawn = record(panel(PanelFixtures.lag(), false).card());
		Drawn fixWord = null;
		Drawn fixText = null;
		for (Drawn d : drawn)
		{
			assertFalse("no colon: " + d, d.text.startsWith("Fix:"));
			if (d.text.equals("Fix"))
			{
				fixWord = d;
			}
			if (d.text.equals("Hop to a quieter world."))
			{
				fixText = d;
			}
		}
		assertNotNull(fixWord);
		assertNotNull(fixText);
		assertEquals(Ui.RSB, fixWord.font);
		assertEquals(Ui.ORANGE.getRGB(), fixWord.colour.getRGB());
		assertEquals(10, fixWord.x);
		assertEquals(Ui.RS, fixText.font);
		assertEquals(Ui.WHITE.getRGB(), fixText.colour.getRGB());
		assertEquals("'Fix' and a 4 px gap: 23 px", 33, fixText.x);
		assertEquals(fixWord.y, fixText.y);
		assertEquals("7 px under the divider, baseline 14", AnswerCard.PAD + 56 + 8 + 38 + 8 + 1 + 7 + 14, fixWord.y);
	}

	@Test
	public void confidenceIsShown()
	{
		final List<Drawn> drawn = record(panel(PanelFixtures.lag(), false).card());
		final Drawn word = find(drawn, "Likely");
		assertEquals(Ui.RSS, word.font);
		assertEquals(Ui.LABEL.getRGB(), word.colour.getRGB());
		assertEquals("right-aligned at x 206", 206, word.right());
		assertEquals("Hint", panel(PanelFixtures.named("answer-s3"), false).card().confidence());
		assertEquals("Sure", panel(PanelFixtures.quiet(), false).card().confidence());
	}

	/** "23:59:59, world 999, 59 min ago" (163 px) and "Can't tell" (46 px) do not fit 196 px with 6 between. */
	@Test
	public void confidenceMovesToItsOwnLineWhenItDoesNotFit()
	{
		final AnswerCard card = panel(PanelFixtures.longestWhen(), false).card();
		assertEquals("23:59:59, world 999, 59 min ago", card.when());
		assertEquals(163, Ui.width(Ui.RSS, card.when()));
		assertEquals(46, Ui.width(Ui.RSS, "Can't tell"));
		assertTrue(card.confidenceOwnLine());
		final List<Drawn> drawn = record(card);
		final Drawn when = find(drawn, card.when());
		final Drawn word = find(drawn, "Can't tell");
		assertEquals("its own 16 px line, under the when words", when.y + 16, word.y);
		assertEquals(206, word.right());
		assertEquals(10, when.x);
	}

	@Test
	public void confidenceSharesTheLineWhenItFits()
	{
		final AnswerCard card = panel(PanelFixtures.lag(), false).card();
		assertFalse(card.confidenceOwnLine());
		final List<Drawn> drawn = record(card);
		final Drawn when = find(drawn, card.when());
		final Drawn word = find(drawn, "Likely");
		assertEquals(when.y, word.y);
		assertTrue("6 px or more apart", when.right() + 6 <= word.x);
		assertEquals("the last line: 12 above the bottom padding", card.height() - 9 - 16 + 12, when.y);
	}

	@Test
	public void whenLineWords()
	{
		assertEquals("21:47:30, world 416, 4 min ago", panel(PanelFixtures.lag(), false).card().when());
		assertEquals("since 21:40", panel(PanelFixtures.condition(), false).card().when());
		assertEquals("Last lag 21:47, this world", panel(PanelFixtures.clearHere(), false).card().when());
		assertEquals("Last lag 21:47, world 302", panel(PanelFixtures.clearElsewhere(), false).card().when());

		final AnswerCard none = panel(PanelFixtures.clearNone(), false).card();
		assertEquals("", none.when());
		assertEquals("no when line, no confidence word", "", none.confidence());
		for (Drawn d : record(none))
		{
			assertFalse(d.toString(), d.text.equals("Sure"));
			assertFalse(d.toString(), d.font.equals(Ui.RSS));
		}
		for (String state : new String[] {"not-logged-in", "warming-up", "waiting"})
		{
			assertEquals(state, "", panel(PanelFixtures.named(state), false).card().when());
			assertEquals(state, "", panel(PanelFixtures.named(state), false).card().confidence());
		}
		final List<Drawn> drawn = record(panel(PanelFixtures.condition(), false).card());
		final Drawn since = find(drawn, "since 21:40");
		assertEquals(Ui.RSS, since.font);
		assertEquals(Ui.LABEL.getRGB(), since.colour.getRGB());
		assertEquals(10, since.x);
	}

	/** The verdict's own headline is not painted: it is the tooltip, with ruledOut after a space. */
	@Test
	public void theTooltipIsTheHeadline()
	{
		final AnswerCard lag = panel(PanelFixtures.lag(), false).card();
		assertEquals("World 416 is struggling, not you Frames and ping were fine.", PanelFixtures.tip(lag, 100, 30));
		for (Drawn d : record(lag))
		{
			assertFalse("the headline is not painted", d.text.contains("struggling"));
		}
		assertEquals("no ruledOut, the headline alone", "Smooth",
			PanelFixtures.tip(panel(PanelFixtures.quiet(), false).card(), 100, 30));
		assertEquals("Still measuring", PanelFixtures.tip(panel(PanelFixtures.warmingUp(), false).card(), 5, 5));
		assertNull("before the first snapshot there is none",
			PanelFixtures.tip(PanelFixtures.onEdt(AnswerCard::new), 50, 20));
	}

	/** Not logged in, measuring, waiting and the card before the first snapshot: the hollow ring and ONE line. */
	@Test
	public void everyNoDataStateHasTheRingAndOneLine()
	{
		final List<AnswerCard> cards = new ArrayList<>();
		for (String name : new String[] {"not-logged-in", "warming-up", "waiting"})
		{
			cards.add(panel(PanelFixtures.named(name), false).card());
		}
		cards.add(PanelFixtures.onEdt(AnswerCard::new));
		final String[] line1 = {"Not logged in", "Measuring", "Waiting", "Measuring"};
		final String[] proof = {"Log in to start measuring.", "Ready in 40 s.", "No frames are being drawn.", ""};
		for (int i = 0; i < cards.size(); i++)
		{
			final AnswerCard card = cards.get(i);
			assertEquals(Level.NO_DATA, card.level());
			assertEquals(line1[i], card.line1());
			assertEquals("", card.line2());
			assertEquals(proof[i], String.join(" ", card.proofLines()));
			assertTrue(card.fixLines().isEmpty());
			assertEquals(-1, card.dividerY());
			assertEquals(proof[i].isEmpty() ? 46 : 73, card.height());
			final List<Drawn> big = big(record(card));
			assertEquals(1, big.size());
			assertEquals(Ui.LABEL.getRGB(), big.get(0).colour.getRGB());
			final BufferedImage img = PanelFixtures.paint(card);
			assertTrue("the edge is RULE", is(img, 1, 5, Ui.RULE));
			assertTrue("the ring's left side", is(img, 10, 16 + 7, Ui.LABEL));
			assertTrue("the ring's top", is(img, 10 + 7, 16, Ui.LABEL));
			assertTrue("the ring is hollow", is(img, 10 + 7, 16 + 7, Ui.CARD));
		}
	}

	// ------------------------------------------------------------------ helpers

	private static List<Drawn> big(List<Drawn> drawn)
	{
		final List<Drawn> out = new ArrayList<>();
		for (Drawn d : drawn)
		{
			if (d.font.equals(Ui.RSB32))
			{
				out.add(d);
			}
		}
		return out;
	}

	private static Drawn find(List<Drawn> drawn, String text)
	{
		for (Drawn d : drawn)
		{
			if (d.text.equals(text))
			{
				return d;
			}
		}
		fail("not drawn: " + text + " in " + drawn);
		return null;
	}
}
