package com.ctrlscript.csua;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.net.*;
import java.util.*;

public class ModuleNetwork implements CsuaModule {

    private final Bootstrap _ctx;
    private final Map<Integer, WebSocketClient> _sockets = new HashMap<>();

    public ModuleNetwork(Bootstrap ctx) { _ctx = ctx; }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "fetch":     _fetch(s(args[0]), s(args[1]), s(args[2])); return null;
            case "wsConnect": _wsConnect(_int(args[0]), s(args[1]));      return null;
            case "wsSend":    _wsSend(_int(args[0]), s(args[1]));         return null;
            case "wsClose":   _wsClose(_int(args[0]));                    return null;
            case "loadImage": _loadImage((ImageView) args[0], s(args[1]));return null;
        }
        return null;
    }

    // ── HTTP fetch ───────────────────────────────────────

    private void _fetch(String cbId, String url, String optsJson) {
        new Thread(() -> {
            try {
                JSONObject opts    = optsJson.isEmpty() ? new JSONObject() : new JSONObject(optsJson);
                String method      = opts.optString("method", "GET").toUpperCase();
                String body        = opts.optString("body", null);
                JSONObject headers = opts.optJSONObject("headers");

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "CSUA/1.0");

                if (headers != null) {
                    Iterator<String> keys = headers.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        conn.setRequestProperty(k, headers.getString(k));
                    }
                }

                if (body != null) {
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.getBytes("UTF-8"));
                    }
                }

                int status = conn.getResponseCode();
                InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String responseBody = is != null ? _readStream(is) : "";

                // Build headers JSON
                JSONObject respHeaders = new JSONObject();
                for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
                    if (e.getKey() != null) respHeaders.put(e.getKey(), e.getValue().get(0));
                }

                String result = new JSONObject()
                    .put("status",  status)
                    .put("headers", respHeaders.toString())
                    .put("body",    responseBody)
                    .toString();

                _ctx.fireRawCallback(cbId, "[null," + status + ",'" +
                    respHeaders.toString().replace("'","\\'") + "','" +
                    responseBody.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n") + "']");

            } catch (Exception e) {
                _ctx.fireCallback(cbId, e.getMessage(), null);
            }
        }, "csua-fetch").start();
    }

    // ── WebSocket ────────────────────────────────────────

    private void _wsConnect(int socketId, String url) {
        new Thread(() -> {
            try {
                WebSocketClient ws = new WebSocketClient(url, event -> {
                    _ctx.fireRawCallback("_csuaWS_" + socketId,
                        "[\"" + event[0] + "\"," + (event[1] != null ? "\"" + event[1].replace("\"","\\\"") + "\"" : "null") + "]");
                });
                _sockets.put(socketId, ws);
                ws.connect();
            } catch (Exception e) {
                _ctx.fireRawCallback("_csuaWS_" + socketId, "[\"error\",\"" + e.getMessage() + "\"]");
            }
        }, "csua-ws").start();
    }

    private void _wsSend(int socketId, String data) {
        WebSocketClient ws = _sockets.get(socketId);
        if (ws != null) ws.send(data);
    }

    private void _wsClose(int socketId) {
        WebSocketClient ws = _sockets.get(socketId);
        if (ws != null) { ws.close(); _sockets.remove(socketId); }
    }

    // ── Image loading ────────────────────────────────────

    private void _loadImage(ImageView iv, String url) {
        new Thread(() -> {
            try {
                InputStream is = new URL(url).openStream();
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                _ctx._ui.post(() -> iv.setImageBitmap(bmp));
            } catch (Exception e) {
                android.util.Log.e("CSUA", "Image load failed: " + url, e);
            }
        }, "csua-img").start();
    }

    // ── Helpers ──────────────────────────────────────────

    private static String _readStream(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    static String s(Object o)   { return o != null ? o.toString() : ""; }
    static int _int(Object o)   { try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; } }

    // ── Minimal WebSocket client ─────────────────────────
    // Android doesn't include a WS client — tiny RFC6455 impl

    static class WebSocketClient {
        private final String _url;
        private final java.util.function.Consumer<String[]> _cb;
        private java.net.Socket _socket;
        private PrintWriter _out;
        private boolean _open = false;

        WebSocketClient(String url, java.util.function.Consumer<String[]> cb) {
            _url = url; _cb = cb;
        }

        void connect() throws Exception {
            URI uri = new URI(_url);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : (_url.startsWith("wss") ? 443 : 80);
            boolean tls = _url.startsWith("wss");

            _socket = tls
                ? javax.net.ssl.SSLSocketFactory.getDefault().createSocket(host, port)
                : new java.net.Socket(host, port);

            _out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(_socket.getOutputStream())));
            String key = Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
            _out.print("GET " + (uri.getPath().isEmpty() ? "/" : uri.getPath()) + " HTTP/1.1\r\n");
            _out.print("Host: " + host + "\r\n");
            _out.print("Upgrade: websocket\r\n");
            _out.print("Connection: Upgrade\r\n");
            _out.print("Sec-WebSocket-Key: " + key + "\r\n");
            _out.print("Sec-WebSocket-Version: 13\r\n\r\n");
            _out.flush();

            // Read handshake response
            InputStream in = _socket.getInputStream();
            byte[] buf = new byte[1024];
            in.read(buf); // consume HTTP upgrade response
            _open = true;
            _cb.accept(new String[]{"open", null});

            // Read frames
            new Thread(() -> {
                try {
                    while (_open) {
                        int b0 = in.read(), b1 = in.read();
                        if (b0 == -1) break;
                        int opcode = b0 & 0x0F;
                        long len = b1 & 0x7F;
                        if (len == 126) len = (in.read() << 8) | in.read();
                        byte[] data = new byte[(int) len];
                        int read = 0;
                        while (read < len) read += in.read(data, read, (int) len - read);
                        if (opcode == 8) { _cb.accept(new String[]{"close", null}); break; }
                        if (opcode == 1) _cb.accept(new String[]{"message", new String(data, "UTF-8")});
                    }
                } catch (Exception e) {
                    if (_open) _cb.accept(new String[]{"error", e.getMessage()});
                }
            }, "csua-ws-read").start();
        }

        void send(String text) {
            if (!_open || _socket == null) return;
            new Thread(() -> {
                try {
                    byte[] payload = text.getBytes("UTF-8");
                    OutputStream os = _socket.getOutputStream();
                    os.write(0x81); // FIN + text opcode
                    int len = payload.length;
                    if (len < 126) os.write(0x80 | len);
                    else { os.write(0x80 | 126); os.write((len >> 8) & 0xFF); os.write(len & 0xFF); }
                    byte[] mask = new byte[4];
                    new java.util.Random().nextBytes(mask);
                    os.write(mask);
                    byte[] masked = new byte[len];
                    for (int i = 0; i < len; i++) masked[i] = (byte)(payload[i] ^ mask[i % 4]);
                    os.write(masked);
                    os.flush();
                } catch (Exception e) { android.util.Log.e("CSUA", "WS send error", e); }
            }, "csua-ws-send").start();
        }

        void close() {
            _open = false;
            try { if (_socket != null) _socket.close(); } catch (Exception ignored) {}
        }
    }
}
