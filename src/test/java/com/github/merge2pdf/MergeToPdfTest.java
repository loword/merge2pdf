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
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.parser.PdfContentReaderTool;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Test;

/**
 * Test cases for {@link MergeToPdf}.
 * 
 * @author <a href="mailto:dmitry.katsubo@gmail.com">Dmitry Katsubo</a>
 */
public class MergeToPdfTest {

	private static final File	IMAGES_DIR		  = new File(
	            "src/test/resources/" + MergeToPdfTest.class.getPackage().getName().replace('.', '/'));
	private static final File	OUTPUT_DIR		  = new File("target");

	private static final String	PAGE_SIZE		  = "A5";
	private static final String	IMAGE_FORMAT	  = "tiff";
	private static final int	IMAGE_COLOR_SPACE = BufferedImage.TYPE_INT_RGB;
	private static final int	IMAGE_WIDTH		  = Math.round(PageSize.getRectangle(PAGE_SIZE).getWidth());
	private static final int	IMAGE_HEIGHT	  = Math.round(PageSize.getRectangle(PAGE_SIZE).getHeight());
	private static final int	IMAGE_SIZE		  = 10;

	private final String[]		args;

	public MergeToPdfTest() {
		if (!OUTPUT_DIR.exists()) {
			OUTPUT_DIR.mkdirs();
		}

		//recreateImages();

		List<String> args = new ArrayList<String>();

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
	public void testMergeDefault() {
		runTest(args, "default");
	}

	@Test
	public void testMergeDpi() {
		runTest(ArrayUtils.insert(0, args, "-dpi"), "dpi");
	}

	@Test
	public void testMergePage() {
		runTest(ArrayUtils.insert(0, args, "-" + PAGE_SIZE), "page");
	}

	private static void runTest(String[] args, String testResourceSuffix) {
		try {
			//releaseDirectByteBuffer();
			File pdfFile = new File(OUTPUT_DIR, "out_" + testResourceSuffix + ".pdf");

			MergeToPdf.main(ArrayUtils.add(args, pdfFile.getPath()));

			StringWriter pdfDump = new StringWriter();
			PdfContentReaderTool.listContentStream(pdfFile, new PrintWriter(pdfDump));
			assertEquals(IOUtils.toString(
			            MergeToPdfTest.class.getResourceAsStream("dump_" + testResourceSuffix + ".txt"),
			            Charsets.UTF_8), StringUtils.replace(pdfDump.toString(), "\r\n", "\n"));
		}
		catch (Exception e) {
			ExceptionUtils.rethrow(e);
		}
	}

	/**
	 * Even though the stream which is associated with OUTPUT_PDF_NAME is closed, "java.io.FileNotFoundException:
	 * out.pdf (The requested operation cannot be per formed on a file with a user-mapped section open)" is thrown.
	 * 
	 * @see <a href="https://stackoverflow.com/a/45850877/267197">How to unmap a file from memory mapped using
	 *      FileChannel in java</a>
	 */
	private static void releaseDirectByteBuffer() throws IOException {
		FileInputStream os = new FileInputStream(new File(OUTPUT_DIR, "out.pdf"));
		MappedByteBuffer buffer = os.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, 1);
		//((sun.nio.ch.DirectBuffer) buffer).cleaner().clean();
		os.close();

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
	 * This method will re-create all images in test resources. After this function is run, the resulting image
	 * {@code 1_no_dpi.tiff} should be wrapped into {@code 1_no_dpi.pdf}, and images {@code 2_dpi_standard.tiff} and
	 * {@code 3_dpi.tiff} should be put into multi-page TIFF.
	 */
	private static void recreateImages() {
		createImage(IMAGE_WIDTH, IMAGE_HEIGHT, "1_no_dpi", -1);
		createImage(IMAGE_WIDTH, IMAGE_HEIGHT, "2_dpi_standard", MergeToPdf.PDF_DPI);
		createImage(IMAGE_WIDTH, IMAGE_HEIGHT, "3_dpi", MergeToPdf.PDF_DPI * 2);
		createImage(IMAGE_WIDTH - IMAGE_SIZE, IMAGE_SIZE, "4_fits", -1);
		createImage(IMAGE_SIZE, IMAGE_HEIGHT + IMAGE_SIZE, "5_fits_scale", MergeToPdf.PDF_DPI);
		createImage(IMAGE_HEIGHT, IMAGE_SIZE, "6_fits_rotate", MergeToPdf.PDF_DPI);
		createImage(IMAGE_HEIGHT * 2, IMAGE_SIZE * 2, "7_fits_rotate_scale", MergeToPdf.PDF_DPI);
	}

	private static void createImage(int width, int height, String imageNamePrefix, int dpi) {
		BufferedImage image = new BufferedImage(width, height, IMAGE_COLOR_SPACE);
		Graphics2D g2d = image.createGraphics();

		g2d.setColor(Color.LIGHT_GRAY);
		g2d.fillRect(0, 0, width, height);

		g2d.setColor(Color.RED);
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
			// Proved to provide the smallest filesize for given images:
			writeParam.setCompressionType("ZLib");

			writer.setOutput(outputStream);
			// IIOMetadata should be attached to the image, and ImageWriteParam should be passed to write():
			writer.write(null, new IIOImage(image, null, createMetadataWithDpi(writer, writeParam, dpi)), writeParam);
		}
		catch (IOException e) {
			ExceptionUtils.rethrow(e);
		}
		finally {
			IOUtils.closeQuietly(outputStream);
		}
	}

	/**
	 * Create IOImage metadata for TIFF writer that contains information about DPI.
	 * 
	 * @see <a href="https://stackoverflow.com/questions/321736/how-to-set-dpi-information-in-an-image">How to set DPI
	 *      information in an image</a>
	 */
	private static IIOMetadata createMetadataWithDpi(ImageWriter writer, ImageWriteParam writeParam, int resolution)
	            throws IIOInvalidTreeException {
		ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(IMAGE_COLOR_SPACE);
		IIOMetadata meta = writer.getDefaultImageMetadata(type, writeParam);
		TIFFDirectory dir = TIFFDirectory.createFromMetadata(meta);

		if (resolution > 0) {
			// Used to create/resolve TIFF tag by its ID:
			BaselineTIFFTagSet base = BaselineTIFFTagSet.getInstance();

			// Create necessary TIFF fields and set them to TIFF directory:
			TIFFField fieldXRes = new TIFFField(base.getTag(BaselineTIFFTagSet.TAG_X_RESOLUTION), TIFFTag.TIFF_RATIONAL,
			            1, new long[][] { { resolution, 1 } });
			TIFFField fieldYRes = new TIFFField(base.getTag(BaselineTIFFTagSet.TAG_Y_RESOLUTION), TIFFTag.TIFF_RATIONAL,
			            1, new long[][] { { resolution, 1 } });

			dir.addTIFFField(fieldXRes);
			dir.addTIFFField(fieldYRes);
		}

		// Convert TIFF directory to IOImage metadata:
		return dir.getAsMetadata();
	}
}
