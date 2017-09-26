package itdelatrisu.mailserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Organization domains manager.
 */
public class OrganizationDomains {
	private static final Logger logger = LoggerFactory.getLogger(OrganizationDomains.class);

	/** Mapping from domain name -> organization. */
	private final Map<String, String> map;

	/** Initializes the organization domains mapping. */
	public OrganizationDomains() {
		this.map = loadMapping("org_domains.json");
	}

	/** Loads the organization domains mapping from the given JSON file. */
	private Map<String, String> loadMapping(String res) {
		Map<String, String> map = new HashMap<>();
		try {
			String json = Utils.resourceToString(res, StandardCharsets.UTF_8);
			JSONArray ar = new JSONArray(json);
			for (int i = 0, n = ar.length(); i < n; i++) {
				JSONObject obj = ar.getJSONObject(i);
				String org = obj.getString("organization");
				JSONArray domains = obj.getJSONArray("domains");
				for (int j = 0, m = domains.length(); j < m; j++)
					map.put(domains.getString(j), org);
			}
		} catch (IOException e) {
			logger.error("Failed to load organization domains mapping.", e);
		}
		return map;
	}

	/** Returns the organization mapped to the given domain name, or null if none. */
	public String getOrganizationForDomain(String domain) {
		return map.get(domain);
	}
}
