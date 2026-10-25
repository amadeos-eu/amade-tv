package eu.amadeos.apps.tv.service.guide;

import eu.amadeos.apps.tv.domain.Broadcast;
import eu.amadeos.apps.tv.domain.Channel;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Where the day schedules of some channels come from. The sources are the program services the
 * broadcasters' own websites use; they have no published terms, so the app asks each of them for a day
 * at most every few hours ({@link GuideService} keeps the answers).
 */
public interface GuideSource {
    /** The broadcasters' days begin and end in German time. */
    ZoneId BROADCAST_ZONE = ZoneId.of("Europe/Berlin");

    Channel.GuideSourceId id();

    /**
     * The program items of {@code day} for those of {@code channels} this source knows, with the channel ids
     * of {@code channels}. A source may answer a little more than the day (its own day starts at 5 am).
     */
    List<Broadcast> day(LocalDate day, List<Channel> channels) throws IOException;
}
