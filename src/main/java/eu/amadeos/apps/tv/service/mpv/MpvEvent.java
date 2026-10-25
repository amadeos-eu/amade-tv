package eu.amadeos.apps.tv.service.mpv;

import com.sun.jna.Pointer;

/** An {@code mpv_event} copied out of native memory, so it can be used after the next {@code mpv_wait_event}. */
sealed interface MpvEvent {
    // mpv_end_file_reason
    int END_EOF = 0;
    int END_STOP = 2;
    int END_QUIT = 3;
    int END_ERROR = 4;
    int END_REDIRECT = 5;

    record None() implements MpvEvent {
    }

    record Shutdown() implements MpvEvent {
    }

    record StartFile() implements MpvEvent {
    }

    record FileLoaded() implements MpvEvent {
    }

    /** @param error negative mpv error code if {@code reason} is {@link #END_ERROR} */
    record EndFile(int reason, int error) implements MpvEvent {
    }

    /** @param value Double, Boolean, Long or String by the observed format; null if unavailable */
    record PropertyChange(long id, String name, Object value) implements MpvEvent {
    }

    record Log(String level, String prefix, String text) implements MpvEvent {
    }

    record Other(int id) implements MpvEvent {
    }

    /** Reads the event {@code mpv_wait_event} returned; layouts from mpv/client.h, see {@link LibMpv}. */
    static MpvEvent read(Pointer event) {
        int id = event.getInt(LibMpv.EVENT_ID);
        Pointer data = event.getPointer(LibMpv.EVENT_DATA);
        return switch (id) {
            case LibMpv.EVENT_NONE -> new None();
            case LibMpv.EVENT_SHUTDOWN -> new Shutdown();
            case LibMpv.EVENT_START_FILE -> new StartFile();
            case LibMpv.EVENT_FILE_LOADED -> new FileLoaded();
            case LibMpv.EVENT_END_FILE -> new EndFile(data.getInt(LibMpv.END_FILE_REASON), data.getInt(LibMpv.END_FILE_ERROR));
            case LibMpv.EVENT_PROPERTY_CHANGE -> new PropertyChange(event.getLong(LibMpv.EVENT_REPLY_USERDATA),
                    string(data.getPointer(LibMpv.PROPERTY_NAME)),
                    value(data.getInt(LibMpv.PROPERTY_FORMAT), data.getPointer(LibMpv.PROPERTY_DATA)));
            case LibMpv.EVENT_LOG_MESSAGE -> new Log(string(data.getPointer(LibMpv.LOG_LEVEL)),
                    string(data.getPointer(LibMpv.LOG_PREFIX)), string(data.getPointer(LibMpv.LOG_TEXT)).strip());
            default -> new Other(id);
        };
    }

    private static Object value(int format, Pointer data) {
        if (data == null) return null;
        return switch (format) {
            case LibMpv.FORMAT_DOUBLE -> data.getDouble(0);
            case LibMpv.FORMAT_FLAG -> data.getInt(0) != 0;
            case LibMpv.FORMAT_INT64 -> data.getLong(0);
            // For MPV_FORMAT_STRING, data points to the char*.
            case LibMpv.FORMAT_STRING -> string(data.getPointer(0));
            default -> null;
        };
    }

    private static String string(Pointer p) {
        return p == null ? "" : p.getString(0, "UTF-8");
    }
}
