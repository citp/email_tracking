package itdelatrisu.mailserver;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.QueryParamsMap;
import spark.Spark;

/**
 * Web server.
 */
public class WebServer {
	private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

	/** Default port. */
	private static final int DEFAULT_PORT = 8080;

	/** The database instance. */
	private final MailDB db;

	/** The email address generator. */
	private final EmailAddressGenerator generator;

	/** The mail server's domain name. */
	private final String domain;

	/** The port. */
	private final int port;

	/** Initializes the web server. */
	public WebServer(MailDB db, String domain) {
		this(db, domain, DEFAULT_PORT);
	}

	/** Initializes the web server. */
	public WebServer(MailDB db, String domain, int port) {
		this.db = db;
		this.generator = new EmailAddressGenerator();
		this.domain = domain;
		this.port = port;
		Spark.port(port);
	}

	/** Starts the server. */
	public void start() {
		Spark.post("/register", this::register);
		Spark.get("/visit", this::visit);
		Spark.post("/results", this::results);
	}

	/** Stops the server. */
	public void stop() { Spark.stop(); }

	/** Returns the server's port. */
	public int getPort() { return port; }

	/**
	 * Registers for a site, creating and returning a new email address.
	 * POST /register : site, url -> email
	 */
	private String register(spark.Request request, spark.Response response) {
		// parse request data
		QueryParamsMap map = request.queryMap();
		String site = map.value("site"), url = map.value("url");
		if (site == null || url == null)
			return badRequest(response);

		logger.info("/register: {} - {}", site, url);

		// generate an email address
		int retries = 3;  // in case generator picks a duplicate
		while (retries-- > 0) {
			String email = generator.generate(domain);
			try {
				if (db.addMailUser(email, site, url)) {
					logger.info("Created new user {}.", email);
					return email;
				}
			} catch (SQLException e) {
				logger.error("Failed to create new user.", e);
				break;
			}
		}
		return internalServerError(response);
	}

	/**
	 * Retrieves a group of URLs to visit.
	 * GET /visit -> {id: int, links: [string...]}
	 */
	private String visit(spark.Request request, spark.Response response) {
		// get a random link group
		MailDB.LinkGroup linkGroup;
		try {
			linkGroup = db.getLinkGroup();
			if (linkGroup == null) {
				response.type("application/json");
				return "{}";
			}
		} catch (SQLException e) {
			logger.error("Failed to retrieve link group.", e);
			return internalServerError(response);
		}

		logger.info("/visit -> ID {} ({} links)", linkGroup.getId(), linkGroup.getUrls().length);

		// encode the data
		JSONObject json = new JSONObject();
		json.put("id", linkGroup.getId());
		json.put("links", new JSONArray(linkGroup.getUrls()));
		response.type("application/json");
		return json.toString();
	}

	/**
	 * Submits all requests generated from a group of URLs (from {@link #visit(spark.Request, spark.Response)}).
	 * POST /results : {id: int, requests: [[url, topLevelUrl, referrer, postBody], []...]}
	 */
	private String results(spark.Request request, spark.Response response) {
		if (request.body().isEmpty())
			return badRequest(response);

		// handle GZIP encoding
		String contentEncoding = request.headers("Content-Encoding");
		String requestBody;
		if (contentEncoding != null && contentEncoding.equals("gzip")) {
			try {
				requestBody = Utils.gzipDecompress(request.bodyAsBytes());
			} catch (IOException e) {
				return internalServerError(response);
			}
		} else
			requestBody = request.body();

		// decode request data
		MailDB.LinkGroup linkGroup;
		String[][] urls;
		try {
			JSONObject json = new JSONObject(requestBody);
			if (!json.has("id") || !json.has("requests"))
				return badRequest(response);

			// parse the URL list
			JSONArray urlsJson = json.getJSONArray("requests");
			urls = new String[urlsJson.length()][4];
			for (int i = 0; i < urlsJson.length(); i++) {
				JSONArray ar = urlsJson.getJSONArray(i);
				urls[i][0] = ar.getString(0);
				urls[i][1] = ar.isNull(1) ? null : ar.getString(1);
				urls[i][2] = ar.isNull(2) ? null : ar.getString(2);
				urls[i][3] = ar.isNull(3) ? null : ar.getString(3);
			}

			// get the link group data
			int id = json.getInt("id");
			linkGroup = db.getLinkGroup(id);
			if (linkGroup == null)
				return badRequest(response);
		} catch (JSONException e) {
			return badRequest(response);
		} catch (SQLException e) {
			return internalServerError(response);
		}

		logger.info("/results: ID {} (received {} results)", linkGroup.getId(), urls.length);

		// get recipient email
		MailDB.MailUser user;
		try {
			user = db.getUserInfo(linkGroup.getRecipientId());
			if (user == null)
				return badRequest(response);
		} catch (SQLException e) {
			return internalServerError(response);
		}

		// check for leaked email address in URLs
		Set<String> baseUrls = new HashSet<String>(Arrays.asList(linkGroup.getUrls()));
		List<HashChecker.NamedValue<String>> encodings = HashChecker.getEncodings(user.getEmail());
		for (String[] container : urls) {
			String url = container[0], topLevelUrl = container[1], referrer = container[2], postBody = container[3];
			if (baseUrls.contains(url))
				continue;  // skip the base URL
			try {
				findLeakedEmailAddress(
					url, topLevelUrl, referrer, postBody,
					encodings, linkGroup.getRecipientId(), linkGroup.getSenderDomain(), linkGroup.getSenderAddress()
				);
			} catch (Exception e) {
				return internalServerError(response);
			}
		}

		// remove the link group
		try {
			db.removeLinkGroup(linkGroup.getId());
		} catch (SQLException e) {
			logger.error("Failed to remove link group.", e);
		}

		return "";
	}

