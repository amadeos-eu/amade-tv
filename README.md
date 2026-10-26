# amade-tv

**TV** for [amadeOS](https://github.com/datazuul/amadeos): the live streams of the German-language
public broadcasters with a program guide, in a Motif/CDE-styled window. Playback is done by
[libmpv](https://mpv.io), embedded into the Swing window; the rest is Java on
[amade-commons](https://github.com/datazuul/amade-commons) like the other amadeOS applications.

Status: **Live, Guide and Recordings.** The library of the broadcasters' on-demand programs,
downloads and a watch-later list follow.

Platforms: **Linux only for now** (amadeOS, Debian and Ubuntu with `libmpv2`). The code would load
`libmpv-2.dll` on Windows, but no Windows installer is built: it would have to bundle libmpv and its
FFmpeg libraries with jpackage, which amade-tv does not do yet. The CI builds only the Debian package.

## Features

| Page | What it shows | Notes |
|------|---------------|-------|
| **Live** | channel list with logos and the program running now, the player, today's schedule of the channel | pause and go back up to 10 minutes, *Back to live*; mute, volume, subtitles, audio track (original language, audio description), full screen (F11) |
| **Guide** | all channels on a time line, one day at a time | *Now*, *Prime Time 20:15*, previous/next day, search; a program's details with *Watch Live*, *Remind Me* and *Record* |
| **Recordings** | planned, running and finished recordings | size while recording; *Play*, *Show in Folder*, *Stop Recording*, *Remove* (optionally with the file) |

Reminders pop up when a program starts and offer to switch to it. *Record* (in the guide or in
today's list on the Live page) saves a program from the live stream into `~/Videos/TV` (the desktop's
videos folder) as a Matroska file: from two minutes before to five minutes after the program, copied
as it is with `ffmpeg -c copy`, so it costs almost no processor time. TV records only while it is
open; a recording whose time passed while TV was closed is marked as missed. The channel list has the 11 main
channels; four groups in the *Channels* menu add more, each switched on by itself: the ARD regional
channels and the Bundestag channels, channels from Austria and Switzerland, international channels
(news and culture from Europe and beyond), and free German private channels. Only the main and
regional channels have a program guide; the others are live streams only. A few international
channels stream through YouTube and are shown only when `yt-dlp` is installed. Texts in English, German, French, Spanish and Italian, following the desktop language.

## Data sources

| What | From | How |
|------|------|-----|
| Stream addresses, program running now | [Zapp](https://github.com/mediathekview/zapp-backend) API of the MediathekView project | `api.zapp.mediathekview.de/v1/channelInfoList`, `/shows/<channel>`; the built-in list in `channels.json` is the fallback |
| ARD channels' schedule | ARD program API | `programm-api.ard.de/program/api/program?day=` |
| ZDF channels' schedule (ZDF, 3sat, ARTE, phoenix, KiKA, ZDFneo, ZDFinfo) | the ZDF EPG behind zdf.de | GraphQL, with the API token read from the zdf.de live TV page |
| Channel logos, stream addresses of the extra groups | [Kodinerds IPTV lists](https://github.com/jnk22/kodinerds-iptv) | `iptv/clean/clean_tv.m3u`; logos downloaded on first use, never shipped in the package |

Schedules are cached in `~/.cache/amadeos/tv/guide/` (6 hours; past days stay until they are a week
old). Settings and reminders are in `~/.config/amadeos/tv/`.

Only freely available streams are used. Some programs are only licensed for Germany (sometimes
Austria and Switzerland); outside, the broadcaster shows a notice or the stream fails, and amade-tv
says so. It never tries to get around such limits. Private channels whose streams are encrypted (DRM)
or paid are not included; only private channels that stream freely are.

None of these interfaces is a documented public API, so a broadcaster can change them at any time.
When a source fails, the guide shows *No program from ... right now* and the rest keeps working.

## Architecture

Java 25, Swing, Guice, JNA; libmpv is driven by `Mpv` of amade-commons (package `eu.amadeos.commons.mpv`),
which Video uses too.

| Package | Content |
|---------|---------|
| `bootstrap` | `AmadeTvApplication` (entry point), `TvModule` |
| `domain` | `Channel`, `ChannelGroup`, `Broadcast`, `Schedule` (immutable, per channel), `Reminder`, `Recording`, `PlayerState` |
| `repository` | `ChannelCatalog` (`channels.json` + stream addresses from Zapp and Kodinerds), `TvPreferences`, `ReminderStore`, `RecordingStore` |
| `service.guide` | `GuideService` (cache, loading, listeners), `ArdProgramSource`, `ZdfEpgSource`, `ZappService` |
| `service.logo` | `LogoCache`: downloads, turns, crops and scales the channel logos |
| `service.mpv` | `MpvPlayer`: the player state on top of amade-commons' `Mpv`: live time shift, volume, tracks |
| `service.record` | `Recorder`: starts due recordings with ffmpeg (through the commons `CommandRunner`), follows their progress, stops them |
| `service.net` | `Web` and its `HttpWeb` implementation (the tests use a fake), `KodinerdsList` |
| `ui.live` | `LivePage`, `ChannelCell`, `LiveControls`, `VideoCanvas` |
| `ui.guide` | `GuidePage`, `GuideGrid`, `TimelineLayout` |
| `ui.recordings` | `RecordingsPage` |
| `ui.main` | `MainWindow` (menus, sidebar, pages), `MainPresenter`, `ChannelTile` |

## Building and testing

Requirements: Java 25 JDK; `libmpv2` to play, `ffmpeg` to record; `dpkg-deb` for the Debian package.

`amade-commons` is taken from JitPack. If it is checked out next to this repository
(`../amade-commons`), Gradle builds that checkout instead.

```bash
xvfb-run -a ./gradlew test     # unit and page tests (no libmpv or network needed; the pages need a display)
./gradlew run                  # start TV
./gradlew deb                  # build/distributions/amade-tv_0.0.0~dev.0_all.deb
./gradlew installToAmadeOs     # copy the .deb into ../amadeos/config/packages.chroot/
```

The Debian package depends on `libmpv2` and `ffmpeg`. It installs `amade-tv.desktop`
(`Categories=AudioVideo;Video;TV;Player;`), so the amadeOS front panel lists it in the Sound & Video
drawer.

## Roadmap

1. Live and Guide
2. Recordings from the live streams (this version)
3. Library: the on-demand programs of the broadcasters, search, downloads, watch later

## License

[EUPL-1.2](LICENSE)
