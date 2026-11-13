package edu.iitp.landstack.files;

import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Determines the real file type from content (magic bytes / structure), never from the extension or
 * MIME header alone, and verifies it matches the declared extension.
 */
public final class FileTypeDetector {
    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final byte[] MZ = {0x4D, 0x5A};
    private static final byte[] ELF = {0x7F, 0x45, 0x4C, 0x46};

    private FileTypeDetector() {}

    /** @param extension lower-case extension of the sanitized original filename */
    public static DetectedType detect(Path file, String extension) throws IOException {
        DetectedType expected = switch (extension) {
            case "csv" -> DetectedType.CSV;
            case "xlsx" -> DetectedType.XLSX;
            case "pdf" -> DetectedType.PDF;
            case "xls" -> throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Legacy .xls workbooks are not supported. Open the file in Excel/LibreOffice and save it as .xlsx.");
            case "xlsm", "xlsb" -> throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Macro-enabled or binary workbooks are not accepted. Save the workbook as a plain .xlsx file.");
            default -> throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Unsupported file type. Allowed formats: .csv, .xlsx, .pdf.");
        };
        byte[] head = readHead(file, 8192);
        if (startsWith(head, MZ) || startsWith(head, ELF)) {
            throw new ApiException(ErrorCode.FILE_SIGNATURE_MISMATCH, "Executable content is not accepted.");
        }
        switch (expected) {
            case PDF -> {
                if (!containsWithin(head, PDF, 1024)) throw mismatch("PDF");
                if (looksEncrypted(file)) {
                    throw new ApiException(ErrorCode.FILE_UNREADABLE,
                            "The PDF is password-protected or encrypted. Upload an unprotected copy.");
                }
            }
            case XLSX -> {
                if (startsWith(head, OLE2)) {
                    throw new ApiException(ErrorCode.FILE_SIGNATURE_MISMATCH,
                            "This file is a legacy or encrypted Excel workbook, not .xlsx. Save it as an unprotected .xlsx file.");
                }
                if (!startsWith(head, ZIP)) throw mismatch("Excel (.xlsx)");
                verifyXlsxStructure(file);
            }
            case CSV -> {
                if (startsWith(head, ZIP) || startsWith(head, PDF) || startsWith(head, OLE2)) throw mismatch("CSV");
                verifyText(head);
            }
        }
        return expected;
    }

    private static ApiException mismatch(String kind) {
        return new ApiException(ErrorCode.FILE_SIGNATURE_MISMATCH,
                "The file content does not match its extension; it is not a valid " + kind + " file.");
    }

    private static void verifyXlsxStructure(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.size() > 10_000) throw mismatch("Excel (.xlsx)");
            ZipEntry types = zip.getEntry("[Content_Types].xml");
            ZipEntry workbook = zip.getEntry("xl/workbook.xml");
            if (types == null || workbook == null) throw mismatch("Excel (.xlsx)");
            if (zip.getEntry("xl/vbaProject.bin") != null) {
                throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Workbooks containing macros are not accepted.");
            }
        } catch (IOException e) {
            throw new ApiException(ErrorCode.FILE_UNREADABLE, "The Excel file is corrupt or could not be opened.");
        }
    }

    private static void verifyText(byte[] head) {
        int len = head.length;
        if (len == 0) throw new ApiException(ErrorCode.FILE_UNREADABLE, "The file is empty.");
        for (int i = 0; i < len; i++) {
            if (head[i] == 0) throw new ApiException(ErrorCode.FILE_SIGNATURE_MISMATCH,
                    "The file contains binary data and is not a valid CSV text file.");
        }
        // Must decode as UTF-8 (ignoring a possibly cut multi-byte char at the end) or be single-byte text.
        int checkLen = Math.max(0, len - 4);
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(head, 0, checkLen));
        } catch (CharacterCodingException e) {
            // Accept legacy single-byte encodings (Windows-1252); the parser reports the fallback.
        }
    }

    private static boolean looksEncrypted(Path file) throws IOException {
        long size = Files.size(file);
        int tail = (int) Math.min(size, 65536);
        byte[] buf = new byte[tail];
        try (var ch = Files.newByteChannel(file)) {
            ch.position(size - tail);
            ByteBuffer bb = ByteBuffer.wrap(buf);
            while (bb.hasRemaining() && ch.read(bb) > 0) { /* fill */ }
        }
        String s = new String(buf, StandardCharsets.ISO_8859_1);
        return s.contains("/Encrypt");
    }

    static byte[] readHead(Path file, int n) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] b = in.readNBytes(n);
            return Arrays.copyOf(b, b.length);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    private static boolean containsWithin(byte[] data, byte[] needle, int limit) {
        int max = Math.min(data.length, limit) - needle.length;
        outer:
        for (int i = 0; i <= max; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }
}
