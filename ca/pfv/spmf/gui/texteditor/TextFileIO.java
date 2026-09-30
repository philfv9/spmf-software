package ca.pfv.spmf.gui.texteditor;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
 * This file is part of the SPMF DATA MINING SOFTWARE (http://www.philippe-fournier-viger.com/spmf).
 * SPMF is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version. SPMF is distributed WITHOUT ANY
 * WARRANTY. See the GNU General Public License for more details.
 */

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reliable loading/saving of text files for the SPMF text editor:
 * <ul>
 * <li>strict UTF-8 decoding with a lossless ISO-8859-1 fallback,</li>
 * <li>UTF-8 BOM detection/preservation,</li>
 * <li>line-ending detection/preservation (CRLF, LF, CR),</li>
 * <li>atomic saves (write to a temporary file, then rename).</li>
 * </ul>
 * 
 * @author Philippe Fournier-Viger
 */
final class TextFileIO {

	static final String LF = "\n";
	static final String CRLF = "\r\n";
	static final String CR = "\r";

	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	/** Result of loading a file */
	static final class LoadedText {
		/** Text with line endings normalized to \n */
		final String text;
		/** Charset the file was decoded with */
		final Charset charset;
		/** Line separator found in the file */
		final String lineSeparator;
		/** True if the file started with a UTF-8 BOM */
		final boolean hasBom;
		/** Last-modified timestamp of the file when it was read */
		final long lastModified;

		LoadedText(String text, Charset charset, String lineSeparator, boolean hasBom, long lastModified) {
			this.text = text;
			this.charset = charset;
			this.lineSeparator = lineSeparator;
			this.hasBom = hasBom;
			this.lastModified = lastModified;
		}
	}

	/** Result of saving a file */
	static final class SaveResult {
		/** Charset actually used (may differ from the requested one) */
		final Charset charset;
		final long lastModified;
		final long size;

		SaveResult(Charset charset, long lastModified, long size) {
			this.charset = charset;
			this.lastModified = lastModified;
			this.size = size;
		}
	}

	private TextFileIO() {
	}

	/**
	 * Load a text file.
	 */
	static LoadedText load(File file) throws IOException {
		byte[] bytes = Files.readAllBytes(file.toPath());

		boolean hasBom = bytes.length >= 3 && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1]
				&& bytes[2] == UTF8_BOM[2];
		int offset = hasBom ? 3 : 0;

		Charset charset = StandardCharsets.UTF_8;
		String text;
		try {
			text = decodeStrict(bytes, offset, charset);
		} catch (CharacterCodingException e) {
			// Not valid UTF-8: ISO-8859-1 maps every byte to exactly one char, so the
			// round trip is lossless.
			charset = StandardCharsets.ISO_8859_1;
			text = new String(bytes, offset, bytes.length - offset, charset);
		}

		String separator = detectLineSeparator(text);
		text = normalizeLineEndings(text);

		return new LoadedText(text, charset, separator, hasBom, file.lastModified());
	}

	/**
	 * Save text to a file. If the text cannot be represented in the requested
	 * charset, UTF-8 is used instead (see {@link SaveResult#charset}).
	 * 
	 * @param text          text with \n line endings
	 * @param charset       requested charset
	 * @param lineSeparator line separator to write
	 * @param writeBom      write a UTF-8 BOM (only honoured for UTF-8)
	 */
	static SaveResult save(File file, String text, Charset charset, String lineSeparator, boolean writeBom)
			throws IOException {
		String out = LF.equals(lineSeparator) ? text : text.replace(LF, lineSeparator);

		Charset used = charset;
		byte[] body;
		try {
			body = encodeStrict(out, charset);
		} catch (CharacterCodingException e) {
			used = StandardCharsets.UTF_8;
			body = out.getBytes(StandardCharsets.UTF_8);
		}

		byte[] data = body;
		if (writeBom && StandardCharsets.UTF_8.equals(used)) {
			data = new byte[body.length + UTF8_BOM.length];
			System.arraycopy(UTF8_BOM, 0, data, 0, UTF8_BOM.length);
			System.arraycopy(body, 0, data, UTF8_BOM.length, body.length);
		}

		writeAtomically(file.toPath(), data);
		return new SaveResult(used, file.lastModified(), file.length());
	}

	/**
	 * Detect the line separator used by a text (defaults to the platform one).
	 */
	static String detectLineSeparator(String text) {
		int cr = text.indexOf('\r');
		int lf = text.indexOf('\n');
		if (cr < 0 && lf < 0) {
			return System.lineSeparator();
		}
		if (cr < 0) {
			return LF;
		}
		if (lf == cr + 1) {
			return CRLF;
		}
		return (lf < 0 || cr < lf) ? CR : LF;
	}

	/** Convert all line endings to \n */
	static String normalizeLineEndings(String text) {
		if (text.indexOf('\r') < 0) {
			return text;
		}
		return text.replace(CRLF, LF).replace(CR, LF);
	}

	private static String decodeStrict(byte[] bytes, int offset, Charset charset) throws CharacterCodingException {
		CharsetDecoder decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		return decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
	}

	private static byte[] encodeStrict(String text, Charset charset) throws CharacterCodingException {
		CharsetEncoder encoder = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		ByteBuffer buffer = encoder.encode(java.nio.CharBuffer.wrap(text));
		byte[] result = new byte[buffer.remaining()];
		buffer.get(result);
		return result;
	}

	/**
	 * Write data to a temporary file in the same directory and rename it over the
	 * target, so that the target is never left half-written. Falls back to a
	 * direct write when a temporary file / rename is not possible.
	 */
	private static void writeAtomically(Path target, byte[] data) throws IOException {
		Path absolute = target.toAbsolutePath();
		Path dir = absolute.getParent();

		// Replacing a symbolic link would turn it into a regular file: write through it
		if (dir == null || Files.isSymbolicLink(absolute)) {
			Files.write(absolute, data);
			return;
		}

		Path tmp;
		try {
			tmp = Files.createTempFile(dir, absolute.getFileName().toString(), ".tmp");
		} catch (IOException e) {
			// No permission to create files in the directory: write directly
			Files.write(absolute, data);
			return;
		}

		try {
			Files.write(tmp, data);
			try {
				Files.move(tmp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (AtomicMoveNotSupportedException e) {
				// try a non-atomic move below
			} catch (IOException e) {
				// e.g. target locked by another process: fall back to a direct write
			}
			try {
				Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (IOException e) {
				// fall back to a direct write, which reports the real error if any
			}
			Files.write(absolute, data);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}
}