package eu.amadeos.apps.tv.bootstrap;

import eu.amadeos.apps.tv.ui.main.MainPresenter;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

/** Entry point. */
public final class AmadeTvApplication {
    public static final AppInfo APP = new AppInfo("amade-tv", "TV", "tv");

    private AmadeTvApplication() {
    }

    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> injector.getInstance(MainPresenter.class).start(), new TvModule());
    }
}
