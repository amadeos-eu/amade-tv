package eu.amadeos.apps.tv.ui.main;

import eu.amadeos.apps.tv.domain.Channel;
import eu.amadeos.apps.tv.service.logo.LogoCache;

import java.util.Optional;

/** The logo of a channel if it is loaded; the presenter repaints when more arrive. */
@FunctionalInterface
public interface LogoSource {
    LogoSource NONE = c -> Optional.empty();

    Optional<LogoCache.Logo> logo(Channel channel);
}
