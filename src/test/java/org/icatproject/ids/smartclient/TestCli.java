package org.icatproject.ids.smartclient;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.ExpectedException.none;

import java.io.IOException;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestCli {

	@Rule
	public final ExpectedException thrown = none();

	@Test
	public void supportsShortOptions() throws IOException {
		OptionParser parser = new OptionParser();

		OptionSpec<Long> investigations = parser.acceptsAll(asList("investigation", "i")).withRequiredArg()
				.ofType(Long.class).describedAs("Investigation id");
		OptionSpec<Long> datasets = parser.acceptsAll(asList("dataset", "s")).withRequiredArg().ofType(Long.class)
				.describedAs("Dataset id");
		OptionSpec<Long> datafiles = parser.acceptsAll(asList("datafile", "f")).withRequiredArg().ofType(Long.class)
				.describedAs("Datafile id");

		parser.acceptsAll(asList("h", "?", "help"), "show helpz").forHelp();

		OptionSet options = parser.parse("--i", "12", "-i14", "-i23", "-s15", "--investigation=45");

		assertEquals(asList(12L, 14L, 23L, 45L), options.valuesOf(investigations));
		assertEquals(asList(15L), options.valuesOf(datasets));
		assertEquals(asList(), options.valuesOf(datafiles));

		parser.printHelpOn(System.out);
	}
}
