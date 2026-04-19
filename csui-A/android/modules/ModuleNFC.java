package com.ctrlscript.csua;

import android.nfc.*;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.content.Intent;

import org.json.JSONObject;

public class ModuleNFC implements CsuaModule {

    private final Bootstrap _ctx;
    private NfcAdapter _nfc;
    private String _pendingReadCbId;
    private String _pendingWriteCbId;
    private byte[] _pendingWriteData;

    public ModuleNFC(Bootstrap ctx) {
        _ctx = ctx;
        _nfc = NfcAdapter.getDefaultAdapter(ctx);
    }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "read":  _read(s(args[0]));                   return null;
            case "write": _write(s(args[0]), s(args[1]), s(args[2])); return null;
        }
        return null;
    }

    private void _read(String cbId) {
        if (_nfc == null || !_nfc.isEnabled()) {
            _ctx.fireCallback(cbId, "nfc_unavailable", null); return;
        }
        _pendingReadCbId = cbId;
        // NFC reads come via onNewIntent — enabled in Bootstrap
        _ctx._ui.post(() -> _nfc.enableReaderMode(_ctx,
            tag -> _handleTag(tag),
            NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B |
            NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V, null));
    }

    private void _write(String cbId, String tagId, String dataJson) {
        _pendingWriteCbId = cbId;
        try {
            org.json.JSONObject d = new JSONObject(dataJson);
            String text = d.optString("text", "");
            _pendingWriteData = text.getBytes("UTF-8");
        } catch (Exception e) { _ctx.fireCallback(cbId, e.getMessage(), null); }
    }

    public void _handleTag(Tag tag) {
        try {
            if (_pendingWriteCbId != null && _pendingWriteData != null) {
                // Write
                NdefMessage msg = new NdefMessage(new NdefRecord[]{
                    NdefRecord.createTextRecord("en", new String(_pendingWriteData))
                });
                Ndef ndef = Ndef.get(tag);
                if (ndef != null) {
                    ndef.connect();
                    ndef.writeNdefMessage(msg);
                    ndef.close();
                    _ctx.fireCallback(_pendingWriteCbId, null, "true");
                } else {
                    NdefFormatable fmt = NdefFormatable.get(tag);
                    if (fmt != null) { fmt.connect(); fmt.format(msg); fmt.close(); _ctx.fireCallback(_pendingWriteCbId, null, "true"); }
                    else _ctx.fireCallback(_pendingWriteCbId, "not_formatable", null);
                }
                _pendingWriteCbId = null;
                _pendingWriteData = null;
            } else if (_pendingReadCbId != null) {
                // Read
                Ndef ndef = Ndef.get(tag);
                JSONObject result = new JSONObject();
                result.put("id", bytesToHex(tag.getId()));
                if (ndef != null) {
                    ndef.connect();
                    NdefMessage msg = ndef.getNdefMessage();
                    if (msg != null && msg.getRecords().length > 0) {
                        NdefRecord r = msg.getRecords()[0];
                        result.put("text", new String(r.getPayload(), 3, r.getPayload().length - 3));
                    }
                    ndef.close();
                }
                _ctx.fireCallback(_pendingReadCbId, null, result.toString());
                _pendingReadCbId = null;
                _ctx._ui.post(() -> _nfc.disableReaderMode(_ctx));
            }
        } catch (Exception e) {
            if (_pendingReadCbId  != null) _ctx.fireCallback(_pendingReadCbId,  e.getMessage(), null);
            if (_pendingWriteCbId != null) _ctx.fireCallback(_pendingWriteCbId, e.getMessage(), null);
            _pendingReadCbId = null; _pendingWriteCbId = null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
}
