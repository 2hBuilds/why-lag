package com.whylag;

import com.whylag.WhyLagWiringTest.Fixture;
import com.whylag.core.PanelSnapshot;
import com.whylag.core.ReportText;
import java.util.List;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;
import static com.whylag.WhyLagWiringTest.notesOf;
import static com.whylag.WhyLagWiringTest.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The version of the build (1.0.1, lot A, A1): one constant, {@link Version#CURRENT}, and the places a player meets
 * it - the plugin's description, the gear menu's last row ({@code GearMenuTest}), the Troubleshoot window's title
 * ({@code TroubleshootDialogTest}), the first line of the report, and the first note of the diagnostics. The export's
 * {@code publish.py} is what checks the constant against the Hub's {@code version=}.
 */
public class VersionTest
{
	@Test
	public void theVersionIsThreeNumbersSeparatedByDots()
	{
		assertTrue(Version.CURRENT, Version.CURRENT.matches("\\d+\\.\\d+\\.\\d+"));
	}

	@Test
	public void theDescriptorsDescriptionEndsWithTheVersionInBrackets()
	{
		final PluginDescriptor d = WhyLagPlugin.class.getAnnotation(PluginDescriptor.class);
		assertNotNull(d);
		assertTrue(d.description(), d.description().endsWith(" (v" + Version.CURRENT + ")"));
		assertTrue("and this build is the one the plan names", d.description().endsWith("(v1.0.0)"));
	}

	/** The report's first line names the version. */
	@Test
	public void theReportsFirstLineContainsTheVersion() throws Exception
	{
		final Fixture f = new Fixture(false);
		onEdt(f.plugin::startUp);
		f.at(1);
		f.plugin.sampleOnce();

		final PanelSnapshot s = f.plugin.attach(f.engine().snapshot(10, f.wall()));
		final String first = ReportText.of(s).split("\n")[0];

		assertTrue(first, first.startsWith("2h Why Lag " + Version.CURRENT + " report - "));
		onEdt(f.plugin::shutDown);
	}

	/** After {@code startUp} the first note of the diagnostics starts with "plugin started, version". */
	@Test
	public void theFirstNoteAfterStartUpStartsWithPluginStartedVersion() throws Exception
	{
		final Fixture f = new Fixture(false);
		onEdt(f.plugin::startUp);

		final List<String> notes = notesOf(f);

		assertTrue(notes.get(0), notes.get(0).substring(10).startsWith("plugin started, version " + Version.CURRENT));
		onEdt(f.plugin::shutDown);
	}
}
