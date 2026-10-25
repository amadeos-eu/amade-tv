package eu.amadeos.apps.tv.service.mpv;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Events built in native memory with the layouts of mpv/client.h (64-bit). */
class MpvEventTest {
    @Test
    void propertyChangesCarryTheObservedValue() {
        assertEquals(new MpvEvent.PropertyChange(1, "time-pos", 12.5),
                MpvEvent.read(event(LibMpv.EVENT_PROPERTY_CHANGE, 1, property("time-pos", LibMpv.FORMAT_DOUBLE, doubleValue(12.5)))));
        assertEquals(new MpvEvent.PropertyChange(3, "pause", true),
                MpvEvent.read(event(LibMpv.EVENT_PROPERTY_CHANGE, 3, property("pause", LibMpv.FORMAT_FLAG, intValue(1)))));
        assertEquals(new MpvEvent.PropertyChange(4, "chapter", 2L),
                MpvEvent.read(event(LibMpv.EVENT_PROPERTY_CHANGE, 4, property("chapter", LibMpv.FORMAT_INT64, longValue(2)))));
        Memory title = string("Ice Age");
        Memory titleRef = new Memory(8);
        titleRef.setPointer(0, title);
        assertEquals(new MpvEvent.PropertyChange(7, "media-title", "Ice Age"),
                MpvEvent.read(event(LibMpv.EVENT_PROPERTY_CHANGE, 7, property("media-title", LibMpv.FORMAT_STRING, titleRef))));
        // Unavailable (e.g. duration of a live stream): format NONE, no data
        assertEquals(new MpvEvent.PropertyChange(2, "duration", null),
                MpvEvent.read(event(LibMpv.EVENT_PROPERTY_CHANGE, 2, property("duration", LibMpv.FORMAT_NONE, null))));
    }

    @Test
    void endFileReasonAndError() {
        Memory data = new Memory(16);
        data.setInt(LibMpv.END_FILE_REASON, MpvEvent.END_ERROR);
        data.setInt(LibMpv.END_FILE_ERROR, -13);
        assertEquals(new MpvEvent.EndFile(MpvEvent.END_ERROR, -13), MpvEvent.read(event(LibMpv.EVENT_END_FILE, 0, data)));
    }

    @Test
    void logMessagesAndSimpleEvents() {
        Memory data = new Memory(32);
        data.setPointer(LibMpv.LOG_PREFIX, string("vo/gpu"));
        data.setPointer(LibMpv.LOG_LEVEL, string("warn"));
        data.setPointer(LibMpv.LOG_TEXT, string("slow!\n"));
        assertEquals(new MpvEvent.Log("warn", "vo/gpu", "slow!"), MpvEvent.read(event(LibMpv.EVENT_LOG_MESSAGE, 0, data)));
        assertEquals(new MpvEvent.FileLoaded(), MpvEvent.read(event(LibMpv.EVENT_FILE_LOADED, 0, null)));
        assertEquals(new MpvEvent.None(), MpvEvent.read(event(LibMpv.EVENT_NONE, 0, null)));
        assertEquals(new MpvEvent.Other(LibMpv.EVENT_VIDEO_RECONFIG), MpvEvent.read(event(LibMpv.EVENT_VIDEO_RECONFIG, 0, null)));
    }

    /** mpv_event { int event_id; int error; uint64_t reply_userdata; void *data; } */
    private static Pointer event(int id, long userdata, Pointer data) {
        Memory m = new Memory(24);
        m.clear();
        m.setInt(LibMpv.EVENT_ID, id);
        m.setLong(LibMpv.EVENT_REPLY_USERDATA, userdata);
        m.setPointer(LibMpv.EVENT_DATA, data);
        return m;
    }

    /** mpv_event_property { const char *name; mpv_format format; void *data; } */
    private static Memory property(String name, int format, Pointer data) {
        Memory m = new Memory(24);
        m.clear();
        m.setPointer(LibMpv.PROPERTY_NAME, string(name));
        m.setInt(LibMpv.PROPERTY_FORMAT, format);
        m.setPointer(LibMpv.PROPERTY_DATA, data);
        return m;
    }

    private static Memory string(String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Memory m = new Memory(bytes.length + 1);
        m.write(0, bytes, 0, bytes.length);
        m.setByte(bytes.length, (byte) 0);
        return m;
    }

    private static Memory doubleValue(double v) {
        Memory m = new Memory(8);
        m.setDouble(0, v);
        return m;
    }

    private static Memory intValue(int v) {
        Memory m = new Memory(4);
        m.setInt(0, v);
        return m;
    }

    private static Memory longValue(long v) {
        Memory m = new Memory(8);
        m.setLong(0, v);
        return m;
    }
}
