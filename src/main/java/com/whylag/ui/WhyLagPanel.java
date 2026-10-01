package com.whylag.ui;

import com.whylag.GraphRange;
import com.whylag.PanelActions;
import com.whylag.PanelControl;
import com.whylag.Report;
import com.whylag.core.Cells;
import com.whylag.core.Fmt;
import com.whylag.core.LagEvent;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.ReportText;
import com.whylag.core.Verdict;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel of picture 18, "Big Answer" (contract 5): top down, the header with the settings gear at its right
 * end, the answer card, the three cells, the range row, the "Graphs" fold row with the three lanes behind it, the
 * "Lags (n)" fold row with the event list behind it, the session's total and its counts, and in developer mode a
 * footer under that. The gear opens a menu of the badge's four settings and "Troubleshoot...", which runs the ten
 * checks and shows the report in a window (1.0.1, lot C); the panel has no button, no verdict line and no version
 * row - the version is the menu's last row. Content width 213 px at the panel's own 225, and the
 * width of the sidebar less the border when it is wider (230 in the client), on the {@link Ui#GROUND} ground. Every
 * block is one custom-painted component, so a one-second update is a few field writes and one repaint of each
 * block.
 *
 * <p><b>Showing.</b> {@link #show} runs on the Swing thread. While the panel is hidden it keeps the snapshot and
 * paints nothing (T9); {@link #onActivate} asks the plugin for one snapshot at once. A show changes the layout only
 * when a block in it changed its height - the card's text, the list's rows, the counts' lines (T11) - and
 * repaints each block in the layout once.
 *
 * <p><b>The fold rows.</b> Both are folded when the panel is built; the state is two fields that last as long as
 * the panel. A folded block is not in the layout, so it is neither measured nor painted (T17); a press on a row
 * lays the panel out once.
 *
 * <p><b>A selected event.</b> A press on a list row selects its event: the card shows that event's verdict, the
 * cells and the lane values its numbers, and its band is drawn as selected. A second press on the row, a range
 * change or {@link #onDeactivate} clears it; a fold does not.
 *
 * <p>Choice: a column layout of the panel's own puts each block at the left border, as wide as the panel less its
 * border (213 at least), under its gap of contract 5.
 * <p>Choice: a show lays out only when a block in the layout changed its height; a fold press lays out once.
 * <p>Choice: {@link #onActivate} paints the kept snapshot at once, then asks the plugin for a fresh one.
 * <p>Choice: an id not among the snapshot's range events clears the selection, also when its event ages out.
 * <p>Choice: a selected event with no verdict (only a test builds one) leaves the card on the snapshot's verdict.
 * <p>Choice: a press on the chip of the range already shown does nothing.
 * <p>Choice: the gear does nothing before the first snapshot: the menu is built from the last snapshot's settings, and
 * the first one comes within a second of the panel showing ({@link #onActivate} asks for it at once).
 * <p>Choice: the menu is built FRESH at every open, so it ticks what the newest snapshot holds; a press on the gear
 * while the menu stands takes it down and opens nothing, and a press within {@code HeaderRow.MENU_REOPEN_MS} of its
 * closing opens nothing either (the header's rule). The menu opens under the gear with its right edge at the gear's.
 * <p>Choice: "Troubleshoot..." does nothing before the first snapshot, and answers "" then; it opens the window at
 * once on "Testing..." - or re-uses the one that stands, for a new test - and hands the last snapshot to the actions.
 * <p>Choice: a press also starts a one-shot Swing timer of {@value #TEST_TIMEOUT_MS} ms, stopped when the report
 * comes back and when the panel is hidden. When it fires first the sampler thread is hung, and the window is filled
 * with a FALLBACK report made here: {@link ReportText#of(PanelSnapshot, String)} of the kept last snapshot with a
 * Verdict and a Checks block that say the sampler did not answer, and {@value #NO_ANSWER} as its verdict. The
 * press's generation is then spent, so a report that comes later is ignored.
 * <p>Choice: the fallback is safe on the Swing thread because the snapshot is immutable and every string in it (the
 * minute log, the diagnostics text) was made on the sampler thread before it was handed over: the fallback only
 * joins them, and reads no ring, no client state and no lock the hung thread could hold.
 * <p>Choice: the report comes back on the Swing thread through {@code PanelActions.testAndReport}'s callback and
 * fills the window it was asked for, when that window still stands; nothing is copied until the player presses
 * "Copy report" in it.
 * <p>Choice: hiding the panel closes the window and stops the timeout: nobody is waiting for the report.
 * <p>Choice: {@link #troubleshoot()} answers the text only when the callback ran before it returned (a stub's); the
 * plugin's answer comes later, so it answers "".
 * <p>Choice: {@link #component()} is the panel itself.
 * <p>Choice: the developer-mode footer is a block of 213 x 14 (as wide as the panel gives it), RuneScape Small at
 * baseline 11, cut with "...".
 * <p>Choice: the session's counts are the last block of the panel, and the developer-mode footer sits under them
 * with its 6 px gap.
 */
public final class WhyLagPanel extends PluginPanel implements PanelControl
{
	/** The client property that holds a block's gap above it. */
	private static final String GAP = "whylag.gap";
	/** How long a press waits for the sampler's report before the panel copies its own fallback. */
	static final int TEST_TIMEOUT_MS = 3000;
	/** The fallback report's verdict, shown in the window when the fallback was made. */
	static final String NO_ANSWER = "Sampler did not answer in 3 s";
	/** The Verdict and Checks lines the fallback report carries in place of the checks, blank lines included. */
	private static final String NO_ANSWER_CHECKS = "Verdict: The plugin's sampler did not answer in 3 s.\n\n"
		+ "Checks: not run (the sampler thread did not answer; the notes and the last 60 minutes below are from the"
		+ " last snapshot)\n\n";

	private final PanelActions actions;
	private final HeaderRow header;
	private final AnswerCard card = new AnswerCard();
	private final CellStrip cells = new CellStrip();
	private final RangeRow rangeRow;
	private final FoldRow graphsRow;
	private final FoldRow lagsRow;
	private final StripChart strips = new StripChart();
	private final EventList list;
	private final SessionHeader sessionHeader = new SessionHeader();
	private final SessionCounts counts = new SessionCounts();
	private final Timer testTimeout;
	private final Footer footer;

	/** Where the report is copied to; true when the clipboard took it. A seam: tests hand in their own. */
	Predicate<String> clipboard = WhyLagPanel::toSystemClipboard;
	/**
	 * How the Troubleshoot window is opened, handed the panel's window and the clipboard: the real one, which needs a
	 * display. A seam: tests hand in a window-less one.
	 */
	BiFunction<Window, Predicate<String>, TroubleshootDialog> dialogOpener = TroubleshootDialog::open;

	private volatile int range;
	private volatile boolean graphsOpen;
	private volatile boolean lagsOpen;
	private volatile boolean active;
	private volatile int activations;
	private volatile int deactivations;
	/** The newest snapshot handed to {@link #show}; Swing thread only. Package-private: a test seam (PanelPeek). */
	PanelSnapshot last;
	/** The selected event's id, -1 = none; Swing thread only. */
	private long selectedId = -1;
	/** Counts presses and spent fallbacks: a report whose press is not the newest generation is stale; Swing thread only. */
	private int pressGeneration;
	/** The Troubleshoot window that stands, null when none does; Swing thread only. */
	private TroubleshootDialog dialog;
	/** The gear menu built at the last open, null before the first; Swing thread only. */
	private JPopupMenu menu;

	/**
	 * The panel. {@code initial} is the range its chips show first (null: 10 min); {@code developerMode} adds the
	 * footer.
	 */
	public WhyLagPanel(PanelActions actions, GraphRange initial, boolean developerMode)
	{
		super();
		this.actions = actions;
		range = (initial == null ? GraphRange.TEN_MIN : initial).minutes();
		rangeRow = new RangeRow(this::pressRange);
		graphsRow = new FoldRow("Graphs", false, () -> fold(!graphsOpen, lagsOpen));
		lagsRow = new FoldRow("Lags", true, () -> fold(graphsOpen, !lagsOpen));
		list = new EventList(this::rowPressed);
		header = new HeaderRow(this::openMenu);
		testTimeout = new Timer(TEST_TIMEOUT_MS, e -> samplerDidNotAnswer());
		testTimeout.setRepeats(false);
		footer = developerMode ? new Footer() : null;

		rangeRow.set(range);
		list.set(Collections.emptyList(), -1, range, null);
		setLayout(new Column());
		setBackground(Ui.GROUND);
		gap(header, 0);
		gap(card, 8);
		gap(cells, 8);
		gap(rangeRow, 12);
		gap(graphsRow, 4);
		gap(strips, 3);
		gap(lagsRow, 4);
		gap(list, 3);
		gap(sessionHeader, 12);
		gap(counts, 4);
		add(header);
		add(card);
		add(cells);
		add(rangeRow);
		add(graphsRow);
		add(lagsRow);
		add(sessionHeader);
		add(counts);
		if (footer != null)
		{
			gap(footer, 6);
			add(footer);
		}
	}

	private static void gap(JComponent block, int px)
	{
		block.putClientProperty(GAP, px);
	}

	/** Draws a snapshot. Swing thread only. While the panel is hidden it is kept, and nothing is painted. */
	public void show(PanelSnapshot s)
	{
		if (s == null)
		{
			return;
		}
		last = s;
		if (active)
		{
			apply(s);
		}
	}

	private void apply(PanelSnapshot s)
	{
		final LagEvent picked = picked(s);
		final Verdict shown = picked != null && picked.verdict != null ? picked.verdict : s.verdict;
		boolean layout = false;
		header.set(s.world);
		layout |= card.set(shown, s);
		cells.set(picked != null ? Cells.of(picked) : Cells.now(s));
		rangeRow.set(range);
		rangeRow.held(s.stretched() ? Fmt.held(s.rangeEndWallMs - s.rangeStartWallMs) : "");
		graphsRow.setOpen(graphsOpen);
		lagsRow.setOpen(lagsOpen);
		lagsRow.setCount(s.rangeEvents.size());
		strips.set(s, picked);
		layout |= list.set(s.rangeEvents, selectedId, s.rangeMinutes, s.zone) && lagsOpen;
		sessionHeader.set(s.sessionTotal);
		layout |= counts.set(s.sessionCounts);
		if (footer != null)
		{
			footer.set(s.footer);
		}
		if (layout)
		{
			revalidate();
		}
		repaintBlocks();
	}

	/** The selected event among the snapshot's range events; clears the selection when it is not there. */
	private LagEvent picked(PanelSnapshot s)
	{
		if (selectedId < 0)
		{
			return null;
		}
		final LagEvent e = inRange(s, selectedId);
		if (e == null)
		{
			selectedId = -1;
		}
		return e;
	}

	private static LagEvent inRange(PanelSnapshot s, long id)
	{
		if (s == null)
		{
			return null;
		}
		for (LagEvent e : s.rangeEvents)
		{
			if (e != null && !e.open && e.id == id)
			{
				return e;
			}
		}
		return null;
	}

	@Override
	public void onActivate()
	{
		active = true;
		activations++;
		if (last != null)
		{
			apply(last);
		}
		actions.activated();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
		deactivations++;
		selectedId = -1;
		testTimeout.stop();
		closeDialog();
	}

	// ------------------------------------------------------------------ what a press does

	private void pressRange(int minutes)
	{
		final int m = GraphRange.of(minutes).minutes();
		if (m == range)
		{
			return;
		}
		range = m;
		final boolean hadPick = selectedId >= 0;
		selectedId = -1;
		rangeRow.set(m);
		rangeRow.repaint();
		actions.rangeChanged(m);
		if (hadPick && active && last != null)
		{
			apply(last);
		}
	}

	private void rowPressed(long id)
	{
		select(id == selectedId ? -1 : id);
	}

	private void placeFoldables()
	{
		place(strips, graphsRow, graphsOpen);
		place(list, lagsRow, lagsOpen);
	}

	private void repaintBlocks()
	{
		for (Component c : getComponents())
		{
			c.repaint();
		}
	}

	/** Puts {@code block} right under {@code row} while it is open, and takes it out of the layout while folded. */
	private void place(JComponent block, JComponent row, boolean open)
	{
		final boolean in = block.getParent() == this;
		if (open && !in)
		{
			add(block, getComponentZOrder(row) + 1);
		}
		else if (!open && in)
		{
			remove(block);
		}
	}

	private static boolean toSystemClipboard(String text)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			return true;
		}
		catch (HeadlessException | IllegalStateException | SecurityException e)
		{
			return false;
		}
	}

	// ------------------------------------------------------------------ PanelControl

	@Override
	public void setRange(int minutes)
	{
		pressRange(minutes);
	}

	@Override
	public int range()
	{
		return range;
	}

	@Override
	public String copyReport()
	{
		return troubleshoot();
	}

	/**
	 * "Troubleshoot..." (Swing thread): the window opens on "Testing..." - or the one that stands is re-used for a new
	 * test - the timeout starts and the last snapshot goes to the actions. Does nothing, and answers "", before the
	 * first snapshot. Answers the report's text when the actions brought it back before this returned, else "".
	 */
	String troubleshoot()
	{
		final PanelSnapshot s = last;
		if (s == null)
		{
			return "";
		}
		final TroubleshootDialog standing = dialog;
		final TroubleshootDialog shown;
		if (standing != null && standing.isOpen())
		{
			standing.testing();
			standing.toFront();
			shown = standing;
		}
		else
		{
			shown = dialogOpener.apply(SwingUtilities.getWindowAncestor(this), text -> clipboard.test(text));
			dialog = shown;
		}
		final String[] atOnce = {""};
		final boolean[] pressing = {true};
		final int mine = ++pressGeneration;
		testTimeout.restart();
		actions.testAndReport(s, report ->
		{
			if (mine != pressGeneration)
			{
				return;
			}
			testTimeout.stop();
			final String text = answer(shown, report);
			if (pressing[0])
			{
				atOnce[0] = text;
			}
		});
		pressing[0] = false;
		return atOnce[0];
	}

	/**
	 * The sampler did not answer a press in {@link #TEST_TIMEOUT_MS} ms (Swing thread): the window is filled with a
	 * report made here from the last snapshot, with the Verdict and Checks lines of a test that was not run. The
	 * press's generation is spent first, so the real answer, if it ever comes, is ignored.
	 *
	 * <p>Choice: the snapshot is immutable and its strings were made on the sampler thread, so joining them here is
	 * safe on the Swing thread and cannot wait on the thread that is hung.
	 */
	private void samplerDidNotAnswer()
	{
		pressGeneration++;
		final PanelSnapshot s = last;
		final TroubleshootDialog d = dialog;
		if (d != null && d.isOpen())
		{
			d.show(new Report(s == null ? "" : ReportText.of(s, NO_ANSWER_CHECKS), NO_ANSWER));
		}
	}

	/** The report came back (Swing thread): it fills the window if that still stands. Answers the text, "" if none. */
	private String answer(TroubleshootDialog shown, Report report)
	{
		if (shown.isOpen())
		{
			shown.show(report);
		}
		return report == null ? "" : report.text;
	}

	/** Closes the Troubleshoot window, if one stands (Swing thread). */
	private void closeDialog()
	{
		final TroubleshootDialog d = dialog;
		dialog = null;
		if (d != null)
		{
			d.dispose();
		}
	}

	/**
	 * The gear was pressed (Swing thread): takes down a menu that still stands, else builds the menu fresh from the
	 * last snapshot and opens it under the gear, its right edge at the gear's. Does nothing before the first snapshot
	 * and, as a menu needs a screen to stand on, nothing more while the panel is not showing.
	 */
	private void openMenu()
	{
		final JPopupMenu standing = menu;
		if (standing != null && standing.isVisible())
		{
			standing.setVisible(false);
			return;
		}
		final PanelSnapshot s = last;
		if (s == null)
		{
			return;
		}
		final JPopupMenu fresh = GearMenu.build(s.badgeSettings, actions, () -> troubleshoot(), header::menuClosed);
		menu = fresh;
		if (header.isShowing())
		{
			final Rectangle gear = header.gearBounds();
			fresh.show(header, gear.x + gear.width - fresh.getPreferredSize().width, header.getHeight());
		}
	}

	@Override
	public void select(long eventId)
	{
		long id = eventId < 0 ? -1 : eventId;
		if (id >= 0 && inRange(last, id) == null)
		{
			id = -1;
		}
		if (id == selectedId)
		{
			return;
		}
		selectedId = id;
		if (active && last != null)
		{
			apply(last);
		}
	}

	@Override
	public void fold(boolean openGraphs, boolean openLags)
	{
		if (openGraphs == graphsOpen && openLags == lagsOpen)
		{
			return;
		}
		graphsOpen = openGraphs;
		lagsOpen = openLags;
		graphsRow.setOpen(openGraphs);
		lagsRow.setOpen(openLags);
		placeFoldables();
		revalidate();
		graphsRow.repaint();
		lagsRow.repaint();
	}

	@Override
	public boolean graphsOpen()
	{
		return graphsOpen;
	}

	@Override
	public boolean lagsOpen()
	{
		return lagsOpen;
	}

	@Override
	public boolean isActive()
	{
		return active;
	}

	@Override
	public int activations()
	{
		return activations;
	}

	@Override
	public int deactivations()
	{
		return deactivations;
	}

	@Override
	public JComponent component()
	{
		return this;
	}

	// ------------------------------------------------------------------ for the tests of this package

	HeaderRow header()
	{
		return header;
	}

	AnswerCard card()
	{
		return card;
	}

	CellStrip cells()
	{
		return cells;
	}

	RangeRow rangeRow()
	{
		return rangeRow;
	}

	FoldRow graphsRow()
	{
		return graphsRow;
	}

	FoldRow lagsRow()
	{
		return lagsRow;
	}

	StripChart strips()
	{
		return strips;
	}

	EventList eventList()
	{
		return list;
	}

	SessionHeader sessionHeader()
	{
		return sessionHeader;
	}

	SessionCounts counts()
	{
		return counts;
	}

	/** The press's timeout: one-shot, running from a press until its report, the fallback or a hide. */
	Timer testTimeout()
	{
		return testTimeout;
	}

	/** The Troubleshoot window that stands; null when none does. */
	TroubleshootDialog dialog()
	{
		return dialog;
	}

	/** The gear menu as the last press on the gear built it; null before the first press that could. */
	JPopupMenu menu()
	{
		return menu;
	}

	/** The developer-mode footer; null outside developer mode. */
	JComponent footer()
	{
		return footer;
	}

	long selectedId()
	{
		return selectedId;
	}

	PanelSnapshot last()
	{
		return last;
	}

	// ------------------------------------------------------------------ the footer and the layout

	/** Block 10, developer mode only: 213 x 14, the snapshot's footer in RuneScape Small {@link Ui#LABEL}. */
	static final class Footer extends JComponent
	{
		static final int HEIGHT = 14;
		static final int BASELINE = 11;

		private String raw = "";
		private String text = "";
		private int fitWidth;

		Footer()
		{
			setOpaque(true);
		}

		void set(String footerText)
		{
			raw = footerText == null ? "" : footerText;
			fitWidth = 0;
		}

		/** The footer as it fits the block's width: cut with "..." when it does not. */
		String text()
		{
			final int w = Ui.widthOf(this);
			if (w != fitWidth)
			{
				text = Ui.fit(Ui.RSS, raw, w);
				fitWidth = w;
			}
			return text;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(Ui.WIDTH, HEIGHT);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			final Graphics2D g = Ui.prepare(graphics);
			g.setColor(Ui.GROUND);
			g.fillRect(0, 0, Ui.widthOf(this), HEIGHT);
			Ui.text(g, text(), 0, BASELINE, Ui.RSS, Ui.LABEL);
		}
	}

	/**
	 * One block under another, each at the left border and as wide as the panel less its border - never under 213 -
	 * at its preferred height, under its own gap. In the client the sidebar gives the panel 242 px (225 and the 17 px
	 * RuneLite keeps at the right for a scroll bar), so the blocks are 230 wide: 6 px in from each edge (the user,
	 * 2026-09-29: "left ... but perhaps we fill all the way to the right as well"); 223 wide while the scroll bar
	 * shows. At the panel's own 225 the blocks are 213 wide, as they were.
	 *
	 * <p>A block whose height depends on its width (the card's and the counts' wrapped text) is given its width
	 * before it is asked for its height, in the layout and in the preferred size alike.
	 *
	 * <p>Choice: a block that already has the width is not resized again, so measuring the panel never moves one.
	 */
	private static final class Column implements LayoutManager
	{
		@Override
		public void addLayoutComponent(String name, Component comp)
		{
		}

		@Override
		public void removeLayoutComponent(Component comp)
		{
		}

		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			final Insets in = parent.getInsets();
			final int width = blockWidth(parent);
			int h = 0;
			for (Component c : parent.getComponents())
			{
				if (c.isVisible())
				{
					h += gapOf(c) + heightAt(c, width);
				}
			}
			return new Dimension(in.left + Ui.WIDTH + in.right, in.top + h + in.bottom);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			return preferredLayoutSize(parent);
		}

		@Override
		public void layoutContainer(Container parent)
		{
			final Insets in = parent.getInsets();
			final int width = blockWidth(parent);
			int y = in.top;
			for (Component c : parent.getComponents())
			{
				if (!c.isVisible())
				{
					continue;
				}
				y += gapOf(c);
				final int h = heightAt(c, width);
				c.setBounds(in.left, y, width, h);
				y += h;
			}
		}

		/** The blocks' width: the panel's, less its border, held at 213; 213 while the panel has no size. */
		private static int blockWidth(Container parent)
		{
			final Insets in = parent.getInsets();
			return Math.max(Ui.WIDTH, parent.getWidth() - in.left - in.right);
		}

		/** The block's preferred height once it has {@code width}. */
		private static int heightAt(Component c, int width)
		{
			if (c.getWidth() != width)
			{
				c.setSize(width, c.getHeight());
			}
			return c.getPreferredSize().height;
		}

		private static int gapOf(Component c)
		{
			final Object gap = c instanceof JComponent ? ((JComponent) c).getClientProperty(GAP) : null;
			return gap instanceof Integer ? (Integer) gap : 0;
		}
	}
}
