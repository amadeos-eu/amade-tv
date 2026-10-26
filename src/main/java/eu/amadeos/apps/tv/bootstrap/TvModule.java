package eu.amadeos.apps.tv.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.apps.tv.repository.ChannelCatalog;
import eu.amadeos.apps.tv.service.net.KodinerdsList;
import eu.amadeos.apps.tv.repository.RecordingStore;
import eu.amadeos.apps.tv.repository.ReminderStore;
import eu.amadeos.apps.tv.repository.TvPreferences;
import eu.amadeos.apps.tv.service.guide.ArdProgramSource;
import eu.amadeos.apps.tv.service.guide.GuideService;
import eu.amadeos.apps.tv.service.guide.ZappService;
import eu.amadeos.apps.tv.service.guide.ZdfEpgSource;
import eu.amadeos.apps.tv.service.logo.LogoCache;
import eu.amadeos.apps.tv.service.mpv.MpvPlayer;
import eu.amadeos.apps.tv.service.net.HttpWeb;
import eu.amadeos.apps.tv.service.record.Recorder;
import eu.amadeos.apps.tv.service.net.Web;
import eu.amadeos.apps.tv.ui.main.ChannelTile;
import eu.amadeos.apps.tv.ui.main.MainPresenter;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.process.CommandRunner;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * TV bindings; executor, directories, theme and EDT executor come from
 * {@link eu.amadeos.commons.app.CommonsModule}. There is one player, one guide and one main window.
 */
public final class TvModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MpvPlayer.class).in(Scopes.SINGLETON);
        bind(MainPresenter.class).in(Scopes.SINGLETON);
        bind(Clock.class).toInstance(Clock.systemDefaultZone());
    }

    @Provides
    @Singleton
    Web web(AppInfo app) {
        return new HttpWeb(app);
    }

    @Provides
    @Singleton
    ChannelCatalog channels(AppDirectories dirs) {
        return new ChannelCatalog(dirs.cache());
    }

    @Provides
    @Singleton
    TvPreferences preferences(AppDirectories dirs) {
        return new TvPreferences(dirs.config());
    }

    @Provides
    @Singleton
    ReminderStore reminders(AppDirectories dirs, Clock clock) {
        return new ReminderStore(dirs.config(), clock.instant());
    }

    // --8<-- [start:recorder]
    @Provides
    @Singleton
    Recorder recorder(AppDirectories dirs, XdgDirectories xdg, Clock clock) {
        return new Recorder(new RecordingStore(dirs.config()), CommandRunner.system(), xdg.videos().resolve("TV"),
                clock.getZone());
    }
    // --8<-- [end:recorder]

    @Provides
    @Singleton
    KodinerdsList kodinerds(Web web) {
        return new KodinerdsList(web);
    }

    // --8<-- [start:guide]
    @Provides
    @Singleton
    ZappService zapp(Web web) {
        return new ZappService(web);
    }
    // --8<-- [end:guide]

    @Provides
    @Singleton
    GuideService guide(Web web, ZappService zapp, AppDirectories dirs, Clock clock) {
        return new GuideService(List.of(new ArdProgramSource(web), new ZdfEpgSource(web)), zapp, dirs.cache(), clock);
    }

    @Provides
    @Singleton
    LogoCache logos(Web web, AppDirectories dirs, ExecutorService executor) {
        return new LogoCache(web, dirs.cache(), executor, ChannelTile.WIDTH - 12, ChannelTile.HEIGHT - 8);
    }
}
