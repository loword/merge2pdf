package com.github.merge2pdf;

import static org.junit.Assert.assertEquals;

import com.github.merge2pdf.MergeToPdf.ExitCode;
import com.github.merge2pdf.MergeToPdf.Opt;

import org.junit.Test;

/**
 * Command-line options test cases for {@link MergeToPdf}.
 * 
 * @author <a href="mailto:dmitry.katsubo@gmail.com">Dmitry Katsubo</a>
 */
public class MiscTest {

	@Test
	public void testVersion() throws Exception {
		assertEquals(ExitCode.VERSION, MergeToPdf.processOptions(new String[] { "--" + Opt.version.name() }));
	}

	@Test
	public void testHelp() throws Exception {
		assertEquals(ExitCode.HELP, MergeToPdf.processOptions(new String[] { "--" + Opt.help.name() }));
		assertEquals(ExitCode.HELP, MergeToPdf.processOptions(new String[] {}));
	}

	@Test
	public void testIllegalOptionCombination() throws Exception {
		assertEquals(ExitCode.ILLEGAL_OPTION_COMBINATION, MergeToPdf.processOptions(new String[] { "-m", "-e" }));
	}

	@Test
	public void testInvalidOption() throws Exception {
		assertEquals(ExitCode.INVALID_OPTION, MergeToPdf.processOptions(new String[] { "-z" }));
	}
}
