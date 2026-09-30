package com.whylag.ui;

import com.whylag.Version;
import com.whylag.core.BadgeSettings;
import com.whylag.core.BadgeStyle;
import com.whylag.core.WhenSmooth;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.MenuElement;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;
import static com.whylag.ui.PanelFixtures.StubActions;
import static com.whylag.ui.PanelFixtures.edt;
import static com.whylag.ui.PanelFixtures.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The gear menu (1.0.1, lot C, C2 and C6 item 2): from a snapshot's settings the items come in the order of the plan
 * with those ticks; each tick calls the matching {@code PanelActions} method with the new value; the version row is
 * last, disabled, and reads "2h Why Lag " and the version; and "Troubleshoot..." hands the panel's last snapshot to
 * {@code testAndReport}.
 */
public class GearMenuTest
{
	/** Show on, Shape only, hidden while smooth, chat line off: nothing the config defaults to. */
	private static final BadgeSettings ODD = new BadgeSettings(true, BadgeStyle.SHAPE_ONLY, WhenSmooth.HIDE, false);
	/** The opposite of {@link #ODD} in every ticked thing. */
	private static final BadgeSettings OTHER = new BadgeSettings(false, BadgeStyle.ICON_AND_WORDS, WhenSmooth.SHOW,
		true);

	private static JPopupMenu build(BadgeSettings s, StubActions actions, Runnable troubleshoot, Runnable closed)
	{
		return onEdt(() -> GearMenu.build(s, actions, troubleshoot, closed));
	}

	private static JPopupMenu build(BadgeSettings s, StubActions actions)
	{
		return build(s, actions, () -> { }, () -> { });
	}

	/** The items come in the order of C2, with the snapshot's ticks. */
	@Test
	public void theItemsComeInTheOrderOfThePlanWithTheSnapshotsTicks()
	{
		final JPopupMenu menu = build(ODD, new StubActions());
		final Component[] parts = menu.getComponents();
		assertEquals("four settings, a rule, Troubleshoot, a rule, the version row", 8, parts.length);

		assertEquals("Badge on the game screen", ((JCheckBoxMenuItem) parts[0]).getText());
		assertTrue("show is on", ((JCheckBoxMenuItem) parts[0]).isSelected());

		final JMenu style = (JMenu) parts[1];
		assertEquals("Badge style", style.getText());
		assertEquals(Arrays.asList("Icon", "Icon and words", "Shape and words", "Shape only"), labels(style));
		assertEquals("only the current style is ticked", Arrays.asList(false, false, false, true), ticks(style));

		assertEquals("Show badge when smooth", ((JCheckBoxMenuItem) parts[2]).getText());
		assertFalse("the choice is Hide: not ticked", ((JCheckBoxMenuItem) parts[2]).isSelected());

		assertEquals("Chat line on a lag", ((JCheckBoxMenuItem) parts[3]).getText());
		assertFalse("the chat line is off", ((JCheckBoxMenuItem) parts[3]).isSelected());

		assertTrue(parts[4] instanceof JPopupMenu.Separator);
		assertEquals("Troubleshoot...", ((JMenuItem) parts[5]).getText());
		assertFalse("a plain item, not a box", parts[5] instanceof JCheckBoxMenuItem);
		assertTrue(parts[6] instanceof JPopupMenu.Separator);
		assertTrue("the version row", parts[7] instanceof JPanel);
	}

