package eu.amadeos.apps.tv.service.mpv;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

/** The parts of libmpv's client API (libmpv.so.2, mpv/client.h) amade-tv uses, mapped with JNA. */
interface LibMpv extends Library {
    // mpv_format
    int FORMAT_NONE = 0;
    int FORMAT_STRING = 1;
    int FORMAT_FLAG = 3;
    int FORMAT_INT64 = 4;
    int FORMAT_DOUBLE = 5;

    // mpv_event_id
    int EVENT_NONE = 0;
    int EVENT_SHUTDOWN = 1;
    int EVENT_LOG_MESSAGE = 2;
    int EVENT_START_FILE = 6;
    int EVENT_END_FILE = 7;
    int EVENT_FILE_LOADED = 8;
    int EVENT_VIDEO_RECONFIG = 17;
    int EVENT_PLAYBACK_RESTART = 21;
    int EVENT_PROPERTY_CHANGE = 22;

    // Offsets in mpv_event { int event_id; int error; uint64_t reply_userdata; void *data; }
    int EVENT_ID = 0;
    int EVENT_ERROR = 4;
    int EVENT_REPLY_USERDATA = 8;
    int EVENT_DATA = 16;
    // mpv_event_property { const char *name; mpv_format format; void *data; }
    int PROPERTY_NAME = 0;
    int PROPERTY_FORMAT = 8;
    int PROPERTY_DATA = 16;
    // mpv_event_log_message { const char *prefix; const char *level; const char *text; int log_level; }
    int LOG_PREFIX = 0;
    int LOG_LEVEL = 8;
    int LOG_TEXT = 16;
    // mpv_event_end_file { int reason; int error; ... }
    int END_FILE_REASON = 0;
    int END_FILE_ERROR = 4;

    /** Library names to try: Debian/Ubuntu (libmpv2, with and without -dev), Windows, macOS. */
    String[] NAMES = {"mpv", "libmpv.so.2", "libmpv-2", "mpv-2"};

    long mpv_client_api_version();

    Pointer mpv_create();

    int mpv_initialize(Pointer handle);

    void mpv_terminate_destroy(Pointer handle);

    int mpv_set_option_string(Pointer handle, String name, String value);

    /** args is NULL-terminated by JNA. */
    int mpv_command(Pointer handle, String[] args);

    int mpv_set_property_string(Pointer handle, String name, String value);

    /** The result must be freed with {@link #mpv_free}; null if the property is unavailable. */
    Pointer mpv_get_property_string(Pointer handle, String name);

    void mpv_free(Pointer data);

    int mpv_observe_property(Pointer handle, long replyUserdata, String name, int format);

    int mpv_request_log_messages(Pointer handle, String minLevel);

    /** Blocks up to timeout seconds; never null. */
    Pointer mpv_wait_event(Pointer handle, double timeout);

    void mpv_wakeup(Pointer handle);

    String mpv_error_string(int error);

    String mpv_event_name(int event);

    /** libc, needed because libmpv refuses to start unless LC_NUMERIC is "C". */
    interface LibC extends Library {
        int LC_NUMERIC = 1;

        String setlocale(int category, String locale);
    }
}
