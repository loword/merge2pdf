package com.github.merge2pdf;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.io.RandomAccessSource;
import com.itextpdf.text.io.RandomAccessSourceFactory;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSmartCopy;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.RandomAccessFileOrArray;
import com.itextpdf.text.pdf.codec.TiffImage;
import com.itextpdf.text.pdf.parser.PdfImageObject;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FilenameUtils;
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
	static final int			PDF_DPI			  = 72;

	private static final Log	logger			  = LogFactory.getLog(MergeToPdf.class);

	private static final String	POM_RESOURCE_NAME = "META-INF/maven/" + MergeToPdf.class.getPackage().getName()
	            + "/merge2pdf/pom.properties";

	enum Opt {
		merge, extract, dpi, A, gravity, scale, border, removeFont("remove-font"), prefix, version, help;

		private final String value;

		private Opt() {
			this.value = name();
		}

		private Opt(String value) {
			this.value = value;
		}

		public String value() {
			return value;
		}
	}

	enum Gravity {
		center("centre"), top("north"), topRight("northEast"), right("east"), bottomRight("southEast"), bottom(
		            "south"), bottomLeft("southWest"), left("west"), topLeft("northWest");

		private final String alias;

		private Gravity(String alias) {
			this.alias = alias;
		}

		public String alias() {
			return alias;
		}
	}

	enum ExitCode {
		OK(0), VERSION(1), HELP(2), INVALID_OPTION(10), MISSING_REQUIRED_OPTION(11), ILLEGAL_OPTION_COMBINATION(
		            12), NOT_ENOUGH_FILES(13);

		private int exitCode;

		private ExitCode(int exitCode) {
			this.exitCode = exitCode;
		}

		public int getExitCode() {
			return exitCode;
		}
	}

	public static void main(String[] args) throws DocumentException, IOException {
		System.exit(processOptions(args).getExitCode());
	}

	/**
	 * Parses and handles command-line options. This function should not call {@link System#exit(int)} hence is more
	 * adapted for testing.
	 */
	static ExitCode processOptions(String[] args) throws DocumentException, IOException {
		Options options = new Options();

		options.addOption("m", Opt.merge.value(), false, "Merge given input files into destination PDF");
		options.addOption("e", Opt.extract.value(), false, "Extact images from given file");
		options.addOption("d", Opt.dpi.value(), false, "Respect image DPI when scaling up/down");
		options.addOption("s", Opt.scale.value(), true,
		            "Scale down (if necessary) image to the box given as page i.e. A4 or dimension i.e. 180x20");
		options.addOption(Option.builder(Opt.A.value()).hasArg().type(Integer.class)
		            .desc("Place image to given page (i.e. -A4 or -A3) scaling down if necessary").build());
		options.addOption("g", Opt.gravity.value(), true,
		            "Place an image to the page at given corner i.e. topleft (default: center)");
		options.addOption("b", Opt.border.value(), true,
		            "Add given border in pixels to each page when page is provided i.e. -b10");
		options.addOption("r", Opt.removeFont.value(), true,
		            "Remove given embedded font(s) i.e. -rArial or remove all fonts i.e. -r \"\"");
		options.addOption("p", Opt.prefix.value(), true, "Output directory and/or file prefix");
		options.addOption("v", Opt.version.value(), false, "Print version and exit");
		options.addOption("h", Opt.help.value(), false, "Print help and exit");

		CommandLine cli;
		try {
			cli = new DefaultParser().parse(options, args);
		}
		catch (ParseException e) {
			logger.error(e.getMessage());
			return ExitCode.INVALID_OPTION;
		}

		if (cli.hasOption(Opt.version.value())) {
			try (InputStream pomStream = MergeToPdf.class.getClassLoader().getSystemClassLoader()
			            .getResourceAsStream(POM_RESOURCE_NAME)) {
				if (pomStream == null) {
					System.out.println("Version: unknown");
				}
				else {
					Properties pomProperties = new Properties();
					pomProperties.load(pomStream);
					System.out.println("Version: " + pomProperties.get("version"));
				}
			}

			return ExitCode.VERSION;
		}

		boolean hasMergeOption = cli.hasOption(Opt.merge.value());
		boolean hasExtractOption = cli.hasOption(Opt.extract.value());

		if (cli.hasOption(Opt.help.value()) || !(hasMergeOption || hasExtractOption)) {
			HelpFormatter helpFormatter = new HelpFormatter();
			helpFormatter.setSyntaxPrefix("Usage:");
			helpFormatter.setOptionComparator(null);
			helpFormatter.printHelp(helpFormatter.getNewLine() + "merge2pdf --" + Opt.merge.value() + " [--"
			            + Opt.dpi.value() + "|--" + Opt.scale.value() + " dim|-" + Opt.A.value() + "num|--"
			            + Opt.border.value() + " num|--" + Opt.removeFont.value()
			            + " pattern] one.pdf [two.jpg three.png ...] out.pdf" + helpFormatter.getNewLine()
			            + "merge2pdf --" + Opt.extract.value() + " [--" + Opt.prefix.value() + " dir/prefix] in.pdf"
			            + helpFormatter.getNewLine() + "merge2pdf --" + Opt.version.value() + helpFormatter.getNewLine()
			            + "merge2pdf --" + Opt.help.value() + helpFormatter.getNewLine(), null, options, null, false);

			return ExitCode.HELP;
		}

		if (hasMergeOption) {
			if (hasExtractOption) {
				logger.error("Either \"" + Opt.merge.value() + "\" or \"" + Opt.extract.value()
				            + "\" options should be provided.");
				return ExitCode.ILLEGAL_OPTION_COMBINATION;
			}

			return merge(cli);
		}

		return extract(cli);
	}

	private static ExitCode merge(CommandLine cli) throws DocumentException, IOException {
		List<String> files = cli.getArgList();

		if (files.size() < 2) {
			logger.error("At least two files are required.");
			return ExitCode.NOT_ENOUGH_FILES;
		}

		// If page is defined, images are scaled to that page size:
		boolean scaleToDpi = cli.hasOption(Opt.dpi.value());

		Rectangle scaleToBox = null;

		if (cli.hasOption(Opt.scale.value())) {
			String scaleToBoxValue = cli.getOptionValue(Opt.scale.value());

			int pos = scaleToBoxValue.indexOf('x');

			if (pos > 0) {
				try {
					scaleToBox = new Rectangle(Integer.parseInt(scaleToBoxValue.substring(0, pos)),
					            Integer.parseInt(scaleToBoxValue.substring(pos + 1)));
				}
				catch (NumberFormatException e) {
					logger.error(e.getMessage());
					return ExitCode.INVALID_OPTION;
				}
			}
			else {
				try {
					scaleToBox = PageSize.getRectangle(scaleToBoxValue);
				}
				catch (RuntimeException e) {
					logger.error(e.getMessage());
					return ExitCode.INVALID_OPTION;
				}
			}
		}

		Rectangle scaleToPage = null;

		if (cli.hasOption(Opt.A.value())) {
			try {
				scaleToPage = PageSize.getRectangle("A" + cli.getOptionValue(Opt.A.value()));
			}
			catch (RuntimeException e) {
				logger.error(e.getMessage());
				return ExitCode.INVALID_OPTION;
			}
		}

		Gravity gravity = null;

		if (cli.hasOption(Opt.gravity.value())) {
			String gravityValue = cli.getOptionValue(Opt.gravity.value());

			for (Gravity g : Gravity.values()) {
				if (g.name().equalsIgnoreCase(gravityValue) || g.alias().equalsIgnoreCase(gravityValue)) {
					gravity = g;
					break;
				}
			}

			if (gravity == null) {
				logger.error("Unknown gravity value " + gravityValue);
				return ExitCode.INVALID_OPTION;
			}

			if (scaleToPage == null) {
				logger.error("Gravity is only applicable when page is defined");
				return ExitCode.INVALID_OPTION;
			}
		}
		else {
			gravity = Gravity.center;
		}

		int border = 0;

		if (cli.hasOption(Opt.border.value())) {
			String borderOptionValue = cli.getOptionValue(Opt.border.value());
			try {
				border = Integer.parseInt(borderOptionValue);
			}
			catch (NumberFormatException e) {
				border = -1;
			}

			if (border < 0) {
				logger.error("Border option should be a non-negative integer but was " + borderOptionValue + ".");
				return ExitCode.INVALID_OPTION;
			}
		}

		Pattern fontNameFilter = null;

		if (cli.hasOption(Opt.removeFont.value())) {
			String removeFontOptionValue = cli.getOptionValue(Opt.removeFont.value());

			if (!removeFontOptionValue.isEmpty()) {
				try {
					fontNameFilter = Pattern.compile(removeFontOptionValue);
				}
				catch (PatternSyntaxException e) {
					logger.error("Remove fond option value should be a valid regular expression.");
					return ExitCode.INVALID_OPTION;
				}
			}
			else {
				// Effectively remove all embedded fonts:
				fontNameFilter = Pattern.compile(".*");
			}
		}

		Document mergedDocument = new Document();
		FileOutputStream os = new FileOutputStream(files.get(files.size() - 1));
		PdfSmartCopy pdfCopyWriter = new PdfSmartCopy(mergedDocument, os);
		mergedDocument.open();

		for (String file : files.subList(0, files.size() - 1)) {
			PdfReader reader;

			if (file.toLowerCase().endsWith(".pdf")) {
				logger.info("Adding PDF " + file + "...");
				// Copy PDF document:
				reader = new PdfReader(file);

				if (fontNameFilter != null) {
					for (int i = 1; i < reader.getXrefSize(); i++) {
						unembedTTF(reader.getPdfObject(i), fontNameFilter);
					}
				}

				// Removing unused objects will remove unused font file streams:
				reader.removeUnusedObjects();
			}
			else {
				logger.info("Adding image " + file + "...");

				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

				Document imageDocument = new Document();
				PdfWriter.getInstance(imageDocument, byteStream);

				imageDocument.open();

				List<Image> images = new ArrayList<Image>();

				if (file.toLowerCase().endsWith(".tiff") || file.toLowerCase().endsWith(".tif")) {
					// Read all pages from TIFF image:
					// See also https://stackoverflow.com/questions/49414913/
					RandomAccessSource source = new RandomAccessSourceFactory()
					            .createBestSource(new RandomAccessFile(file, "r"));
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
					images.add(Image.getInstance(file));
				}

				for (Image image : images) {
					// The image should be scaled according to DPI (if available) before it is placed to the page.
					// See also https://stackoverflow.com/a/8245450/267197
					if (scaleToDpi && image.getDpiX() > 0 && image.getDpiY() > 0
					            && (image.getDpiX() != PDF_DPI || image.getDpiY() != PDF_DPI)) {
						image.scalePercent(100f * PDF_DPI / image.getDpiX(), 100f * PDF_DPI / image.getDpiY());
						logger.debug(String.format("Scaled image as to %d DPI (%.2f, %.2f) -> (%.2f, %.2f)",
						            image.getDpiX(), image.getWidth(), image.getHeight(), image.getScaledWidth(),
						            image.getScaledHeight()));
					}

					if (scaleToBox != null) {
						scaleToBox(image, scaleToBox, 0);
					}

					if (scaleToPage != null) {
						Rectangle page = scaleToBox(image, scaleToPage, border);

						imageDocument.setPageSize(page);

						//FIXME: apply gravity
						switch (gravity) {
						case center:
							image.setAbsolutePosition(page.getWidth() / 2 - image.getScaledWidth() / 2,
							            page.getHeight() / 2 - image.getScaledHeight() / 2);
							break;
						case top:
							image.setAbsolutePosition(page.getWidth() / 2 - image.getScaledWidth() / 2,
							            page.getHeight() - image.getScaledHeight() - border);
							break;
						case topRight:
							image.setAbsolutePosition(page.getWidth() - image.getScaledWidth() - border,
							            page.getHeight() - image.getScaledHeight() - border);
							break;
						case right:
							image.setAbsolutePosition(page.getWidth() - image.getScaledWidth() - border,
							            page.getHeight() / 2 - image.getScaledHeight() / 2);
							break;
						case bottomRight:
							image.setAbsolutePosition(page.getWidth() - image.getScaledWidth() - border, border);
							break;
						case bottom:
							image.setAbsolutePosition(page.getWidth() / 2 - image.getScaledWidth() / 2, border);
							break;
						case bottomLeft:
							image.setAbsolutePosition(border, border);
							break;
						case left:
							image.setAbsolutePosition(border, page.getHeight() / 2 - image.getScaledHeight() / 2);
							break;
						case topLeft:
							image.setAbsolutePosition(border, page.getHeight() - image.getScaledHeight() - border);
							break;
						}
					}
					else {
						// The page could be later scaled to given page when it is printed by PDF viewer:
						image.setAbsolutePosition(border, border);
						imageDocument.setPageSize(new Rectangle(image.getScaledWidth() + border * 2,
						            image.getScaledHeight() + border * 2));
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

		return ExitCode.OK;
	}

	private static ExitCode extract(CommandLine cli) throws IOException {
		List<String> files = cli.getArgList();

		if (files.isEmpty()) {
			logger.error("Input PDF file is required.");
			return ExitCode.NOT_ENOUGH_FILES;
		}

		String inputFile = files.get(0);
		String outputFilePrefix;

		if (cli.hasOption(Opt.prefix.value())) {
			outputFilePrefix = cli.getOptionValue(Opt.prefix.value());
			if (FilenameUtils.getName(outputFilePrefix).isEmpty()) {
				outputFilePrefix += FilenameUtils.getBaseName(inputFile);
			}
		}
		else {
			outputFilePrefix = FilenameUtils.getFullPath(inputFile) + FilenameUtils.getBaseName(inputFile);
		}

		// Code is taken from https://developers.itextpdf.com/examples/image-examples-itext5/reduce-image
		PdfReader reader = new PdfReader(inputFile);

		int objectsNumber = reader.getXrefSize();
		int imageIndex = 1;

		for (int objectIndex = 0; objectIndex < objectsNumber; objectIndex++) {
			PdfObject object = reader.getPdfObject(objectIndex);
			if (object == null || !object.isStream()) {
				continue;
			}

			PRStream stream = (PRStream) object;
			if (!PdfName.IMAGE.equals(stream.getAsName(PdfName.SUBTYPE))) {
				continue;
			}

			PdfImageObject image = new PdfImageObject(stream);

			String outputFileName = outputFilePrefix + "_" + imageIndex++ + "."
			            + image.getImageBytesType().name().toLowerCase();

			logger.info("Extracting " + outputFileName + "...");

			try (FileOutputStream fos = new FileOutputStream(outputFileName)) {
				fos.write(image.getImageAsBytes());
			}
		}

		return ExitCode.OK;
	}

	/**
	 * Scale down given image to given box..
	 */
	private static Rectangle scaleToBox(Image image, Rectangle box, int border) {
		// If image does not fit the page after DPI and box scale (if requested), then scale it further down:
		if (isImageNotFittingBox(image, box, border)) {
			if (image.getScaledWidth() > image.getScaledHeight()) {
				// Rotate the page by 90 degrees effectively changing portrait orientation to landscape and vice versa:
				box = new Rectangle(box.getHeight(), box.getWidth());

				if (isImageNotFittingBox(image, box, border)) {
					image.scaleToFit(box.getWidth() - border * 2, box.getHeight() - border * 2);
				}
			}
			else {
				image.scaleToFit(box.getWidth() - border * 2, box.getHeight() - border * 2);
			}
		}

		return box;
	}

	/**
	 * Returns {@code true} if given image does not fit the given box including the given margin (border).
	 */
	private static boolean isImageNotFittingBox(Image image, Rectangle box, int border) {
		return image.getScaledWidth() > box.getWidth() - border * 2
		            || image.getScaledHeight() > box.getHeight() - border * 2;
	}

	/**
	 * Processes a dictionary. In case of font dictionaries, the dictionary is processed. The code was taken from
	 * <a href="https://itextpdf.com/en/resources/examples/itext-5/unembed-font">Unembed a font</a>.
	 */
	private static void unembedTTF(PdfObject pdfObject, Pattern fontNameFilter) {
		// Ignore all dictionaries that aren't font dictionaries:
		if (pdfObject == null || !pdfObject.isDictionary()) {
			return;
		}

		PdfDictionary dict = (PdfDictionary) pdfObject;

		if (!dict.isFont()) {
			return;
		}

		PdfName baseFont = dict.getAsName(PdfName.BASEFONT);
		// Remove leading "/" from font name:
		String fontName = PdfName.decodeName(baseFont.toString()).substring(1);

		// Check if a subset (i.e. "ZIGEYT+ComicSansMS") was used (in which case we remove the prefix):
		int pos = fontName.indexOf('+');
		if (pos > 0) {
			fontName = fontName.substring(pos + 1);
			baseFont = new PdfName(fontName);
		}

		if (dict.getAsDict(PdfName.FONTFILE2) != null) {
			logger.warn("Font " + fontName + " is not TTF and hence not removed");
			return;
		}

		if (!fontNameFilter.matcher(fontName).matches()) {
			return;
		}

		PdfDictionary fontDescriptor = dict.getAsDict(PdfName.FONTDESCRIPTOR);

		if (fontDescriptor == null) {
			return;
		}

		// Replace the font name and remove the embedded font data:
		dict.put(PdfName.BASEFONT, baseFont);
		fontDescriptor.put(PdfName.FONTNAME, baseFont);
		fontDescriptor.remove(PdfName.FONTFILE2);
	}
}
