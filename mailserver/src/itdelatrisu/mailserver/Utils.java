package itdelatrisu.mailserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import com.google.common.io.Resources;
import com.google.common.net.InternetDomainName;
import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

/**
 * Utility methods.
 */
public class Utils {
	/** List of illegal filename characters. */
	private final static int[] illegalChars = {
		34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
		11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
		24, 25, 26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47
	};
	static { Arrays.sort(illegalChars); }

	/**
	 * Cleans a file name.
	 * @param badFileName the original name string
	 * @param replace the character to replace illegal characters with (or 0 if none)
	 * @return the cleaned file name
	 * @author Sarel Botha (http://stackoverflow.com/a/5626340)
	 */
	public static String cleanFileName(String badFileName, char replace) {
		boolean doReplace = (replace > 0 && Arrays.binarySearch(illegalChars, replace) < 0);
		StringBuilder cleanName = new StringBuilder();
		for (int i = 0, n = badFileName.length(); i < n; i++) {
			int c = badFileName.charAt(i);
			if (Arrays.binarySearch(illegalChars, c) < 0)
				cleanName.append((char) c);
			else if (doReplace)
				cleanName.append(replace);
		}
		return cleanName.toString();
	}

	/** Reads the input stream and returns the data as a string. */
	public static String streamToString(InputStream is) {
		try (Scanner s = new Scanner(is)) {
			return s.useDelimiter("\\A").hasNext() ? s.next() : "";
		}
	}

	/** Reads the file and returns the data as a string. */
	public static String fileToString(String path, Charset encoding) throws IOException {
		return new String(Files.readAllBytes(Paths.get(path)), encoding);
	}

	/** Reads the resource and returns the data as a string. */
	public static String resourceToString(String res, Charset encoding) throws IOException {
		return Resources.toString(Resources.getResource(res), encoding);
	}

	/** Parses mail data into a MimeMessage. */
	public static MimeMessage toMimeMessage(String content) throws MessagingException {
		Session s = Session.getDefaultInstance(new Properties());
		InputStream is = new ByteArrayInputStream(content.getBytes());
		return new MimeMessage(s, is);
	}

	/** Returns a string representation of the MIME message. */
	public static String messageToString(MimeMessage message)
		throws IOException, MessagingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		message.writeTo(baos);
		return baos.toString(StandardCharsets.UTF_8.name());
	}

	/** Returns the HTML section of a MIME message, or null if not found. */
	public static String getHtmlFromMessage(MimeMessage message) throws MessagingException, IOException {
		Part part = getPartFromMessage(message, "text/html");
		/*
		if (part == null && message.getHeader("Content-Type", null) == null) {
			// if no "Content-Type" header present, try using "multipart/mixed"
			// (as of 11/2016, Gmail didn't always add this header?)
			try {
				MimeMessage copy = new MimeMessage(message);
				copy.setHeader("Content-Type", "multipart/mixed");
				copy.saveChanges();
				part = getPartFromMessage(copy, "text/html");
			} catch (Exception e) {}
		}
		*/
		return (part == null) ? null : (String) part.getContent();
	}

	/** Returns the plain-text section of a MIME message, or null if not found. */
	public static String getTextFromMessage(MimeMessage message) throws MessagingException, IOException {
		Part part = getPartFromMessage(message, "text/plain");
		return (part == null) ? null : (String) part.getContent();
	}

	/** Returns the specified Part (by MIME type) from the given message, or null if not found. */
	private static Part getPartFromMessage(Part message, String contentType) throws MessagingException, IOException {
		if (message.getContentType().startsWith(contentType))
			return message;

		// multipart message: recursively check parts
		if (message.getContentType().startsWith("multipart/")) {
			Multipart multipart = (Multipart) message.getContent();
			for (int i = 0; i < multipart.getCount(); i++) {
				Part part = getPartFromMessage(multipart.getBodyPart(i), contentType);
				if (part != null)
					return part;
			}
		}

		return null;
	}

	/** Returns the private domain + public suffix from a given URL, or null if the host is undefined. */
	public static String getDomainName(String url) throws MalformedURLException {
		String hostname = new URL(url).getHost();
		if (hostname == null)
			return null;
		try {
			InternetDomainName idn = InternetDomainName.from(hostname);
			if (idn.isPublicSuffix())
				return idn.toString();
			return idn.topPrivateDomain().toString();
		} catch (Exception e) {
			return hostname;
		}
	}

	/** Decompresses a GZIP byte array into a String. */
	public static String gzipDecompress(byte[] compressed) throws IOException {
		try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
			return streamToString(gis);
		}
	}

	/** Extracts all links from the given text. */
	public static List<String> extractLinksFromText(String s) {
		UrlDetector parser = new UrlDetector(s, UrlDetectorOptions.Default);
		return parser.detect().stream().map(Url::getFullUrl).collect(Collectors.toList());
	}
}
