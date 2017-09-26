package itdelatrisu.mailserver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Link extractor.
 */
public class LinkExtractor {
	/** Image data. */
	public class Image {
		/** Source URL. */
		public final String url;

		/** Dimension attributes. */
		public final String width, height;

		/** Creates a new image. */
		public Image(String url, String width, String height) {
			this.url = url;
			this.width = width;
			this.height = height;
		}
	}

	/** Link data. */
	public class Link {
		/** URL. */
		public final String url;

		/** The URL type. */
		public final LinkType type;

		/** Creates a new link. */
		public Link(String url, LinkType type) {
			this.url = url;
			this.type = type;
		}
	}

	/** Inline link data. */
	public class InlineLink {
		/** URL. */
		public final String url;

		/** Inner text. */
		public final String text;

		/** Creates a new inline link. */
		public InlineLink(String url, String text) {
			this.url = url;
			this.text = text;
		}
	}

	/** Link types. */
	public enum LinkType {
		IMAGE, LINK, MEDIA, IMPORT;
		@Override public String toString() { return this.name().toLowerCase(); }
	}

	/** Parsed document. */
	private final Document document;

	/** All links. */
	private final List<Link> links = new ArrayList<Link>();

	/** All inline images (e.g. in 'img' tags). */
	private final List<Image> inlineImages = new ArrayList<Image>();

	/** All images in inline CSS (e.g. in 'url()' values). */
	private final List<String> inlineCssImages = new ArrayList<String>();

	/** All imports (e.g. external stylesheets). */
	private final List<String> imports = new ArrayList<String>();

	/** All inline links (e.g. in 'a' tags). */
	private final List<InlineLink> inlineLinks = new ArrayList<InlineLink>();

	/** All other media (e.g. with 'src' keys, but not 'img' tags). */
	private final List<String> media = new ArrayList<String>();

	/**
	 * Extracts links from an HTML body.
	 * @param html the HTML body
	 */
	public LinkExtractor(String html) {
		this.document = extractLinksFromHtml(html);
	}

	/** Returns the parsed document. */
	public Document getDocument() { return document; }

	/** Returns all extracted links. */
	public List<Link> getAllLinks() { return links; }

	/** Returns all inline images (e.g. in 'img' tags). */
	public List<Image> getInlineImages() { return inlineImages; }

	/** Returns all images in inline CSS (e.g. in 'url()' values). */
	public List<String> getInlineCssImages() { return inlineCssImages; }

	/** Returns all imports (e.g. external stylesheets). */
	public List<String> getImports() { return imports; }

	/** Returns all inline links (e.g. in 'a' tags). */
	public List<InlineLink> getInlineLinks() { return inlineLinks; }

	/** Returns all other media (e.g. with 'src' keys, but not 'img' tags). */
	public List<String> getMedia() { return media; }

	/** Finds all links contained in an HTML body. */
	private Document extractLinksFromHtml(String html) {
		// parse document
		Document doc = Jsoup.parse(html);

		// media
		for (Element src : doc.select("[src]")) {
			String url = src.attr("abs:src");
			if (!url.startsWith("http"))
				continue;
			if (src.tagName().equals("img")) {
				String width = src.attr("width").trim(), height = src.attr("height").trim();
				inlineImages.add(new Image(url, width, height));
				links.add(new Link(url, LinkType.IMAGE));
			} else {
				media.add(url);
				links.add(new Link(url, LinkType.MEDIA));
			}
		}

		// imports
		for (Element link : doc.select("link[href]")) {
			String url = link.attr("abs:href");
			if (!url.startsWith("http"))
				continue;
			imports.add(url);
			links.add(new Link(url, LinkType.IMPORT));
		}

		// links
		for (Element link : doc.select("a[href]")) {
			String url = link.attr("abs:href");
			if (!url.startsWith("http"))
				continue;
			inlineLinks.add(new InlineLink(url, link.text()));
			links.add(new Link(url, LinkType.LINK));
		}

		// css
		for (Element css : doc.select("style")) {
			List<String> cssLinks = extractLinksFromCSS(css.data());
			inlineCssImages.addAll(cssLinks);
			for (String url : cssLinks)
				links.add(new Link(url, LinkType.IMAGE));
		}

		return doc;
	}

	/** Returns all links contained in a CSS body. */
	private List<String> extractLinksFromCSS(String css) {
		List<String> list = new ArrayList<String>();
		Pattern pattern = Pattern.compile("url\\((?!['\"]?(?:data):)['\"]?([^'\"\\)]*)['\"]?\\)");
		Matcher matcher = pattern.matcher(css);
		while (matcher.find()) {
			String url = matcher.group(1).trim();
			if (!url.startsWith("http"))
				continue;
			list.add(url);
		}
		return list;
	}
}
