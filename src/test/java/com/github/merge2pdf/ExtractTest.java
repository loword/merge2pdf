package com.github.merge2pdf;

import static com.github.merge2pdf.MergeTest.IMAGES_DIR;
import static com.github.merge2pdf.MergeTest.IMAGE_HEIGHT;
import static com.github.merge2pdf.MergeTest.IMAGE_SIZE;
import static com.github.merge2pdf.MergeTest.IMAGE_WIDTH;
import static com.github.merge2pdf.MergeTest.OUTPUT_DIR;
import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import com.github.merge2pdf.MergeToPdf.ExitCode;
import com.github.merge2pdf.MergeToPdf.Opt;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

/**
 * Extract-specific test cases for {@link MergeToPdf}.
 * 
 * @author <a href="mailto:dmitry.katsubo@gmail.com">Dmitry Katsubo</a>
 */
public class ExtractTest {

	public ExtractTest() {
		if (!OUTPUT_DIR.exists()) {
			OUTPUT_DIR.mkdirs();
		}
	}

	@Test
	public void testExtactWithNoFiles() throws Exception {
		assertEquals(ExitCode.NOT_ENOUGH_FILES, MergeToPdf.processOptions(new String[] { "--" + Opt.extract }));
	}

	@Test
	public void testExtractDeflatedImage() throws Exception {
		// TIFF image was put into below PDF with deflate compression and is extracted as PNG:
		assertEquals(ExitCode.OK, MergeToPdf
		            .processOptions(new String[] { "-e", "-p", OUTPUT_DIR + "/", IMAGES_DIR + "/1_no_dpi.pdf" }));

		// Image can be read/decoded:
		BufferedImage image = ImageIO.read(new File(OUTPUT_DIR, "1_no_dpi_1.png"));
		assertEquals(IMAGE_WIDTH, image.getWidth());
		assertEquals(IMAGE_HEIGHT, image.getHeight());
	}

	@Test
	public void testMergeExtract() throws Exception {
		String pdfName = OUTPUT_DIR + "/" + "out_extract.pdf";

		assertEquals(ExitCode.OK, MergeToPdf.processOptions(new String[] { "--" + Opt.merge,
		        IMAGES_DIR + "/0_extact.jp2", IMAGES_DIR + "/4_fits.tiff", pdfName }));

		assertEquals(ExitCode.OK, MergeToPdf.processOptions(new String[] { "--" + Opt.extract, pdfName }));

		assertEquals(FileUtils.sizeOf(new File(IMAGES_DIR, "0_extact.jp2")),
		            FileUtils.sizeOf(new File(OUTPUT_DIR, "out_extract_1.jp2")));
		BufferedImage image = ImageIO.read(new File(OUTPUT_DIR, "out_extract_1.jp2"));
		assertEquals(IMAGE_WIDTH / 2, image.getWidth());
		assertEquals(IMAGE_HEIGHT / 2, image.getHeight());

		// At the moment iText decodes non 8-bit-per-pixel images as PNG:
		//assertEquals(FileUtils.sizeOf(new File(IMAGES_DIR, "4_fits.tiff")),
		//            FileUtils.sizeOf(new File(OUTPUT_DIR, "out_extract_2.png")));
		image = ImageIO.read(new File(OUTPUT_DIR, "out_extract_2.png"));
		assertEquals(IMAGE_WIDTH - IMAGE_SIZE, image.getWidth());
		assertEquals(IMAGE_SIZE, image.getHeight());
	}
}
