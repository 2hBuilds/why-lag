package com.whylag.ui;

import com.whylag.GraphRange;
import com.whylag.PanelActions;
import com.whylag.PanelControl;
import com.whylag.core.Cells;
import com.whylag.core.Fmt;
import com.whylag.core.LagEvent;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.Verdict;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Collections;
import java.util.function.Predicate;
import javax.swing.JComponent;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel of picture 18, "Big Answer" (contract 5): top down, the header, the answer card, the five cells,
 * the range row, the "Graphs" fold row with the five lanes behind it, the "Lags (n)" fold row with the event list
 * behind it, the session's total and its counts, the "Copy report" button, and in developer mode a footer. Content width
 * 213 px at the panel's own 225, and the width of the sidebar less the border when it is wider (230 in the client),
 * on the {@link Ui#GROUND} ground. Every block is one custom-painted component, so a one-second update is
 * a few field writes and one repaint of each block.
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
 * <p>Choice: "Copy report" copies nothing before the first snapshot, or when the report is "", and answers "".
 * <p>Choice: "Copied" shows only when the clipboard took the text.
 * <p>Choice: {@link #component()} is the panel itself.
 * <p>Choice: the developer-mode footer is a block of 213 x 14 (as wide as the panel gives it), RuneScape Small at
 * baseline 11, cut with "...".
 */
public final class WhyLagPanel extends PluginPanel implements PanelControl
{
	/** The client property that holds a block's gap above it. */
	private static final String GAP = "whylag.gap";

	private final PanelActions actions;
	private final HeaderRow header = new HeaderRow();
	private final AnswerCard card = new AnswerCard();
	private final CellStrip cells = new CellStrip();
	private final RangeRow rangeRow;
	private final FoldRow graphsRow;
	private final FoldRow lagsRow;
	private final StripChart strips = new StripChart();
	private final EventList list;
	private final SessionHeader sessionHeader = new SessionHeader();
	private final SessionCounts counts = new SessionCounts();
	private final ButtonRow buttons;
	private final Footer footer;

	/** Where "Copy report" puts its text; true when the clipboard took it. A seam: tests hand in their own. */
	Predicate<String> clipboard = WhyLagPanel::toSystemClipboard;

	private volatile int range;
	private volatile boolean graphsOpen;
	private volatile boolean lagsOpen;
	private volatile boolean active;
	private volatile int activations;
	private volatile int deactivations;
	/** The newest snapshot handed to {@link #show}; Swing thread only. */
	private PanelSnapshot last;
	/** The selected event's id, -1 = none; Swing thread only. */
	private long selectedId = -1;

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
		buttons = new ButtonRow(this::copyReport);
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
		gap(buttons, 10);
		add(header);
		add(card);
		add(cells);
		add(rangeRow);
		add(graphsRow);
		add(lagsRow);
		add(sessionHeader);
		add(counts);
		add(buttons);
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
		buttons.setCopyEnabled(true);
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
		buttons.stopTimer();
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
		final PanelSnapshot s = last;
		if (s == null)
		{
			return "";
		}
		final String text = actions.report(s);
		if (text == null || text.isEmpty())
		{
			return "";
		}
		if (clipboard.test(text))
		{
			buttons.copiedNow();
		}
		return text;
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

	ButtonRow buttons()
	{
		return buttons;
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
