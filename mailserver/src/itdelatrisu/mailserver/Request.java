package itdelatrisu.mailserver;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles making requests to a URL.
 */
public class Request {
	/** Maximum number of HTTP/HTTPS redirects to follow. */
	private static final int MAX_REDIRECTS = 50;

	/** Connection timeout (in ms). */
	private static final int CONNECTION_TIMEOUT = 5000;

	/** Read timeout (in ms). */
	private static final int READ_TIMEOUT = 10000;

	/** The URL. */
	private final URL url;

	/** The list of redirects. */
	private final List<URL> redirects;

	/** The list of cookies. */
	private final List<HttpCookie> cookies;

	/** The last HTTP response code received. */
	private int status = -1;

	/**
	 * Thread-local cookie store.
	 * @author DavidBlackledge (http://stackoverflow.com/a/17513786)
	 */
	private static class ThreadLocalCookieStore implements CookieStore {
		private final static ThreadLocal<CookieStore> store = new ThreadLocal<CookieStore>() {
			@Override
			protected synchronized CookieStore initialValue() {
				return (new CookieManager()).getCookieStore();
			}
		};
		@Override public void add(URI uri, HttpCookie cookie) { store.get().add(uri, cookie); }
		@Override public List<HttpCookie> get(URI uri) { return store.get().get(uri); }
		@Override public List<HttpCookie> getCookies() { return store.get().getCookies(); }
		@Override public List<URI> getURIs() { return store.get().getURIs(); }
		@Override public boolean remove(URI uri, HttpCookie cookie) { return store.get().remove(uri, cookie); }
		@Override public boolean removeAll() { return store.get().removeAll(); }
	}

	/** The cookie manager. */
	private static final CookieManager cookieManager = new CookieManager(new ThreadLocalCookieStore(), null);
	static {
		CookieHandler.setDefault(cookieManager);
	}

	/**
	 * Constructor.
	 * @param url the URL to request
	 */
	public Request(String url) throws MalformedURLException {
		this.url = new URL(url);
		this.redirects = new ArrayList<URL>();
		this.cookies = new ArrayList<HttpCookie>();
	}

	/** Returns the requested URL. */
	public URL getURL() { return url; }

	/** Returns the list of redirects, in the order that they occurred. */
	public List<URL> getRedirects() { return redirects; }

	/** Returns the list of cookies (in no particular order). */
	public List<HttpCookie> getCookies() { return cookies; }

	/** Returns the last HTTP response code received. */
	public int getResponseCode() { return status; }

	/** Requests the URL.  */
	public void go() throws IOException {
		// clear previous cookies
		cookieManager.getCookieStore().removeAll();

		URL requestURL = url;
		int redirectCount = 0;
		while (true) {
			// set connection properties
			HttpURLConnection conn = (HttpURLConnection) requestURL.openConnection();
			conn.setConnectTimeout(CONNECTION_TIMEOUT);
			conn.setReadTimeout(READ_TIMEOUT);
			conn.setUseCaches(false);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
			conn.setRequestProperty("Accept-Encoding", "gzip, deflate, sdch, br");
			conn.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
			conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");

			// check for redirects
			try {
				status = conn.getResponseCode();
			} catch (SocketTimeoutException e) {
				break;  // read timed out
			}
			if (status != HttpURLConnection.HTTP_MOVED_TEMP && status != HttpURLConnection.HTTP_MOVED_PERM &&
			    status != HttpURLConnection.HTTP_SEE_OTHER && status != HttpURLConnection.HTTP_USE_PROXY) {
				conn.disconnect();
				break;
			}

			URL base = conn.getURL();
			String location = conn.getHeaderField("Location");
			URL target = (location != null) ? new URL(base, location) : null;
			conn.disconnect();

			// check for problems
			if (location == null)
				throw new IOException(String.format("Request for URL '%s' is attempting to redirect without a 'location' header.", base.toString()));
			if (!target.getProtocol().equals("http") && !target.getProtocol().equals("https"))
				throw new IOException(String.format("Request for URL '%s' is attempting to redirect to a non-HTTP/HTTPS protocol '%s'.", base.toString(), target.getProtocol()));
			if (redirectCount > MAX_REDIRECTS)
				throw new IOException(String.format("Request for URL '%s' is attempting too many redirects (over %d).", base.toString(), MAX_REDIRECTS));

			// follow redirect
			requestURL = target;
			redirects.add(target);
			redirectCount++;
		}

		// store new cookies
		for (HttpCookie c : cookieManager.getCookieStore().getCookies())
			cookies.add((HttpCookie) c.clone());
	}
}
