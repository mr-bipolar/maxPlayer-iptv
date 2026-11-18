package com.aaronmaxlab.maxplayer.playlist;

import com.aaronmaxlab.maxplayer.playlist.models.PlaylistInfo;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PlaylistParserSecurityTest {

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertFails(ThrowingRunnable action) {
        try {
            action.run();
            fail("Expected an exception");
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable expected) {
            // Expected.
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /** Generates a byte stream of the requested length without allocating a matching array. */
    private static InputStream repeatedBytes(final int length) {
        return new InputStream() {
            private int remaining = length;

            @Override
            public int read() {
                if (remaining == 0) return -1;
                remaining--;
                return 'a';
            }

            @Override
            public int read(byte[] buffer, int offset, int count) {
                if (count == 0) return 0;
                if (remaining == 0) return -1;

                int size = Math.min(count, remaining);
                Arrays.fill(buffer, offset, offset + size, (byte) 'a');
                remaining -= size;
                return size;
            }
        };
    }

    private static int readExactly(InputStream in, int count) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        while (total < count) {
            int read = in.read(buffer, 0, Math.min(buffer.length, count - total));
            if (read < 0) break;
            total += read;
        }
        return total;
    }

    private static boolean hasDeclaredMethod(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) return true;
        }
        return false;
    }

    private static PlaylistParser.ValidatedUri uri(String value) throws IOException {
        return PlaylistParser.parseValidatedHttpUri(value);
    }

    private static Response.Builder baseResponse(int code) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://example.com/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test");
    }

    private static Response redirect(String location) {
        Response.Builder builder = baseResponse(302);
        if (location != null) {
            builder.header("Location", location);
        }
        return builder.build();
    }

    private static Response bodyResponse(byte[] body, String... headers) {
        Response.Builder builder = baseResponse(200);
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return builder.body(ResponseBody.create(MediaType.parse("text/plain"), body)).build();
    }

    private static Response contentLengthResponse(String value) {
        return baseResponse(200).header("Content-Length", value).build();
    }

    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            out.append(value);
        }
        return out.toString();
    }

    @Test
    public void acceptsAsciiHttpAndHttpsCanonicalization() throws Exception {
        PlaylistParser.ValidatedUri http = uri("HTTP://Example.COM:80/list.m3u?a=1#frag");

        assertEquals("http", http.scheme);
        assertEquals("example.com", http.canonicalHost);
        assertEquals("example.com", http.hostForUrl);
        assertEquals(80, http.port);
        assertEquals("/list.m3u", http.rawPath);
        assertEquals("a=1", http.rawQuery);
        assertEquals("http://example.com/list.m3u?a=1", http.canonicalUrl);
        assertFalse(http.ipLiteral);
        assertNull(http.literalAddress);

        PlaylistParser.ValidatedUri https = uri("https://example.com");
        assertEquals("https", https.scheme);
        assertEquals(443, https.port);
        assertEquals("https://example.com/", https.canonicalUrl);
    }

    @Test
    public void acceptsPublicIpv4Literal() throws Exception {
        PlaylistParser.ValidatedUri parsed = uri("http://8.8.8.8:8080/list.m3u");

        assertEquals("8.8.8.8", parsed.canonicalHost);
        assertEquals("http://8.8.8.8:8080/list.m3u", parsed.canonicalUrl);
        assertTrue(parsed.ipLiteral);
        assertNotNull(parsed.literalAddress);
    }

    @Test
    public void acceptsPublicIpv6LiteralAndCanonicalizesIt() throws Exception {
        PlaylistParser.ValidatedUri parsed =
                uri("http://[2001:4860:4860:0:0:0:0:8888]/list.m3u");

        assertEquals("2001:4860:4860::8888", parsed.canonicalHost);
        assertEquals("[2001:4860:4860::8888]", parsed.hostForUrl);
        assertEquals("http://[2001:4860:4860::8888]/list.m3u", parsed.canonicalUrl);
        assertTrue(parsed.ipLiteral);
        assertNotNull(parsed.literalAddress);
    }

    @Test
    public void acceptsIdnHostViaRawAuthority() throws Exception {
        PlaylistParser.ValidatedUri parsed = uri("http://bücher.example/list.m3u");

        assertEquals("xn--bcher-kva.example", parsed.canonicalHost);
        assertEquals("http://xn--bcher-kva.example/list.m3u", parsed.canonicalUrl);
        assertFalse(parsed.ipLiteral);
    }

    @Test
    public void rejectsUnsafeUrls() {
        String[] badUrls = {
                "ftp://example.com/list.m3u",
                "file:/etc/passwd",
                "/relative/list.m3u",
                "http:///list.m3u",
                "http://user:pass@example.com/list.m3u",
                "http://exa%6dple.com/list.m3u",
                "http://example.com/a%00b",
                "http://example.com/a%1fb",
                "http://example.com/café.m3u",
                "http://127.0.0.1/list.m3u",
                "http://10.0.0.1/list.m3u",
                "http://169.254.169.254/latest/meta-data",
                "http://[::1]/list.m3u",
                "http://[fe80::1]/list.m3u",
                "http://[fe80::1%25eth0]/list.m3u",
                "http://2130706433/list.m3u",
                "http://127.1/list.m3u",
                "http://0177.0.0.1/list.m3u",
                "http://0x7f000001/list.m3u",
                "http://0x7f.0.0.1/list.m3u",
                "http://example.com:0/list.m3u",
                "http://example.com:70000/list.m3u",
                "http://example.com.:80/list.m3u",
                "http://-bad.example/list.m3u",
                "http://bad-.example/list.m3u"
        };

        for (final String badUrl : badUrls) {
            assertFails(() -> uri(badUrl));
        }
    }

    @Test
    public void pinningDnsRejectsUnexpectedHostnameAndProhibitedAnswers() throws Exception {
        Dns publicDns = hostname -> Collections.singletonList(InetAddress.getByName("8.8.8.8"));
        PlaylistParser.PinningDns dns = new PlaylistParser.PinningDns("example.com", publicDns);

        assertFails(() -> dns.lookup("evil.example"));

        List<InetAddress> answers = dns.lookup("EXAMPLE.com");
        assertEquals(1, answers.size());
        assertEquals(InetAddress.getByName("8.8.8.8"), answers.get(0));

        Dns loopbackDns = hostname -> Collections.singletonList(InetAddress.getByName("127.0.0.1"));
        PlaylistParser.PinningDns blocked = new PlaylistParser.PinningDns("example.com", loopbackDns);
        assertFails(() -> blocked.lookup("example.com"));

        PlaylistParser.PinningDns empty =
                new PlaylistParser.PinningDns("example.com", hostname -> Collections.emptyList());
        assertFails(() -> empty.lookup("example.com"));
    }

    @Test
    public void addressPolicyBlocksSpecialPurposeRanges() throws Exception {
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("0.0.0.0")));
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("10.1.2.3")));
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("100.64.0.1")));
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("169.254.1.1")));
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("192.168.1.1")));
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("224.0.0.1")));
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("::1")));
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("fe80::1")));
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("fd00::1")));
        assertTrue(PlaylistParser.isProhibitedAddress(InetAddress.getByName("::ffff:127.0.0.1")));

        assertFalse(PlaylistParser.isProhibitedAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(PlaylistParser.isProhibitedAddress(InetAddress.getByName("2001:4860:4860::8888")));
    }

    @Test
    public void prefixMatchingUsesExactBitBoundaries() {
        assertTrue(PlaylistParser.matchesPrefix(
                new byte[]{10, 1, 2, 3}, new byte[]{10, 0, 0, 0}, 8));
        assertFalse(PlaylistParser.matchesPrefix(
                new byte[]{11, 1, 2, 3}, new byte[]{10, 0, 0, 0}, 8));

        assertTrue(PlaylistParser.matchesPrefix(
                new byte[]{(byte) 100, 64, 0, 1}, new byte[]{100, 64, 0, 0}, 10));
        assertFalse(PlaylistParser.matchesPrefix(
                new byte[]{(byte) 100, (byte) 128, 0, 1}, new byte[]{100, 64, 0, 0}, 10));

        assertTrue(PlaylistParser.matchesPrefix(
                new byte[]{0x3f, (byte) 0xff, (byte) 0xff, 0},
                new byte[]{0x3f, (byte) 0xff, (byte) 0xf0}, 20));
        assertFalse(PlaylistParser.matchesPrefix(
                new byte[]{0x3f, (byte) 0xff, 0x0f, 0},
                new byte[]{0x3f, (byte) 0xff, (byte) 0xf0}, 20));
    }

    @Test
    public void limitedStreamAllowsExactLimitButRejectsOneMoreByte() throws Exception {
        PlaylistParser.LimitedInputStream exact = new PlaylistParser.LimitedInputStream(
                new ByteArrayInputStream(new byte[9]), 9);
        assertEquals(9, exact.read(new byte[16]));
        assertEquals(-1, exact.read());

        PlaylistParser.LimitedInputStream over = new PlaylistParser.LimitedInputStream(
                new ByteArrayInputStream(new byte[10]), 9);
        assertEquals(9, over.read(new byte[16]));
        assertFails(() -> over.read());
    }

    @Test
    public void limitedStreamEnforcesPlaylistByteLimit() throws Exception {
        int limit = 16 * 1024 * 1024;

        PlaylistParser.LimitedInputStream exact = new PlaylistParser.LimitedInputStream(
                repeatedBytes(limit), limit);
        assertEquals(limit, readExactly(exact, limit));
        assertEquals(-1, exact.read());

        PlaylistParser.LimitedInputStream over = new PlaylistParser.LimitedInputStream(
                repeatedBytes(limit + 1), limit);
        assertEquals(limit, readExactly(over, limit));
        assertFails(over::read);
    }

    @Test
    public void byteLineReaderHandlesCrLfUtf8AndEnforcesByteLineLimit() throws Exception {
        PlaylistParser.ByteLineReader reader = new PlaylistParser.ByteLineReader(
                new ByteArrayInputStream(bytes("#EXTINF:-1,Кanal\r\nhttp://example.com/stream\n")));
        assertEquals("#EXTINF:-1,Кanal", reader.readLine());
        assertEquals("http://example.com/stream", reader.readLine());
        assertNull(reader.readLine());

        int limit = 64 * 1024;
        byte[] exactLine = new byte[limit + 1];
        Arrays.fill(exactLine, 0, limit, (byte) 'a');
        exactLine[limit] = '\n';
        PlaylistParser.ByteLineReader exact = new PlaylistParser.ByteLineReader(
                new ByteArrayInputStream(exactLine));
        assertEquals(limit, exact.readLine().length());
        assertNull(exact.readLine());

        byte[] longLine = new byte[limit + 2];
        Arrays.fill(longLine, 0, limit + 1, (byte) 'a');
        longLine[limit + 1] = '\n';
        PlaylistParser.ByteLineReader tooLong = new PlaylistParser.ByteLineReader(
                new ByteArrayInputStream(longLine));
        assertFails(tooLong::readLine);
    }

    @Test
    public void remoteParserPreservesExistingM3uAndHlsBehavior() throws Exception {
        String m3u = "#EXTM3U\n"
                + "#EXTGRP:News\n"
                + "#EXTINF:-1 tvg-logo=\"http://logo/a.png\" group-title=\"Sports\",Channel A\n"
                + "http://stream/a\n"
                + "# a comment\n"
                + "#EXTINF:-1,Channel B\n"
                + "http://stream/b\n";

        PlaylistInfo info = PlaylistParser.parseRemoteLines(
                new PlaylistParser.ByteLineReader(new ByteArrayInputStream(bytes(m3u))),
                "list.m3u");

        assertTrue(info.isPlaylist);
        assertFalse(info.isHlsManifest);
        assertEquals("M3U", info.format);
        assertEquals(2, info.channelCount);
        assertEquals(2, info.channels.size());
        assertEquals("Channel A", info.channels.get(0).name);
        assertEquals("http://stream/a", info.channels.get(0).url);
        assertEquals("Sports", info.channels.get(0).category);
        assertEquals("http://logo/a.png", info.channels.get(0).logoUrl);
        assertEquals("", info.channels.get(1).category);
        assertTrue(info.categoryNames.contains("News"));
        assertTrue(info.categoryNames.contains("Sports"));

        String hls = "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:10,\nsegment.ts\n";
        PlaylistInfo hlsInfo = PlaylistParser.parseRemoteLines(
                new PlaylistParser.ByteLineReader(new ByteArrayInputStream(bytes(hls))),
                "list.m3u8");

        assertTrue(hlsInfo.isHlsManifest);
        assertEquals("HLS M3U8", hlsInfo.format);
        assertEquals(0, hlsInfo.channelCount);
        assertTrue(hlsInfo.channels.isEmpty());
        assertTrue(hlsInfo.categoryNames.isEmpty());
    }

    @Test
    public void remoteParserAcceptsExactlyMaximumEntries() throws Exception {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < 80_000; i++) {
            playlist.append("#EXTINF:-1,Channel ").append(i).append('\n');
            playlist.append("http://stream/").append(i).append('\n');
        }

        PlaylistInfo info = PlaylistParser.parseRemoteLines(
                new PlaylistParser.ByteLineReader(
                        new ByteArrayInputStream(bytes(playlist.toString()))),
                "list.m3u");

        assertFalse(info.isHlsManifest);
        assertEquals(80_000, info.channelCount);
        assertEquals(80_000, info.channels.size());
    }

    @Test
    public void remoteParserRejectsEntryOverMaximum() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < 80_001; i++) {
            playlist.append("#EXTINF:-1,Channel\n");
        }

        byte[] body = bytes(playlist.toString());
        assertFails(() -> PlaylistParser.parseRemoteLines(
                new PlaylistParser.ByteLineReader(new ByteArrayInputStream(body)),
                "list.m3u"));
    }

    @Test
    public void responseValidationRejectsEncodingAndSizeAbuse() throws Exception {
        Response gzip = bodyResponse(bytes("#EXTM3U\n"), "Content-Encoding", "gzip");
        assertFails(() -> PlaylistParser.parseRemoteResponse(gzip, "list.m3u"));

        Response identity = bodyResponse(bytes("#EXTM3U\n"), "Content-Encoding", "identity");
        assertNotNull(PlaylistParser.parseRemoteResponse(identity, "list.m3u"));

        Response oversized = baseResponse(200)
                .header("Content-Length", String.valueOf(16 * 1024 * 1024 + 1))
                .build();
        assertFails(() -> PlaylistParser.validateResponseHeaders(oversized));

        Response duplicateLength = baseResponse(200)
                .addHeader("Content-Length", "1")
                .addHeader("Content-Length", "2")
                .build();
        assertFails(() -> PlaylistParser.validateResponseHeaders(duplicateLength));

        Response badTransferEncoding = baseResponse(200)
                .header("Transfer-Encoding", "deflate")
                .build();
        assertFails(() -> PlaylistParser.validateResponseHeaders(badTransferEncoding));

        Response conflicting = baseResponse(200)
                .header("Content-Length", "10")
                .header("Transfer-Encoding", "chunked")
                .build();
        assertFails(() -> PlaylistParser.validateResponseHeaders(conflicting));
    }

    @Test
    public void responseValidationAcceptsExactPlaylistByteLimit() throws Exception {
        Response exact = baseResponse(200)
                .header("Content-Length", String.valueOf(16 * 1024 * 1024))
                .build();

        PlaylistParser.validateResponseHeaders(exact);
    }

    @Test
    public void contentLengthValidationAcceptsNormalValues() throws Exception {
        PlaylistParser.validateResponseHeaders(contentLengthResponse("0"));
        PlaylistParser.validateResponseHeaders(contentLengthResponse("1"));
        PlaylistParser.validateResponseHeaders(contentLengthResponse("123456"));
        PlaylistParser.validateResponseHeaders(contentLengthResponse(String.valueOf(16 * 1024 * 1024)));
    }

    @Test
    public void contentLengthValidationRejectsFirstByteOverLimit() {
        String overLimit = String.valueOf(16 * 1024 * 1024 + 1);

        assertFails(() -> PlaylistParser.validateResponseHeaders(
                contentLengthResponse(overLimit)));
    }

    @Test
    public void contentLengthValidationRejectsLongMaxValueAsOverLimit() {
        String maxLong = String.valueOf(Long.MAX_VALUE);

        assertFails(() -> PlaylistParser.validateResponseHeaders(
                contentLengthResponse(maxLong)));
    }

    @Test
    public void contentLengthValidationRejectsLongOverflowWithoutWrapping() {
        // Values that would wrap to a small/negative long if the old arithmetic were used.
        String[] overflowing = {
                "9223372036854775808",       // Long.MAX_VALUE + 1
                "18446744073709551616",      // 2^64, would wrap to 0
                "18446744073709551617",      // 2^64 + 1, would wrap to 1
                repeat('9', 1000)
        };

        for (final String value : overflowing) {
            assertFails(() -> PlaylistParser.validateResponseHeaders(
                    contentLengthResponse(value)));
        }
    }

    @Test
    public void contentLengthValidationRejectsMalformedValues() {
        // OkHttp trims surrounding whitespace from header values, so use values
        // that remain malformed after header normalization.
        String[] malformed = {"", "-1", "+10", "abc", "12x", "1.5"};

        for (final String value : malformed) {
            assertFails(() -> PlaylistParser.validateResponseHeaders(
                    contentLengthResponse(value)));
        }
    }

    @Test
    public void redirectValidationResolvesRelativeUrlsAndRejectsUnsafeTargets() throws Exception {
        PlaylistParser.ValidatedUri next = PlaylistParser.resolveRedirect(
                uri("https://example.com/a/list.m3u8"),
                redirect("../../other/next.m3u?x=1"));

        assertEquals("https://example.com/other/next.m3u?x=1", next.canonicalUrl);

        PlaylistParser.ValidatedUri current = uri("https://example.com/a/list.m3u8");
        assertFails(() -> PlaylistParser.resolveRedirect(current, redirect("http://example.com/x")));
        assertFails(() -> PlaylistParser.resolveRedirect(current, redirect("http://127.0.0.1/x")));
        assertFails(() -> PlaylistParser.resolveRedirect(current, redirect(null)));

        Response multiple = baseResponse(302)
                .addHeader("Location", "/one.m3u")
                .addHeader("Location", "/two.m3u")
                .build();
        assertFails(() -> PlaylistParser.resolveRedirect(current, multiple));
    }

    @Test
    public void remoteAddFlowHasNoSecondaryHeadMetadataLoader() throws Exception {
        Class<?> playlist = Class.forName("com.aaronmaxlab.maxplayer.utils.Playlist");

        // The old unvalidated HEAD helper must stay absent: its removal is what prevents
        // the remote add flow from issuing a second, unprotected network request.
        assertFalse(hasDeclaredMethod(playlist, "loadRemoteMetadata"));

        // Local-file metadata handling is unrelated and must remain intact.
        assertTrue(hasDeclaredMethod(playlist, "loadFileMetadata"));
    }
}