	/** Finds leaked email addresses in the given data. */
	private void findLeakedEmailAddress(
		String url,
		String topLevelUrl,
		String referrer,
		String postBody,
		List<HashChecker.NamedValue<String>> encodings,
		int recipientId,
		String senderDomain,
		String senderAddress
	) throws SQLException {
		for (HashChecker.NamedValue<String> enc : encodings) {
			String type;
			boolean isIntentional;
			if (postBody != null && postBody.contains(enc.getValue())) {
				// in POST data:
				// > accidental if top-level URL leaks and occurs at least once,
				//   but intentional if the leaked email address occurs more
				//   frequently than the top-level URL (x number of occurrences)
				type = "link-post";
				if (!urlContainsString(topLevelUrl, enc.getValue()))
					isIntentional = true;
				else
					isIntentional = isValueMoreFrequentThanUrlsInString(enc.getValue(), topLevelUrl, postBody);
			} else if (url.contains(enc.getValue())) {
				// in request URL:
				// > intentional if the leak is NOT in the query parameters
				// > accidental if top level URL leaks and occurs at least once,
				//   but intentional if the leaked email address occurs more
				//   frequently than the top-level URL
				type = "link-request";
				if (!urlContainsString(topLevelUrl, enc.getValue()))
					isIntentional = true;
				else {
					try {
						URL u = new URL(url);
						if (u.getQuery() == null ||  // no query params
						    !u.getQuery().contains(enc.getValue()) ||  // not in query params
						    url.replace(u.getQuery(), "").contains(enc.getValue()))  // in non-query section
							isIntentional = true;
						else {
							// NOTE:
							// there's no point in parsing the parameters separately,
							// because many scripts just embed the page URL as a query parameter
							// without URL encoding it (so we can't tell which parameters
							// belong to which URL)
							isIntentional = isValueMoreFrequentThanUrlsInString(enc.getValue(), topLevelUrl, u.getQuery());
						}
					} catch (MalformedURLException e) {
						isIntentional = true;  // invalid URL?
					}
				}
			} else if (referrer != null && referrer.contains(enc.getValue())) {
				// in Referer header:
				// > assume accidental (we can't ever infer this was intentional)
				type = "link-referrer";
				isIntentional = false;
			} else
				continue;

			db.addLeakedEmailAddress(
				url, type, enc.getName(), true, isIntentional,
				senderDomain, senderAddress, recipientId
			);
		}
	}

	/** Returns whether the URL contains the given string. */
	private boolean urlContainsString(String url, String s) {
		if (url == null || url.isEmpty())
			return false;

		if (url.contains(s))
			return true;
		try {
			// try URL encoding/decoding on the URL
			if (URLEncoder.encode(url, "UTF-8").contains(s) ||
			    URLDecoder.decode(url, "UTF-8").contains(s))
				return true;
		} catch (Exception e) {}

		return false;
	}

	/** Returns whether the value is present in the given string more frequently than the URL. */
	private boolean isValueMoreFrequentThanUrlsInString(String value, String url, String s) {
		if (url == null || url.isEmpty())
			return true;

		String replaced = s.replace(url, "");
		try {
			// try URL encoding on the URL
			String urlEncoded = URLEncoder.encode(url, "UTF-8");
			replaced = replaced.replace(urlEncoded, "");
		} catch (Exception e) {}

		return replaced.contains(value);
	}

	/** Returns a 400 Bad Request response. */
	private String badRequest(spark.Response response) {
		response.status(400);
		return "<html><body><h2>400 Bad Request</h2></body></html>";
	}

	/** Returns a 500 Internal Server Error response. */
	private String internalServerError(spark.Response response) {
		response.status(500);
		return "<html><body><h2>500 Internal Server Error</h2></body></html>";
	}
}
