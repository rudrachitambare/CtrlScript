package com.ctrlscript.csua;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.speech.tts.TextToSpeech;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class ModuleAudio implements CsuaModule {

    private final Bootstrap _ctx;
    private final Map<Integer, MediaPlayer> _players = new HashMap<>();
    private MediaRecorder _recorder;
    private File _recordFile;
    private TextToSpeech _tts;

    public ModuleAudio(Bootstrap ctx) { _ctx = ctx; }

    @Override
    public Object call(String method, Object... args) {
        switch (method) {
            case "load":    _load(_int(args[0]), s(args[1]));           return null;
            case "play":    _play(_int(args[0]));                       return null;
            case "pause":   _pause(_int(args[0]));                      return null;
            case "stop":    _stop(_int(args[0]));                       return null;
            case "volume":  _volume(_int(args[0]), Float.parseFloat(s(args[1]))); return null;
            case "loop":    _loop(_int(args[0]), "true".equals(s(args[1]))); return null;
            case "onEnd":   _onEnd(_int(args[0]), s(args[1]));          return null;
            case "destroy": _destroy(_int(args[0]));                    return null;
            case "record":  _record(s(args[0]), _int(args[1]));         return null;
            case "stopRecord": _stopRecord();                           return null;
            case "speak":   _speak(s(args[0]), args.length > 1 ? s(args[1]) : "{}"); return null;
            case "stop":    if (_tts != null) _tts.stop();              return null;
        }
        return null;
    }

    // ── MediaPlayer ──────────────────────────────────────

    private void _load(int id, String src) {
        new Thread(() -> {
            try {
                MediaPlayer mp = new MediaPlayer();
                if (src.startsWith("http")) {
                    mp.setDataSource(src);
                } else {
                    // local asset
                    android.content.res.AssetFileDescriptor fd = _ctx.getAssets().openFd(src);
                    mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                    fd.close();
                }
                mp.prepare();
                _players.put(id, mp);
            } catch (Exception e) {
                android.util.Log.e("CSUA", "Audio load error: " + src, e);
            }
        }, "csua-audio-load").start();
    }

    private void _play(int id)   { MediaPlayer mp = _players.get(id); if (mp != null) _ctx._ui.post(mp::start); }
    private void _pause(int id)  { MediaPlayer mp = _players.get(id); if (mp != null) _ctx._ui.post(mp::pause); }
    private void _stop(int id)   { MediaPlayer mp = _players.get(id); if (mp != null) _ctx._ui.post(() -> { mp.stop(); try { mp.prepare(); } catch (Exception ignored) {} }); }
    private void _volume(int id, float v) { MediaPlayer mp = _players.get(id); if (mp != null) mp.setVolume(v, v); }
    private void _loop(int id, boolean v) { MediaPlayer mp = _players.get(id); if (mp != null) mp.setLooping(v); }
    private void _onEnd(int id, String cbId) {
        MediaPlayer mp = _players.get(id);
        if (mp != null) mp.setOnCompletionListener(p -> _ctx.fireCallback(cbId, null, null));
    }
    private void _destroy(int id) {
        MediaPlayer mp = _players.remove(id);
        if (mp != null) { mp.release(); }
    }

    // ── Recording ────────────────────────────────────────

    private void _record(String cbId, int maxMs) {
        new Thread(() -> {
            try {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File dir = _ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
                if (dir == null) dir = _ctx.getFilesDir();
                _recordFile = new File(dir, "REC_" + ts + ".mp4");

                _recorder = new MediaRecorder();
                _recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                _recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                _recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                _recorder.setOutputFile(_recordFile.getAbsolutePath());
                _recorder.prepare();
                _recorder.start();

                Thread.sleep(maxMs);
                _stopRecord();
                _ctx.fireCallback(cbId, null, "\"" + _recordFile.getAbsolutePath() + "\"");
            } catch (Exception e) {
                _ctx.fireCallback(cbId, e.getMessage(), null);
            }
        }, "csua-record").start();
    }

    private void _stopRecord() {
        if (_recorder != null) {
            try { _recorder.stop(); } catch (Exception ignored) {}
            _recorder.release();
            _recorder = null;
        }
    }

    // ── TTS ──────────────────────────────────────────────

    private void _speak(String text, String optsJson) {
        if (_tts == null) {
            _tts = new TextToSpeech(_ctx, status -> {
                if (status == TextToSpeech.SUCCESS) _doSpeak(text, optsJson);
            });
        } else {
            _doSpeak(text, optsJson);
        }
    }

    private void _doSpeak(String text, String optsJson) {
        try {
            org.json.JSONObject opts = new org.json.JSONObject(optsJson);
            float pitch = (float) opts.optDouble("pitch", 1.0);
            float rate  = (float) opts.optDouble("rate",  1.0);
            String lang = opts.optString("lang", "en");
            _tts.setLanguage(new Locale(lang));
            _tts.setPitch(pitch);
            _tts.setSpeechRate(rate);
            _tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } catch (Exception e) {
            _tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    static String s(Object o) { return o != null ? o.toString() : ""; }
    static int _int(Object o) { try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; } }
}
