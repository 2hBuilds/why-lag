package com.whylag.ui;

import com.whylag.PanelActions;
import com.whylag.Version;
import com.whylag.core.BadgeSettings;
import com.whylag.core.BadgeStyle;
import com.whylag.core.WhenSmooth;
import java.awt.Font;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.client.ui.ColorScheme;

/**
 * The settings menu behind the header's gear (1.0.1, lot C): a {@link JPopupMenu} that {@link #build} makes FRESH at
 * every open from the last snapshot's {@link BadgeSettings}, so what it ticks is what is stored. Top down:
 * <ol>
 * <li>{@value #SHOW}, a check box;</li>
 * <li>{@value #STYLE}, a submenu of the four {@link BadgeStyle} labels with the current one ticked;</li>
 * <li>{@value #WHEN_SMOOTH}, a check box ticked while the choice is {@link WhenSmooth#SHOW};</li>
 * <li>{@value #CHAT_LINE}, a check box;</li>
 * <li>a separator, {@value #TROUBLESHOOT}, a separator;</li>
 * <li>the version row: "2h Why Lag" and {@link Version#CURRENT} in grey, disabled, no hover and no click.</li>
 * </ol>
 * A tick or a choice hands the new value to the {@link PanelActions} method of its setting and, as every menu item
 * does, closes the menu; the menu never writes a setting itself and keeps no state between two opens.
 *
 * <p>Choice: the check boxes act after their model has toggled, so {@code isSelected()} in the listener is the NEW
 * value; the style choices are radio items in one group, so one of them is always ticked.
 * <p>Choice: every item sets its face (the default sans at 12 px): one that does not is drawn by the look and feel in
 * the 16 px bitmap RuneScape default, and the menu would look nothing like the bank tracker's.
 * <p>Choice: the version row is a panel holding a label, not a menu item: a menu item highlights under the mouse and
 * a press on it would close the menu, and the row is neither a control nor a setting. The PANEL is disabled, and the
 * label keeps the light grey of the stock small lines, which a disabled label would not paint. It is 12 px, the
 * menu's own size (the bank tracker's was 10 px until the user found it hard to read).
 * <p>Choice: {@code closed} runs when the menu goes away by any means - a choice, a press elsewhere, Escape - which
 * is the one moment the header can learn of a close (its press guard).
 */
final class GearMenu
{
	static final String SHOW = "Badge on the game screen";
	static final String STYLE = "Badge style";
	static final String WHEN_SMOOTH = "Show badge when smooth";
	static final String CHAT_LINE = "Chat line on a lag";
	static final String TROUBLESHOOT = "Troubleshoot...";
	/** The version row's words: "2h Why Lag " and the version. */
	static final String VERSION = "2h Why Lag " + Version.CURRENT;
	/** The menu's face: RuneLite's default font (Dialog) at 12 px, made whole here so no font is derived in this package. */
	private static final Font SANS = new Font(Font.DIALOG, Font.PLAIN, 12);
	private static final int ROW_GAP = 6;

	private GearMenu()
	{
	}

	/**
	 * The menu for {@code s}: a tick calls the matching method of {@code actions}, {@value #TROUBLESHOOT} runs
	 * {@code troubleshoot}, and {@code closed} runs when the menu goes away.
	 */
	static JPopupMenu build(BadgeSettings s, PanelActions actions, Runnable troubleshoot, Runnable closed)
	{
		final JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));
		menu.add(check(SHOW, s.show, actions::badgeShow));
		menu.add(styleMenu(s.style, actions));
		menu.add(check(WHEN_SMOOTH, s.whenSmooth == WhenSmooth.SHOW,
			on -> actions.badgeWhenSmooth(on ? WhenSmooth.SHOW : WhenSmooth.HIDE)));
		menu.add(check(CHAT_LINE, s.chatLine, actions::badgeChatLine));
		menu.addSeparator();
		final JMenuItem test = new JMenuItem(TROUBLESHOOT);
		test.setFont(SANS);
		test.addActionListener(e -> troubleshoot.run());
		menu.add(test);
		menu.addSeparator();
		menu.add(versionRow());
		menu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
			{
				closed.run();
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
			}
		});
		return menu;
	}

	/** One check box; {@code onToggle} gets the value the box now holds. */
	private static JCheckBoxMenuItem check(String text, boolean ticked, Consumer<Boolean> onToggle)
	{
		final JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, ticked);
		item.setFont(SANS);
		item.addActionListener(e -> onToggle.accept(item.isSelected()));
		return item;
	}

	/** The style submenu: the four labels in the enum's order as one radio group, the current one ticked. */
	private static JMenu styleMenu(BadgeStyle current, PanelActions actions)
	{
		final JMenu menu = new JMenu(STYLE);
		menu.setFont(SANS);
		final ButtonGroup group = new ButtonGroup();
		for (BadgeStyle style : BadgeStyle.values())
		{
			final JRadioButtonMenuItem item = new JRadioButtonMenuItem(style.toString(), style == current);
			item.setFont(SANS);
			item.addActionListener(e -> actions.badgeStyle(style));
			group.add(item);
			menu.add(item);
		}
		return menu;
	}

	/** The last row: {@link #VERSION} in grey at the menu's own size, disabled, with no hover and no click. */
	private static JPanel versionRow()
	{
		final JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(2, ROW_GAP, 2, ROW_GAP));
		final JLabel label = new JLabel(VERSION);
		label.setFont(SANS);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(label);
		row.add(Box.createHorizontalGlue());
		row.setEnabled(false);
		return row;
	}
}
