package com.github.merge2pdf;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.io.RandomAccessSource;
import com.itextpdf.text.io.RandomAccessSourceFactory;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSmartCopy;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.RandomAccessFileOrArray;
import com.itextpdf.text.pdf.codec.TiffImage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Helper class that creates PDF from given image(s) (JPEG, PNG, ...) or PDFs.
 * 
 * @author <a href="mailto:dmitry.katsubo@gmail.com">Dmitry Katsubo</a>
 */
public class MergeToPdf {

	/**
	 * PDF considers page sizes at 72 dpi, see {@link PageSize}.
	 */
	static final int		 PDF_DPI = 72;

	private static final Log logger	 = LogFactory.getLog(MergeToPdf.class);

	public static void main(String[] args) throws IOException, DocumentException {
		if (args.length < 2) {
			logger.error("At least two arguments are required: [-A4|-dpi] one.pdf [two.jpg three.png ...] out.pdf");
			System.exit(1);
		}

		Document mergedDocument = new Document();
		FileOutputStream os = new FileOutputStream(args[args.length - 1]);
		PdfSmartCopy pdfCopyWriter = new PdfSmartCopy(mergedDocument, os);
		mergedDocument.open();

		// If page is defined, images are scaled to that page size:
		Rectangle scaleToPage = null;
		boolean scaleToDpi = false;

		for (int i = 0; i < args.length - 1; i++) {
			if (i == 0 && args[i].startsWith("-")) {
				if (args[i].equalsIgnoreCase("-dpi")) {
					scaleToDpi = true;
				}
				else {
					scaleToPage = PageSize.getRectangle(args[i].substring(1));
				}

				continue;
			}

			PdfReader reader;

			if (args[i].toLowerCase().endsWith(".pdf")) {
				logger.info("Adding PDF " + args[i] + "...");
				// Copy PDF document:
				reader = new PdfReader(args[i]);
			}
			else {
				logger.info("Adding image " + args[i] + "...");

				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

				Document imageDocument = new Document();
				PdfWriter.getInstance(imageDocument, byteStream);

				imageDocument.open();

				List<Image> images = new ArrayList<Image>();

				if (args[i].toLowerCase().endsWith(".tiff") || args[i].toLowerCase().endsWith(".tif")) {
					// Read all pages from TIFF image:
					// See also https://stackoverflow.com/questions/49414913/
					RandomAccessSource source = new RandomAccessSourceFactory()
					            .createBestSource(new RandomAccessFile(args[i], "r"));
					try {
						RandomAccessFileOrArray rafa = new RandomAccessFileOrArray(source);
						int pages = TiffImage.getNumberOfPages(rafa);
						for (int p = 1; p <= pages; p++) {
							images.add(TiffImage.getTiffImage(rafa, p));
						}
					}
					finally {
						source.close();
					}
				}
				else {
					// Create single page with the dimensions as source image and no margins:
					images.add(Image.getInstance(args[i]));
				}

				for (Image image : images) {
					if (scaleToPage != null || scaleToDpi) {
						// The image should be scaled according to DPI (if available) before it is placed to the page.
						// See also https://stackoverflow.com/a/8245450/267197
						if (image.getDpiX() > 0 && image.getDpiY() > 0
						            && (image.getDpiX() != PDF_DPI || image.getDpiY() != PDF_DPI)) {
							image.scalePercent(100f * PDF_DPI / image.getDpiX(), 100f * PDF_DPI / image.getDpiY());
							logger.debug(String.format("Scaled image as to %d DPI (%.2f, %.2f) -> (%.2f, %.2f)",
							            image.getDpiX(), image.getWidth(), image.getHeight(), image.getScaledWidth(),
							            image.getScaledHeight()));
						}

						if (scaleToPage != null) {
							Rectangle page = scaleToPage;

							// If image does not fit the page after DPI scale, then scale it further down:
							if (isImageNotFittingPage(image, page)) {
								if (image.getScaledWidth() > image.getScaledHeight()) {
									// Rotate the page by 90 degrees effectively changing portrait orientation to landscape and vice versa:
									page = new Rectangle(scaleToPage.getHeight(), scaleToPage.getWidth());

									if (isImageNotFittingPage(image, page)) {
										image.scaleToFit(page.getWidth(), page.getHeight());
									}
								}
								else {
									image.scaleToFit(page.getWidth(), page.getHeight());
								}
							}

							// Align the image with top-left corner of the page:
							image.setAbsolutePosition(0, page.getHeight() - image.getScaledHeight());
							imageDocument.setPageSize(page);
						}
					}

					if (scaleToPage == null) {
						// The page will be scaled to given page when it will be printed:
						image.setAbsolutePosition(0, 0);
						imageDocument.setPageSize(new Rectangle(image.getScaledWidth(), image.getScaledHeight()));
					}

					imageDocument.newPage();
					imageDocument.add(image);
				}

				imageDocument.close();

				// Copy PDF document which is a sequence of pages each having one image:
				reader = new PdfReader(byteStream.toByteArray());
			}

			pdfCopyWriter.addDocument(reader);
			reader.close();
		}

		mergedDocument.close();
	}

	/**
	 * Returns {@code true} if given image does not fit the given page.
	 */
	private static boolean isImageNotFittingPage(Image image, Rectangle page) {
		return image.getScaledWidth() > page.getWidth() || image.getScaledHeight() > page.getHeight();
	}
}
