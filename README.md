# amade-tv

**TV** for [amadeOS](https://github.com/datazuul/amadeos): the live streams of the German-language
public broadcasters with a program guide, in a Motif/CDE-styled window. Playback is done by
[libmpv](https://mpv.io), embedded into the Swing window; the rest is Java on
[amade-commons](https://github.com/datazuul/amade-commons) like the other amadeOS applications.

Status: **step 1, Live and Guide.** Step 2 adds the library of the broadcasters' on-demand
programs, downloads and a watch-later list.

## Features

| Page | What it shows | Notes |
|------|---------------|-------|
| **Live** | channel list with logos and the program running now, the player, today's schedule of the channel | pause and go back up to 10 minutes, *Back to live*; mute, volume, subtitles, audio track (original language, audio description), full screen (F11) |
| **Guide** | all channels on a time line, one day at a time | *Now*, *Prime Time 20:15*, previous/next day, search; a program's details with *Watch Live* and *Remind Me* |

Reminders pop up when a program starts and offer to switch to it. The channel list has the 11 main
channels; *Channels → Show Regional Channels* adds the ARD regional channels and the Bundestag
channels. Texts in English, German, French, Spanish and Italian, following the desktop language.

## Data sources

| What | From | How |
|------|------|-----|
| Stream addresses, program running now | [Zapp](https://github.com/mediathekview/zapp-backend) API of the MediathekView project | `api.zapp.mediathekview.de/v1/channelInfoList`, `/shows/<channel>`; the built-in list in `channels.json` is the fallback |
| ARD channels' schedule | ARD program API | `programm-api.ard.de/program/api/program?day=` |
| ZDF channels' schedule (ZDF, 3sat, ARTE, phoenix, KiKA, ZDFneo, ZDFinfo) | the ZDF EPG behind zdf.de | GraphQL, with the API token read from the zdf.de live TV page |
| Channel logos | [Kodinerds IPTV lists](https://github.com/jnk22/kodinerds-iptv) | downloaded on first use, never shipped in the package |

Schedules are cached in `~/.cache/amadeos/tv/guide/` (6 hours; past days stay until they are a week
old). Settings and reminders are in `~/.config/amadeos/tv/`.

Only freely available streams are used. Some programs are only licensed for Germany (sometimes
Austria and Switzerland); outside, the broadcaster shows a notice or the stream fails, and amade-tv
says so. It never tries to get around such limits. Private channels are not included: their streams
are encrypted (DRM) or paid.

None of these interfaces is a documented public API, so a broadcaster can change them at any time.
When a source fails, the guide shows *No program from ... right now* and the rest keeps working.

## Architecture

Java 25, Swing, Guice, JNA; libmpv is driven by `Mpv` of amade-commons (package `eu.amadeos.commons.mpv`),
which Video uses too.

| Package | Content |
|---------|---------|
| `bootstrap` | `AmadeTvApplication` (entry point), `TvModule` |
| `domain` | `Channel`, `Broadcast`, `Schedule` (immutable, per channel), `Reminder`, `PlayerState` |
| `repository` | `ChannelCatalog` (`channels.json` + stream addresses from Zapp), `TvPreferences`, `ReminderStore` |
| `service.guide` | `GuideService` (cache, loading, listeners), `ArdProgramSource`, `ZdfEpgSource`, `ZappService` |
| `service.logo` | `LogoCache`: downloads, turns, crops and scales the channel logos |
| `service.mpv` | `MpvPlayer`: the player state on top of amade-commons' `Mpv`: live time shift, volume, tracks |
| `service.net` | `Web` and its `HttpWeb` implementation (the tests use a fake) |
| `ui.live` | `LivePage`, `ChannelCell`, `LiveControls`, `VideoCanvas` |
| `ui.guide` | `GuidePage`, `GuideGrid`, `TimelineLayout` |
| `ui.main` | `MainWindow` (menus, sidebar, pages), `MainPresenter`, `ChannelTile` |

## Building and testing

Requirements: Java 25 JDK; `libmpv2` to play; `dpkg-deb` for the Debian package.

`amade-commons` is taken from JitPack. If it is checked out next to this repository
(`../amade-commons`), Gradle builds that checkout instead.

```bash
xvfb-run -a ./gradlew test     # unit and page tests (no libmpv or network needed; the pages need a display)
./gradlew run                  # start TV
./gradlew deb                  # build/distributions/amade-tv_0.0.0~dev.0_all.deb
./gradlew installToAmadeOs     # copy the .deb into ../amadeos/config/packages.chroot/
```

The Debian package depends on `libmpv2`. It installs `amade-tv.desktop`
(`Categories=AudioVideo;Video;TV;Player;`), so the amadeOS front panel lists it in the Sound & Video
drawer.

## Roadmap

1. Live and Guide (this version)
2. Library: the on-demand programs of the broadcasters, search, downloads, watch later
3. Handbook chapters (the mpv binding is in amade-commons now)

## License

[EUPL-1.2](LICENSE)
