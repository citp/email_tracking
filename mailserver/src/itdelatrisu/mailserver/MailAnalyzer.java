package itdelatrisu.mailserver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzer for incoming mail.
 */
public class MailAnalyzer {
	private static final Logger logger = LoggerFactory.getLogger(MailAnalyzer.class);

	/** Size of the thread pool for executing requests. */
	private static final int MAX_REQUEST_THREADS = 5;

	/** Delay (in ms) before scheduling a task. */
	private static final int TASK_SCHEDULE_DELAY = 1000;

	/** Keywords for email confirmation. */
	private static final String[] EMAIL_CONFIRMATION_KEYWORDS = new String[] {
		"confirm", "verify", "validate", "activate"
	};

	/** Additional keywords in link text for email confirmation. */
	private static final String[] EMAIL_CONFIRMATION_LINK_KEYWORDS = new String[] {
		"subscribe", "click"
	};

	/** Blacklisted keywords in link text for email confirmation. */
	private static final String[] EMAIL_CONFIRMATION_LINK_BLACKLIST = new String[] {
		"unsubscribe", "view", "cancel", "deactivate"
	};

	/** Blacklisted keywords in link URLs for email confirmation. */
	private static final String[] EMAIL_CONFIRMATION_LINK_URL_BLACKLIST = new String[] {
		"unsubscribe", "deactivate"
	};

	/** Blacklisted keywords in email subject for email confirmation. */
	private static final String[] EMAIL_CONFIRMATION_SUBJECT_BLACKLIST = new String[] {
		"confirmed", "subscribed", "activated"
	};

	/** The database instance. */
	private final MailDB db;

	/** The thread pool for executing requests. */
	private final ScheduledExecutorService pool;

	/** The random number generator instance. */
	private final Random random;

	/** Task for making requests to a URL. */
	private class RequestTask implements Callable<Request> {
		private final Request req;
		private final String urlType;
		private final String senderDomain, senderAddress;
		private final int recipientId;
		private final List<HashChecker.NamedValue<String>> encodings;

		/** Creates a new request task to request the given URL. */
		public RequestTask(
			String url,
			String type,
			String senderDomain,
			String senderAddress,
			int recipientId,
			List<HashChecker.NamedValue<String>> encodings
		) throws MalformedURLException {
			this.req = new Request(url);
			this.urlType = type;
			this.senderDomain = senderDomain;
			this.senderAddress = senderAddress;
			this.recipientId = recipientId;
			this.encodings = encodings;
		}

		@Override
		public Request call() throws Exception {
			try {
				// make the request
				req.go();

				// write results into database
				db.addRedirects(req, senderDomain, senderAddress, recipientId);
				if (!req.getRedirects().isEmpty()) {
					for (URL url : req.getRedirects())
						findLeakedEmailAddress(url.toString(), urlType, encodings, true, recipientId, senderDomain, senderAddress);
				}

				return req;
			} catch (Exception e) {
				logger.error(String.format("Error raised during request for [%s].", req.getURL().toString()), e);
				throw e;
			}
		}
	}

	/** Initializes the analyzer module. */
	public MailAnalyzer(MailDB db) {
		this.db = db;
		this.pool = Executors.newScheduledThreadPool(MAX_REQUEST_THREADS);
		this.random = new Random();
	}

	/** Shuts down the executor service. */
	public void shutdown() { pool.shutdown(); }

	/** Analyzes the mail. */
	public void analyze(String from, MailDB.MailUser user, String data) {
		if (user.getRegistrationSiteDomain() == null || user.getRegistrationSiteDomain().isEmpty())
			return;

		// extract HTML from the email
		MimeMessage message;
		String html;
		LinkExtractor extractor = null;
		try {
			message = Utils.toMimeMessage(data);
			html = Utils.getHtmlFromMessage(message);
			if (html != null)
				extractor = new LinkExtractor(html);
		} catch (MessagingException | IOException e) {
			logger.error("Failed to parse message.", e);
			return;
		}

		// is this the first email?
		if (user.getReceivedEmailCount() == 0)
			findConfirmationLinksToVisit(message, extractor, from, user.getId(), user.getRegistrationSiteDomain());

		if (html == null)
			return;  // no HTML, skip everything else

		// find leaked email addresses
		List<HashChecker.NamedValue<String>> encodings = HashChecker.getEncodings(user.getEmail());
		for (LinkExtractor.Link link : extractor.getAllLinks())
			findLeakedEmailAddress(link.url, link.type.toString(), encodings, false, user.getId(), user.getRegistrationSiteDomain(), from);

		// request tracking images
		requestTrackingImages(extractor, from, user.getId(), user.getRegistrationSiteDomain(), encodings);

		// record links to visit
		recordLinksToVisit(extractor, from, user.getId(), user.getRegistrationSiteDomain(), encodings);
	}