	/** The same menu from the opposite settings ticks the opposite boxes: nothing is remembered between two opens. */
	@Test
	public void theTicksAreTheSnapshotsAndNothingIsRemembered()
	{
		final StubActions actions = new StubActions();
		final JPopupMenu odd = build(ODD, actions);
		final JPopupMenu other = build(OTHER, actions);
		final Component[] parts = other.getComponents();
		assertFalse(((JCheckBoxMenuItem) parts[0]).isSelected());
		assertEquals(Arrays.asList(false, true, false, false), ticks((JMenu) parts[1]));
		assertTrue("Show is ticked when the choice is Show", ((JCheckBoxMenuItem) parts[2]).isSelected());
		assertTrue(((JCheckBoxMenuItem) parts[3]).isSelected());
		assertTrue("the first menu is as it was built", ((JCheckBoxMenuItem) odd.getComponent(0)).isSelected());
		assertTrue(actions.written.isEmpty());

		for (BadgeStyle s : BadgeStyle.values())
		{
			final JPopupMenu one = build(new BadgeSettings(true, s, WhenSmooth.SHOW, true), actions);
			final List<Boolean> ticks = ticks((JMenu) one.getComponent(1));
			assertEquals(s.name(), 1, ticks.stream().filter(t -> t).count());
			assertTrue(s.name(), ticks.get(s.ordinal()));
		}
	}

	/** Each tick calls the matching action with the NEW value, once, and nothing else. */
	@Test
	public void eachTickCallsTheMatchingActionWithTheNewValue()
	{
		final StubActions actions = new StubActions();
		final JPopupMenu menu = build(ODD, actions);
		final Component[] parts = menu.getComponents();

		edt(((JCheckBoxMenuItem) parts[0])::doClick);
		assertEquals(Arrays.asList("badgeShow=false"), actions.written);

		edt(((JRadioButtonMenuItem) ((JMenu) parts[1]).getMenuComponent(0))::doClick);
		assertEquals("Icon is the first of the four", "badgeStyle=ICON", actions.written.get(1));
		edt(((JRadioButtonMenuItem) ((JMenu) parts[1]).getMenuComponent(2))::doClick);
		assertEquals("badgeStyle=SHAPE_AND_WORDS", actions.written.get(2));

		edt(((JCheckBoxMenuItem) parts[2])::doClick);
		assertEquals("unticked Hide becomes ticked Show", "badgeWhenSmooth=SHOW", actions.written.get(3));

		edt(((JCheckBoxMenuItem) parts[3])::doClick);
		assertEquals("badgeChatLine=true", actions.written.get(4));
		assertEquals("one write per tick", 5, actions.written.size());

		final StubActions back = new StubActions();
		final Component[] other = build(OTHER, back).getComponents();
		edt(((JCheckBoxMenuItem) other[0])::doClick);
		edt(((JCheckBoxMenuItem) other[2])::doClick);
		edt(((JCheckBoxMenuItem) other[3])::doClick);
		assertEquals(Arrays.asList("badgeShow=true", "badgeWhenSmooth=HIDE", "badgeChatLine=false"), back.written);
		assertTrue("a tick never asks for a report", back.reported.isEmpty() && actions.reported.isEmpty());
	}

	/** Every style label writes its own constant's NAME (what RuneLite stores), whatever the label reads. */
	@Test
	public void everyStyleChoiceWritesItsConstant()
	{
		final StubActions actions = new StubActions();
		final JMenu style = (JMenu) build(ODD, actions).getComponent(1);
		for (int i = 0; i < BadgeStyle.values().length; i++)
		{
			edt(((JRadioButtonMenuItem) style.getMenuComponent(i))::doClick);
		}
		assertEquals(Arrays.asList("badgeStyle=ICON", "badgeStyle=ICON_AND_WORDS", "badgeStyle=SHAPE_AND_WORDS",
			"badgeStyle=SHAPE_ONLY"), actions.written);
	}

