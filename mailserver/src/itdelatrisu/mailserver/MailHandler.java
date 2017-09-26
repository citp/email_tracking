package itdelatrisu.mailserver;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for incoming mail.
 */
public class MailHandler {
	private static final Logger logger = LoggerFactory.getLogger(MailHandler.class);

	/** The database instance. */
	private final MailDB db;

	/** The storage module instance. */
	private final MailStorage storage;

	/** The analyzer module instance. */
	private final MailAnalyzer analyzer;

	/** Creates the mail handler. */
	public MailHandler(MailDB db) {
		this.db = db;
		this.storage = new MailStorage(db);
		this.analyzer = new MailAnalyzer(db);
	}

	/** Returns whether to accept or reject this message. */
	public boolean accept(String from, String recipient) {
		// reject if email address not in database
		try {
			return db.userExists(recipient);
		} catch (SQLException e) {
			logger.error("Failed to query database.", e);
		}
		return true;
	}

	/** Handles the message. */
	public void handleMessage(String from, String recipient, String data) {
		// get user info
		MailDB.MailUser user;
		try {
			user = db.getUserInfo(recipient);
		} catch (SQLException e) {
			logger.error("Failed to query database.", e);
			return;
		}
		if (user == null) {
			logger.error("No user entry for email '{}'.", recipient);
			return;
		}

		// store mail on disk
		storage.store(from, user, data);

		// analyze mail
		analyzer.analyze(from, user, data);
	}
}