	/** Finds leaked email addresses in the given URL. */
	private void findLeakedEmailAddress(
		String url,
		String type,
		List<HashChecker.NamedValue<String>> encodings,
		boolean isRedirect,
		int recipientId,
		String senderDomain,
		String senderAddress
	) {
		try {
			for (HashChecker.NamedValue<String> enc : encodings) {
				if (url.contains(enc.getValue())) {
					db.addLeakedEmailAddress(
						url, type, enc.getName(), isRedirect, true,
						senderDomain, senderAddress, recipientId
					);
				}
			}
		} catch (SQLException e) {
			logger.error("Failed to record leaked email address.", e);
		}
	}

	/** Makes requests for tracking images present in the message. */
	private void requestTrackingImages(
		LinkExtractor extractor,
		String from,
		int recipientId,
		String senderDomain,
		List<HashChecker.NamedValue<String>> encodings
	) {
		try {
			// make requests for:
			// - images explicitly labeled as 1x1
			// - URLs containing the recipient email address (raw or encoded)
			// - 1 random other image
			Set<String> requests = new HashSet<String>();
			List<String> nonRequestedImages = new ArrayList<String>();
			for (LinkExtractor.Image img : extractor.getInlineImages()) {
				if (img.width.equals("1") && img.height.equals("1"))
					requests.add(img.url);
				else {
					boolean added = false;
					for (HashChecker.NamedValue<String> enc : encodings) {
						if (img.url.contains(enc.getValue())) {
							requests.add(img.url);
							added = true;
							break;
						}
					}
					if (!added)
						nonRequestedImages.add(img.url);
				}
			}
			for (String img : extractor.getInlineCssImages()) {
				boolean added = false;
				for (HashChecker.NamedValue<String> enc : encodings) {
					if (img.contains(enc.getValue())) {
						requests.add(img);
						added = true;
						break;
					}
				}
				if (!added)
					nonRequestedImages.add(img);
			}
			if (!nonRequestedImages.isEmpty()) {
				String img = nonRequestedImages.get(random.nextInt(nonRequestedImages.size()));
				requests.add(img);
			}
			if (requests.isEmpty())
				return;

			// submit all requests
			for (String url : requests) {
				try {
					RequestTask task = new RequestTask(
						url, LinkExtractor.LinkType.IMAGE.toString(),
						senderDomain, from, recipientId, encodings
					);
					pool.schedule(task, TASK_SCHEDULE_DELAY, TimeUnit.MILLISECONDS);
				} catch (MalformedURLException e) {}
			}
		} catch (Exception e) {
			logger.error("Failed to request tracking images.", e);
		}
	}

	/** Records a group of links in the message to be visited. */
	private void recordLinksToVisit(
		LinkExtractor extractor,
		String from,
		int recipientId,
		String senderDomain,
		List<HashChecker.NamedValue<String>> encodings
	) {
		// visit links:
		// - up to 2 URLs from the most frequent prefix:
		//   > 1 URL containing the recipient email address (if any)
		//   > 1 other URL containing query parameters (if any)
		//   > if none matched above: the longest URL with a non-empty path (if any)
		// - 1 other URL from another prefix containing the recipient email address (if any)
		Map<String, List<String>> map = groupByPrefix(extractor.getInlineLinks());
		if (map.isEmpty())
			return;
		List<String> maxList = null;
		for (List<String> list : map.values()) {
			if (maxList == null || list.size() > maxList.size())
				maxList = list;
		}
		Collections.shuffle(maxList, random);
		List<String> urls = new ArrayList<String>();
		for (String url : maxList) {
			for (HashChecker.NamedValue<String> enc : encodings) {
				if (url.contains(enc.getValue())) {
					urls.add(url);
					break;
				}
			}
			if (!urls.isEmpty())
				break;
		}
		if (!urls.isEmpty())
			maxList.remove(urls.get(0));
		for (String url : maxList) {
			if (url.indexOf('?') != -1) {
				urls.add(url);
				break;
			}
		}
		if (urls.isEmpty()) {
			String longestUrl = null;
			for (String url : maxList) {
				int i = url.indexOf("://"), j = url.indexOf('/', (i == -1) ? 0 : i + 3);
				if (j == -1 || j == url.length() - 1)
					continue;
				if (longestUrl == null || url.length() > longestUrl.length())
					longestUrl = url;
			}
			if (longestUrl != null)
				urls.add(longestUrl);
		}
		for (List<String> list : map.values()) {
			if (list == maxList)
				continue;
			boolean added = false;
			for (String url : list) {
				for (HashChecker.NamedValue<String> enc : encodings) {
					if (url.contains(enc.getValue())) {
						urls.add(url);
						added = true;
						break;
					}
				}
				if (added)
					break;
			}
			if (added)
				break;
		}

		if (urls.isEmpty())
			return;

		// record links in database
		try {
			db.addLinkGroup(urls, senderDomain, from, recipientId);
		} catch (SQLException e) {
			logger.error("Failed to record links to visit.", e);
		}
	}

