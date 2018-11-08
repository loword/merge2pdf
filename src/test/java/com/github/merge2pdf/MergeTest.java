package com.github.merge2pdf;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;

import com.github.jaiimageio.plugins.tiff.BaselineTIFFTagSet;
import com.github.jaiimageio.plugins.tiff.TIFFDirectory;
import com.github.jaiimageio.plugins.tiff.TIFFField;
import com.github.jaiimageio.plugins.tiff.TIFFTag;
import com.github.merge2pdf.MergeToPdf.ExitCode;
import com.github.merge2pdf.MergeToPdf.Opt;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.parser.PdfContentReaderTool;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Test;

/**
 * Merge-specific test cases for {@link MergeToPdf}.
 * 
 * @author <a href="mailto:dmitry.katsubo@gmail.com">Dmitry Katsubo</a>
 */
public class MergeTest {

	static final File			IMAGES_DIR	 = new File(
	            "src/test/resources/" + MergeTest.class.getPackage().getName().replace('.', '/'));
	static final File			OUTPUT_DIR	 = new File("target");

	private static final String	PAGE_SIZE	 = Opt.A + "5";
	private static final String	IMAGE_FORMAT = "tiff";

	static final int			IMAGE_WIDTH	 = Math.round(PageSize.getRectangle(PAGE_SIZE).getWidth());
	static final int			IMAGE_HEIGHT = Math.round(PageSize.getRectangle(PAGE_SIZE).getHeight());
	static final int			IMAGE_SIZE	 = 10;

	private final String[]		args;

