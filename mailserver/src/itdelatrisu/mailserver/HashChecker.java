package itdelatrisu.mailserver;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

import com.sangupta.murmur.Murmur1;
import com.sangupta.murmur.Murmur2;
import com.sangupta.murmur.Murmur3;

/**
 * Utility for checking for hashed strings within a text body.
 */
public class HashChecker {
	/** Represents a named value. */
	public static class NamedValue<T> {
		/** The name of the value. */
		private final String name;

		/** The value. */
		private final T value;

		/** Constructor. */
		public NamedValue(String name, T value) {
			this.name = name;
			this.value = value;
		}

		/** Returns the name of this value. */
		public String getName() { return name; }

		/** Returns the value. */
		public T getValue() { return value; }
	}

	/** Returns a list of encodings (mostly hashes) of the given string. */
	public static List<NamedValue<String>> getEncodings(String s) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		List<NamedValue<String>> list = new ArrayList<NamedValue<String>>();

		// non-hashes
		list.add(new NamedValue<String>("raw", s));
		try {
			list.add(new NamedValue<String>("urlencoded", URLEncoder.encode(s, "UTF-8")));
		} catch (UnsupportedEncodingException e) {}
		list.add(new NamedValue<String>("base64", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)));

		// common hashes
		try {
			list.add(new NamedValue<String>("md5", toHex(MessageDigest.getInstance("MD5").digest(bytes))));
		} catch (NoSuchAlgorithmException e) {}
		try {
			list.add(new NamedValue<String>("sha1", toHex(MessageDigest.getInstance("SHA-1").digest(bytes))));
		} catch (NoSuchAlgorithmException e) {}
		try {
			list.add(new NamedValue<String>("sha224", toHex(MessageDigest.getInstance("SHA-224").digest(bytes))));
		} catch (NoSuchAlgorithmException e) {}
		try {
			list.add(new NamedValue<String>("sha256", toHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
		} catch (NoSuchAlgorithmException e) {}
		try {
			list.add(new NamedValue<String>("sha384", toHex(MessageDigest.getInstance("SHA-384").digest(bytes))));
		} catch (NoSuchAlgorithmException e) {}
		try {
			list.add(new NamedValue<String>("sha512", toHex(MessageDigest.getInstance("SHA-512").digest(bytes))));
		} catch (NoSuchAlgorithmException e) {}

		// checksums
		CRC32 crc = new CRC32();
		crc.update(bytes);
		Adler32 adler = new Adler32();
		adler.update(bytes);
		list.add(new NamedValue<String>("crc32", Long.toString(crc.getValue())));
		list.add(new NamedValue<String>("crc32-hex", Long.toHexString(crc.getValue())));
		list.add(new NamedValue<String>("adler32", Long.toString(adler.getValue())));
		list.add(new NamedValue<String>("adler32-hex", Long.toHexString(adler.getValue())));

		// murmur hashes
		long murmur1 = Murmur1.hash(bytes, bytes.length, 0);
		long murmur2 = Murmur2.hash(bytes, bytes.length, 0);
		long murmur2_64 = Murmur2.hash64(bytes, bytes.length, 0);
		long murmur3_32 = Murmur3.hash_x86_32(bytes, bytes.length, 0);
		long[] murmur3_128 = Murmur3.hash_x64_128(bytes, bytes.length, 0);
		list.add(new NamedValue<String>("mmh1", Long.toString(murmur1)));
		list.add(new NamedValue<String>("mmh1-hex", Long.toHexString(murmur1)));
		list.add(new NamedValue<String>("mmh2", Long.toString(murmur2)));
		list.add(new NamedValue<String>("mmh2-hex", Long.toHexString(murmur2)));
		list.add(new NamedValue<String>("mmh2-64", Long.toString(murmur2_64)));
		list.add(new NamedValue<String>("mmh2-64-hex", Long.toHexString(murmur2_64)));
		list.add(new NamedValue<String>("mmh3-32", Long.toString(murmur3_32)));
		list.add(new NamedValue<String>("mmh3-32-hex", Long.toHexString(murmur3_32)));
		list.add(new NamedValue<String>("mmh3-128-1", Long.toString(murmur3_128[0])));
		list.add(new NamedValue<String>("mmh3-128-1-hex", Long.toHexString(murmur3_128[0])));
		list.add(new NamedValue<String>("mmh3-128-2", Long.toString(murmur3_128[1])));
		list.add(new NamedValue<String>("mmh3-128-2-hex", Long.toHexString(murmur3_128[1])));

		return list;
	}

	/** Converts the byte array into a hexadecimal string. */
	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			String hex = Integer.toHexString(bytes[i] & 0xff);
			if (hex.length() == 1)
				sb.append('0');
			sb.append(hex);
		}
		return sb.toString();
	}
}
