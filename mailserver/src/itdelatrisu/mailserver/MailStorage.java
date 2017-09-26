package itdelatrisu.mailserver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Date;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Storage handler for incoming mail.
 */
public class MailStorage {
	private static final Logger logger = LoggerFactory.getLogger(MailStorage.class);

	/** Default root mail directory. */
	private static final File DEFAULT_MAIL_DIR = new File("mail");

	/** The database instance. */
	private final MailDB db;

	/** The root mail directory. */
	private final File mailDir;

	/** Initializes the storage module. */
	public MailStorage(MailDB db) {
		this(db, DEFAULT_MAIL_DIR);
	}

	/** Initializes the storage module. */
	public MailStorage(MailDB db, File rootDir) {
		this.db = db;
		this.mailDir = rootDir;
		if (!mailDir.isDirectory() && !mailDir.mkdirs())
			logger.error("Failed to create root mail directory '{}'.", mailDir.getAbsolutePath());
	}

	/** Stores the message. */
	public void store(String from, MailDB.MailUser user, String data) {
		// {root_mail_dir}/{email}/{timestamp}.eml
		File dir = new File(mailDir, Utils.cleanFileName(user.getEmail(), '_'));
		if (!dir.isDirectory() && !dir.mkdirs()) {
			logger.error("Failed to create mail directory '{}'.", dir.getAbsolutePath());
			dir = mailDir;
		}
		String filename = String.format("%d.eml", System.currentTimeMillis());
		File file = new File(dir, filename);

		// write contents to file
		try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
			writer.write(stripAttachments(data));
		} catch (IOException e) {
			logger.error("Failed to write email to disk.", e);
		}

		// write mail entry into database
		String subject = null;
		Date sentDate = null;
		try {
			MimeMessage message = Utils.toMimeMessage(data);
			subject = message.getSubject();
			sentDate = message.getSentDate();
		} catch (MessagingException e) {
			logger.error("Failed to parse message.", e);
		}
		try {
			db.addMailEntry(user.getEmail(), from, sentDate, subject, file.getName());
		} catch (SQLException e) {
			logger.error("Failed to log message to database.", e);
		}
	}

	/** Strips attachments in the given message. */
	private String stripAttachments(String data) {
		try {
			// parse MIME message
			MimeMessage message = Utils.toMimeMessage(data);
			Object content = message.getContent();
			if (!(content instanceof Multipart))
				return data;  // not a multipart message

			// strip attachments
			Multipart multipart = (Multipart) content;
			if (stripAttachments(multipart)) {
				message.setContent(multipart);
				message.saveChanges();
				return Utils.messageToString(message);
			} else
				return data;  // content unmodified
		} catch (MessagingException | IOException e) {
			logger.error("Error while stripping attachments.", e);
			return data;
		}
	}

	/**
	 * Recursively strips attachments from a multipart message,
	 * and returns whether the message was modified.
	 */
	private boolean stripAttachments(Multipart multipart) throws MessagingException, IOException {
		boolean modified = false;
		for (int i = multipart.getCount() - 1; i >= 0; i--) {
			Part part = multipart.getBodyPart(i);
			String contentType = part.getContentType();
			if (contentType.startsWith("multipart/")) {
				if (stripAttachments((Multipart) part.getContent()))
					modified = true;
			} else if (discardMimeType(contentType)) {
				modified = true;
				multipart.removeBodyPart(i);
			}
		}
		return modified;
	}

	/** Returns whether to discard content with this MIME type. */
	private boolean discardMimeType(String contentType) {
		return !contentType.startsWith("text/");
	}
}