	/** Returns a map of the URLs grouped by prefix: prefix -> list(URLs). */
	private Map<String, List<String>> groupByPrefix(List<LinkExtractor.InlineLink> links) {
		// match prefixes by (in order of preference):
		// - the first "/?" sequence
		// - the '/' character before the first '?'
		// - the second '/' after the TLD
		// - the entire string
		Map<String, List<String>> map = new HashMap<String, List<String>>();
		for (LinkExtractor.InlineLink link : links) {
			String url = link.url;
			int index = url.indexOf('?');
			if (index != -1) {
				if (url.charAt(index - 1) != '/') {
					int i = url.substring(0, index - 1).lastIndexOf('/');
					if (i != -1)
						index = i;
				}
			} else {
				int i = url.indexOf("://"), j = url.indexOf('/', (i == -1) ? 0 : i + 3);
				if (j != -1)
					index = url.indexOf('/', j + 1);
			}
			String prefix = (index == -1) ? url : url.substring(0, index + 1);
			List<String> list = map.get(prefix);
			if (list == null) {
				list = new ArrayList<String>();
				map.put(prefix, list);
			}
			list.add(url);
		}
		return map;
	}

	/** Finds email confirmation links to visit. */
	private void findConfirmationLinksToVisit(
		MimeMessage message,
		LinkExtractor extractor,
		String from,
		int recipientId,
		String senderDomain
	) {
		// if keyword matches in message subject line or text body...
		// - HTML: link with a keyword in the tag text, or if only 1 link exists
		// - plain-text: longest link
		boolean subjectMatches;
		try {
			String subject = message.getSubject().toLowerCase();
			subjectMatches =
				matches(subject, EMAIL_CONFIRMATION_KEYWORDS) &&
				!matches(subject, EMAIL_CONFIRMATION_SUBJECT_BLACKLIST);
		} catch (MessagingException e) {
			logger.error("Failed to parse email to find confirmation links.", e);
			return;
		}
		String url = null;
		if (extractor != null && !extractor.getInlineLinks().isEmpty()) {
			// check HTML
			if (!subjectMatches && !matches(extractor.getDocument().text().toLowerCase(), EMAIL_CONFIRMATION_KEYWORDS))
				return;  // no keyword matches
			if (extractor.getInlineLinks().size() == 1) {
				LinkExtractor.InlineLink link = extractor.getInlineLinks().get(0);
				if (!matches(link.text.toLowerCase(), EMAIL_CONFIRMATION_LINK_BLACKLIST) &&
				    !matches(link.url.toLowerCase(), EMAIL_CONFIRMATION_LINK_URL_BLACKLIST))
					url = link.url;
			} else {
				// check if initially-matched keywords are in any link text
				for (LinkExtractor.InlineLink link : extractor.getInlineLinks()) {
					String text = link.text.toLowerCase();
					if (matches(text, EMAIL_CONFIRMATION_KEYWORDS) &&
					    !matches(text, EMAIL_CONFIRMATION_LINK_BLACKLIST) &&
					    !matches(link.url.toLowerCase(), EMAIL_CONFIRMATION_LINK_URL_BLACKLIST)) {
						url = link.url;
						break;
					}
				}
				if (url == null) {
					// look for additional less-accurate keywords
					for (LinkExtractor.InlineLink link : extractor.getInlineLinks()) {
						String text = link.text.toLowerCase();
						if (matches(text, EMAIL_CONFIRMATION_LINK_KEYWORDS) &&
						    !matches(text, EMAIL_CONFIRMATION_LINK_BLACKLIST) &&
						    !matches(link.url.toLowerCase(), EMAIL_CONFIRMATION_LINK_URL_BLACKLIST)) {
							url = link.url;
							break;
						}
					}
				}
			}
		} else {
			// check plain-text
			try {
				String text = Utils.getTextFromMessage(message);
				if (text == null)
					return;
				if (!subjectMatches && !matches(text.toLowerCase(), EMAIL_CONFIRMATION_KEYWORDS))
					return;  // no keyword matches
				if (matches(text, EMAIL_CONFIRMATION_LINK_BLACKLIST))
					return;  // don't accidentally unsubscribe...
				List<String> links = Utils.extractLinksFromText(text);
				if (links.isEmpty())
					return;
				for (String link : links) {
					if (url == null || link.length() > url.length())
						url = link;
				}
			} catch (MessagingException | IOException e) {
				return;
			}
		}
		if (url == null)
			return;

		// record links in database
		List<String> urls = new ArrayList<String>(1);
		urls.add(url);
		try {
			db.addLinkGroup(urls, senderDomain, from, recipientId);
		} catch (SQLException e) {
			logger.error("Failed to record email confirmation link to visit.", e);
		}
	}

	/** Returns whether the given string matches any given keyword. */
	private boolean matches(String s, String[] keywords) {
		for (String keyword : keywords) {
			if (s.contains(keyword))
				return true;
		}
		return false;
	}
}