	/** The version row is last, disabled, in grey, with no hover and no click, reading "2h Why Lag " and the version. */
	@Test
	public void theVersionRowIsLastDisabledAndReadsTheVersion()
	{
		final JPopupMenu menu = build(ODD, new StubActions());
		final Component last = menu.getComponent(menu.getComponentCount() - 1);
		assertFalse("disabled", last.isEnabled());
		assertFalse("not a menu element: nothing highlights under the mouse", last instanceof MenuElement);
		assertEquals("no mouse listener: no click", 0, last.getMouseListeners().length);
		assertEquals(0, last.getMouseMotionListeners().length);
		assertNull("no tooltip", ((JPanel) last).getToolTipText());

		final JLabel label = (JLabel) ((JPanel) last).getComponent(0);
		assertEquals("2h Why Lag " + Version.CURRENT, label.getText());
		assertEquals(GearMenu.VERSION, label.getText());
		assertEquals("small grey: the stock light grey", ColorScheme.LIGHT_GRAY_COLOR, label.getForeground());
		assertEquals(12, label.getFont().getSize());
		assertEquals(0, label.getMouseListeners().length);
		assertNull(label.getToolTipText());
	}

	/** Every item sets its face: one that does not is drawn by the look and feel in the 16 px bitmap font. */
	@Test
	public void everyItemSetsItsFaceAtTwelvePixels()
	{
		final JPopupMenu menu = build(ODD, new StubActions());
		int checked = 0;
		for (Component c : menu.getComponents())
		{
			if (c instanceof JMenuItem)
			{
				assertEquals(((JMenuItem) c).getText(), 12, c.getFont().getSize());
				checked++;
			}
		}
		final JMenu style = (JMenu) menu.getComponent(1);
		for (Component c : style.getMenuComponents())
		{
			assertEquals(((JMenuItem) c).getText(), 12, c.getFont().getSize());
			checked++;
		}
		assertEquals("five items in the menu, four in the submenu", 9, checked);
	}

	/** "Troubleshoot..." runs the panel's hook once; closing the menu, by any means, runs the closed hook. */
	@Test
	public void troubleshootRunsItsHookAndAClosingTellsTheCaller()
	{
		final int[] hook = new int[2];
		final JPopupMenu menu = build(ODD, new StubActions(), () -> hook[0]++, () -> hook[1]++);
		edt(((JMenuItem) menu.getComponent(5))::doClick);
		assertEquals(1, hook[0]);
		assertEquals("a choice alone does not say the menu closed: the popup does", 0, hook[1]);

		PanelFixtures.closePopup(menu);
		assertEquals(1, hook[1]);
		assertEquals(1, hook[0]);
	}

	/** Through the panel: "Troubleshoot..." calls {@code testAndReport} with the panel's last snapshot. */
	@Test
	public void troubleshootCallsTestAndReportWithTheLastSnapshot()
	{
		final StubActions actions = new StubActions();
		final WhyLagPanel p = PanelFixtures.panel(PanelFixtures.quiet(), false, false, false, actions);
		PanelFixtures.pressGear(p);
		final JPopupMenu menu = p.menu();
		assertNotNull(menu);
		assertTrue("nothing is asked of the actions by opening the menu", actions.reported.isEmpty());

		edt(((JMenuItem) menu.getComponent(5))::doClick);
		assertEquals(1, actions.reported.size());
		assertSame("the panel's last snapshot", p.last(), actions.reported.get(0));
		assertNotNull("the window opened", p.dialog());
		assertEquals("and holds the stub's report", StubActions.REPORT, p.dialog().reportArea.getText());

		edt(() -> p.show(PanelFixtures.lag().snapshot));
		PanelFixtures.pressGear(p);
		edt(((JMenuItem) p.menu().getComponent(5))::doClick);
		assertEquals(2, actions.reported.size());
		assertSame("a newer snapshot is the one handed over", p.last(), actions.reported.get(1));
		assertNotSame(actions.reported.get(0), actions.reported.get(1));
		edt(p::onDeactivate);
	}

	private static List<String> labels(JMenu menu)
	{
		final List<String> out = new ArrayList<>();
		for (Component c : menu.getMenuComponents())
		{
			out.add(((JMenuItem) c).getText());
		}
		return out;
	}

	private static List<Boolean> ticks(JMenu menu)
	{
		final List<Boolean> out = new ArrayList<>();
		for (Component c : menu.getMenuComponents())
		{
			out.add(((JRadioButtonMenuItem) c).isSelected());
		}
		return out;
	}
}