	public MergeTest() {
		if (!OUTPUT_DIR.exists()) {
			OUTPUT_DIR.mkdirs();
		}

		//recreateTestImages();

		List<String> args = new ArrayList<String>();

		args.add("--" + Opt.merge);

		// List all generated TIFF images and form list of CLI arguments:
		for (File file : IMAGES_DIR.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File file, String name) {
				return name.endsWith(IMAGE_FORMAT) || name.endsWith("tif") || name.endsWith("pdf");
			}
		})) {
			args.add(file.getPath());
		}

		Collections.sort(args);

		this.args = args.toArray(new String[args.size()]);
	}

	@Test
	public void testMergeWithNoFiles() throws Exception {
		assertEquals(ExitCode.NOT_ENOUGH_FILES, MergeToPdf.processOptions(new String[] { "--" + Opt.merge }));
		assertEquals(ExitCode.NOT_ENOUGH_FILES,
		            MergeToPdf.processOptions(new String[] { "--" + Opt.merge, "one.pdf" }));
	}

	@Test
	public void testMergeWithInvalidScaleBox() throws Exception {
		assertEquals(ExitCode.INVALID_OPTION,
		            MergeToPdf.processOptions(new String[] { "-m", "-s", "x10", "in.pdf", "out.pdf" }));
		assertEquals(ExitCode.INVALID_OPTION,
		            MergeToPdf.processOptions(new String[] { "-m", "-s", "10x", "in.pdf", "out.pdf" }));
		assertEquals(ExitCode.INVALID_OPTION,
		            MergeToPdf.processOptions(new String[] { "-m", "-s", "AxB", "in.pdf", "out.pdf" }));
		assertEquals(ExitCode.INVALID_OPTION,
		            MergeToPdf.processOptions(new String[] { "-m", "-s", "ZZ", "in.pdf", "out.pdf" }));
	}

	@Test
	public void testMergeWithInvalidPage() throws Exception {
		assertEquals(ExitCode.INVALID_OPTION,
		            MergeToPdf.processOptions(new String[] { "-m", "-A!", "in.pdf", "out.pdf" }));
	}

	@Test
	public void testMergeWithInvalidGravity() throws Exception {
		assertEquals(ExitCode.INVALID_OPTION,
		            MergeToPdf.processOptions(new String[] { "-m", "-g", "center", "in.pdf", "out.pdf" }));
		assertEquals(ExitCode.INVALID_OPTION,
		            MergeToPdf.processOptions(new String[] { "-m", "-A2", "-g", "unknown", "in.pdf", "out.pdf" }));
		assertEquals(ExitCode.INVALID_OPTION,
		            MergeToPdf.processOptions(new String[] { "-m", "-A1", "-g", "south_west", "in.pdf", "out.pdf" }));
	}

	@Test
	public void testMergeWithInvalidBorder() throws Exception {
		assertEquals(ExitCode.INVALID_OPTION,
		            MergeToPdf.processOptions(new String[] { "-m", "-b-1", "in.pdf", "out.pdf" }));
	}

	@Test
	public void testMergeDefault() {
		runTest(args, "default");
	}

	@Test
	public void testMergeDefaultBorder() {
		runTest(ArrayUtils.insert(0, args, "-b20"), "default_border");
	}

	@Test
	public void testMergeDpi() {
		runTest(ArrayUtils.insert(0, args, "--" + Opt.dpi), "dpi");
	}

	@Test
	public void testMergeDpiBorder() {
		runTest(ArrayUtils.insert(0, args, "-d", "--" + Opt.border, "50"), "dpi_border");
	}

	@Test
	public void testMergePage() {
		runTest(ArrayUtils.insert(0, args, "-" + PAGE_SIZE), "page");
	}

	@Test
	public void testMergePageBorder() {
		runTest(ArrayUtils.insert(0, args, "-" + PAGE_SIZE, "--" + Opt.border, "10"), "page_border");
	}

	@Test
	public void testMergeDpiPage() {
		runTest(ArrayUtils.insert(0, args, "--" + Opt.dpi, "-" + PAGE_SIZE), "dpi_page");
	}

	@Test
	public void testMergeDpiPageBorder() {
		runTest(ArrayUtils.insert(0, args, "--" + Opt.dpi, "-" + PAGE_SIZE, "--" + Opt.border, "10"),
		            "dpi_page_border");
	}

	@Test
	public void testMergeScale() {
		runTest(ArrayUtils.insert(0, args, "--" + Opt.scale, "100x100"), "scale");
	}

	@Test
	public void testMergeScalePage() {
		// Scale to page prevails over scale to box:
		runTest(ArrayUtils.insert(0, args, "-s", "1000x1000", "-A5"), "scale_page");
		runTest(ArrayUtils.insert(0, args, "-s", "10000x1000", "-A5"), "scale_page");
		runTest(ArrayUtils.insert(0, args, "-s", "100000x1000", "-A5"), "scale_page");
		// Scale to box does not have any effect:
		runTest(ArrayUtils.insert(0, args, "-s", "A5", "-A5"), "scale_page");
	}

	@Test
	public void testGravityTop() {
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "top"), "gravity_top");
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "north"), "gravity_top");
	}

	@Test
	public void testGravityTopRight() {
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "topRight"), "gravity_top_right");
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "northEast"), "gravity_top_right");
	}

	@Test
	public void testGravityRight() {
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "RIGHT"), "gravity_right");
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "EAST"), "gravity_right");
	}

	@Test
	public void testGravityBottomRight() {
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "BottomRight"), "gravity_bottom_right");
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "SouthEast"), "gravity_bottom_right");
	}

	@Test
	public void testGravityBottom() {
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "boTTom"), "gravity_bottom");
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "sOUth"), "gravity_bottom");
	}

	@Test
	public void testGravityBottomLeft() {
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "bottomLEFT"), "gravity_bottom_left");
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "southWest"), "gravity_bottom_left");
	}

	@Test
	public void testGravityLeft() {
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "left"), "gravity_left");
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "west"), "gravity_left");
	}

	@Test
	public void testGravityTopLeft() {
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "TOPleft"), "gravity_top_left");
		runTest(ArrayUtils.insert(0, args, "-A3", "--" + Opt.gravity, "NORTHwest"), "gravity_top_left");
	}

	private static void runTest(String[] args, String testResourceSuffix) {
		try {
			File pdfFile = new File(OUTPUT_DIR, "out_" + testResourceSuffix + ".pdf");

			releaseDirectByteBuffer(pdfFile);

			assertEquals(ExitCode.OK, MergeToPdf.processOptions(ArrayUtils.add(args, pdfFile.getPath())));

			StringWriter pdfDump = new StringWriter();
			PdfContentReaderTool.listContentStream(pdfFile, new PrintWriter(pdfDump));
			assertEquals(IOUtils.toString(MergeTest.class.getResourceAsStream("dump_" + testResourceSuffix + ".txt"),
			            Charsets.UTF_8), StringUtils.replace(pdfDump.toString(), "\r\n", "\n"));
		}
		catch (Exception e) {
			ExceptionUtils.rethrow(e);
		}
	}

	/**
	 * This method will re-create all images in test resources. After this function is run:
	 * <ul>
	 * <li>The image {@code 0_extact.tiff} should be converted into {@code 0_extact.jp2}
	 * <li>The image {@code 1_no_dpi.tiff} should be wrapped into {@code 1_no_dpi.pdf}
	 * <li>Images {@code 6_fits_rotate.tiff} and {@code 7_fits_rotate_scale.tiff} should be put into one multi-page TIFF
	 * {@code 6-7_fits_rotate-scale.tif}
	 * </ul>
	 */
	private static void recreateTestImages() {
		createImage(IMAGE_WIDTH / 2, IMAGE_HEIGHT / 2, "0_extact", -1, BufferedImage.TYPE_INT_RGB);
		createImage(IMAGE_WIDTH, IMAGE_HEIGHT, "1_no_dpi", -1, BufferedImage.TYPE_INT_RGB);
		createImage(IMAGE_WIDTH, IMAGE_HEIGHT, "2_dpi_standard", MergeToPdf.PDF_DPI, BufferedImage.TYPE_INT_RGB);
		createImage(IMAGE_WIDTH, IMAGE_HEIGHT, "3_dpi", MergeToPdf.PDF_DPI * 2, BufferedImage.TYPE_INT_RGB);
		createImage(IMAGE_WIDTH - IMAGE_SIZE, IMAGE_SIZE, "4_fits", -1, BufferedImage.TYPE_BYTE_BINARY);
		createImage(IMAGE_SIZE, IMAGE_HEIGHT + IMAGE_SIZE, "5_fits_scale", MergeToPdf.PDF_DPI,
		            BufferedImage.TYPE_INT_RGB);
		createImage(IMAGE_HEIGHT, IMAGE_SIZE, "6_fits_rotate", MergeToPdf.PDF_DPI, BufferedImage.TYPE_INT_RGB);
		createImage(IMAGE_HEIGHT * 2, IMAGE_SIZE * 2, "7_fits_rotate_scale", MergeToPdf.PDF_DPI,
		            BufferedImage.TYPE_INT_RGB);
	}

	private static void createImage(int width, int height, String imageNamePrefix, int dpi, int colorSpace) {
		BufferedImage image = new BufferedImage(width, height, colorSpace);
		Graphics2D g2d = image.createGraphics();

		g2d.setColor(Color.LIGHT_GRAY);
		g2d.fillRect(0, 0, width, height);

		g2d.setColor(colorSpace == BufferedImage.TYPE_BYTE_BINARY ? Color.BLACK : Color.RED);
		g2d.drawLine(0, 0, 0, height - 1);
		g2d.drawLine(0, 0, width - 1, 0);
		g2d.drawLine(width - 1, height - 1, width - 1, 0);
		g2d.drawLine(width - 1, height - 1, 0, height - 1);

		g2d.setColor(Color.BLUE);
		g2d.drawLine(1, 1, 1, height - 2);
		g2d.drawLine(1, 1, width - 2, 1);
		g2d.drawLine(width - 2, height - 2, width - 2, 1);
		g2d.drawLine(width - 2, height - 2, 1, height - 2);

		ImageOutputStream outputStream = null;

		try {
			File outputFile = new File(IMAGES_DIR, imageNamePrefix + "." + IMAGE_FORMAT);
			// ImageIO does not overwrite/truncate the file, as it is opened via RandomAccessFile(f, "rw") and re-used, hence size only grows.
			// Truncation is also an option, see https://stackoverflow.com/a/36316178/267197
			outputFile.delete();
			outputStream = ImageIO.createImageOutputStream(outputFile);
			ImageWriter writer = ImageIO.getImageWritersByFormatName(IMAGE_FORMAT).next();

			ImageWriteParam writeParam = writer.getDefaultWriteParam();
			writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			// Proved to provide the smallest filesize for given images (for the whole list check com.github.jaiimageio.impl.plugins.tiff.TIFFImageWriter#TIFFCompressionTypes):
			writeParam.setCompressionType(colorSpace == BufferedImage.TYPE_BYTE_BINARY ? "CCITT RLE" : "ZLib");

			writer.setOutput(outputStream);
			// IIOMetadata should be attached to the image, and ImageWriteParam should be passed to write():
			writer.write(null, new IIOImage(image, null, createMetadataWithDpi(writer, writeParam, dpi, colorSpace)),
			            writeParam);
		}
		catch (IOException e) {
			ExceptionUtils.rethrow(e);
		}
		finally {
			IOUtils.closeQuietly(outputStream);
		}
	}

	/**
	 * Even though the stream which is associated with OUTPUT_PDF_NAME is closed, "java.io.FileNotFoundException:
	 * out.pdf (The requested operation cannot be per formed on a file with a user-mapped section open)" is thrown.
	 * 
	 * @see <a href="https://stackoverflow.com/a/45850877/267197">How to unmap a file from memory mapped using
	 *      FileChannel in java</a>
	 */
	private static void releaseDirectByteBuffer(File outputFile) throws IOException {
		if (!outputFile.exists()) {
			return;
		}

		FileInputStream os = new FileInputStream(outputFile);
		MappedByteBuffer buffer = os.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, 1);
		os.close();
		// Below line of code does not help:
		//((sun.nio.ch.DirectBuffer) buffer).cleaner().clean();

		WeakReference<MappedByteBuffer> bufferWeakRef = new WeakReference<MappedByteBuffer>(buffer);
		buffer = null;

		final long startTime = System.currentTimeMillis();
		while (null != bufferWeakRef.get()) {
			if (System.currentTimeMillis() - startTime > 1000) {
				// Give up and home for the best:
				return;
			}
			System.gc();
			Thread.yield();
		}
	}

	/**
	 * Create IOImage metadata for TIFF writer that contains information about DPI.
	 * 
	 * @see <a href="https://stackoverflow.com/questions/321736/how-to-set-dpi-information-in-an-image">How to set DPI
	 *      information in an image</a>
	 * @see <a href=
	 *      "https://github.com/jai-imageio/jai-imageio-core/blob/0feba94520cc9cb59c58167f3b13dd712f100bbc/src/main/java/com/github/jaiimageio/impl/plugins/tiff/TIFFImageWriter.java#L1152"><tt>TIFFImageWriter:1152-1236</tt></a>
	 */
	private static IIOMetadata createMetadataWithDpi(ImageWriter writer, ImageWriteParam writeParam, int resolution,
	            int colorSpace) throws IIOInvalidTreeException {
		ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(colorSpace);
		IIOMetadata meta = writer.getDefaultImageMetadata(type, writeParam);
		TIFFDirectory dir = TIFFDirectory.createFromMetadata(meta);

		if (resolution > 0) {
			// Used to create/resolve TIFF tag by its ID:
			BaselineTIFFTagSet tagSet = BaselineTIFFTagSet.getInstance();

			// First value is a divisible and second is divider, so resolution is calculated as "resolution / 1" by reader:
			long[][] fieldValue = new long[][] { { resolution, 1 } };

			// Create necessary TIFF fields and set them to TIFF directory:
			dir.addTIFFField(new TIFFField(tagSet.getTag(BaselineTIFFTagSet.TAG_X_RESOLUTION), TIFFTag.TIFF_RATIONAL, 1,
			            fieldValue));
			dir.addTIFFField(new TIFFField(tagSet.getTag(BaselineTIFFTagSet.TAG_Y_RESOLUTION), TIFFTag.TIFF_RATIONAL, 1,
			            fieldValue));
			dir.addTIFFField(new TIFFField(tagSet.getTag(BaselineTIFFTagSet.TAG_RESOLUTION_UNIT),
			            BaselineTIFFTagSet.RESOLUTION_UNIT_INCH));
		}

		// Convert TIFF directory to IOImage metadata:
		return dir.getAsMetadata();
	}
}
