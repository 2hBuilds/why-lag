# 2h Why Lag

> This is the full description of 2h Why Lag - every answer, figure and setting. The short version, with pictures,
> is the plugin's page on the Plugin Hub (the repository's README).

When the game lags, this plugin says **what caused it and what to do about it**: your PC, your connection, the world
or memory. It reads five things together - the frame rate, the server ticks, the ping, memory and the CPU - because
a slow tick can come from any of them, and puts the answer in two plain lines with the one fix under it. It also
keeps a small badge on the game screen, a list of every lag with its time and cause, and a report you can copy.

## What the sidebar shows

Top to bottom:

- **The header.** "Why Lag" and your world ("World 416"), or "Not logged in".
- **The answer card.** Two big lines: what caused the lag, then whose side it is on ("World lag / Not you", "Low FPS /
  Your PC"). Under them, what was measured, the word **Fix** in orange with the fix beside it when there is one, and
  a line saying when it was ("21:47:30, world 416, 4 min ago") with how sure the plugin is - **Sure**, **Likely**,
  **Hint** or **Can't tell**.
  A shape at the left says how bad: a red square, an amber triangle, a green circle, or a hollow ring when there is
  no data. Hover the card to see, after the headline, what was measured and found fine ("Ping and ticks were fine.").
- **Five cells.** Each is green, amber or red (grey with no data), and each has a tip on hover:

  | Cell | Shows | Amber | Red |
  |---|---|---|---|
  | FPS | frames in the newest second | under 40 | under 25 |
  | Tick | the mean gap between server ticks over the last 60 s (a normal tick is 600 ms) | a tick 200 ms or more off | 400 ms or more off |
  | Ping | the round trip of the game's own connection | 80 ms | 150 ms |
  | Mem | memory in use, as a share of the client's memory limit | heap after clean-up 85 %, or a pause of 100 ms in the last 60 s | 93 %, or a pause of 300 ms |
  | CPU | the game's share, and under it the whole PC's ("PC 37") | the whole PC at 85 % | 95 % |

  "Off" for a tick is measured after taking away one frame's time, because a tick can only be handled on a frame. A
  frame cap you set yourself, with the frame rate sitting on it, counts as green. The game's own share of the CPU has
  no colour: a busy game is normal with an unlocked frame rate.
- **The range row.** **1 min**, **10 min** or **60 min**; the panel opens on 1 min. It sets both the graphs and the
  list of lags. While the session is younger than the range, a grey note says how much is held ("30 s so far").
- **Graphs** (open by default; press the row to fold it). Five lanes on one time line - frame rate, ticks, ping, memory, CPU - each
  coloured by the same limits as the cells, with every lag as a shaded band across them and the cause's own lane
  drawn redder. Hover for one moment's clock and its six numbers.
- **Lags** (folded until you open it; press the row). Each lag in the range, newest first, at most six rows: its start time, the
  cause's group (Connection, Frame rate, Memory, World or Not sure) and how long it lasted. Press a row and the card,
  the cells and the graph values show that lag's numbers; press it again to go back to now. Hover a row for its
  headline.
- **This session.** The number of lags since the plugin started, and how many of each group ("Conn 1  Frame 1  Mem 1
  World 1"; a count over 99 reads "99+").
- **Copy report.** One button, the row's width: it copies a plain-text summary to the clipboard and says "Copied"
  for two seconds.

The report is plain ASCII: a header (world, start and end time, minutes), a "Now" line with the five readings, the
session's counts, one line per lag (time, length, group, how sure, the headline, the proof and the fix), and the
settings that matter (client version, renderer, draw distance, anti-aliasing, frame cap, memory limit and memory
source). It names no player and no address.

## The game badge

A small badge on the game screen that follows the same answer as the card. Its **Style** setting picks one of four:

- **Icon** - a picture of the cause (a globe for the world, a Wi-Fi mark for your connection, a screen for your PC, a
  memory stick for memory, a question mark for "can't tell") with the status shape in its corner.
- **Icon and words** - the icon, and the answer's two lines beside it.
- **Shape and words** - the status shape, and the two lines.
- **Shape only** - the status shape alone; the hover tells the causes apart.

The two styles without words are a real RuneLite infobox: they sit in the infobox row and you move, flip or detach
them with RuneLite's own menu. The two with words are an overlay, top left by default, moved with Alt + drag.

While all is well the badge is a green circle - or nothing at all with **When smooth: Hide**. A lag shows the answer
in amber or red. When the lag ends the badge stays for 15 seconds, dimmed, then goes back. A slow spell that is not a
lag by itself (a capped frame rate, a high steady ping, slow drawing, a slow world, a low memory limit) is shown in
amber or red without dimming, whatever **When smooth** says. "Measuring" and "Waiting" show a hollow ring (nothing,
with **When smooth: Hide**); "Not logged in" shows nothing.

Hover it for two lines. On a lag: the answer in one line, then the numbers of that cause - "Ticks 1,240 ms, ping 41
ms" for the world, "Ping 310 ms, ticks 1,240 ms" for your connection, "Worst frame 480 ms, 50 fps" for your PC,
"Pause 340 ms, memory 742 MB" for memory, "Ticks 1,240 ms, worst frame 170 ms" when it cannot tell.

With **Chat line on a lag** on, one game message follows each lag: "[Why Lag] World lag - not you (14 s). Ticks 1,240
ms, ping 41 ms." At most one every 30 seconds, so a bad minute cannot fill the chat.

## How it decides

Once a second the plugin reads the frames, the server ticks, the game connection (ping and packets that had to be
sent again), memory pauses, and how busy the game's own thread was. A **lag** starts at the first of these: a single
frame that took 200 ms or more (longer when a low frame cap makes frames slower); a tick 250 ms or more off; no tick
for 1.2 seconds while frames still draw; 1 % or more of what your PC sent being sent again; a memory pause of 100 ms
or more; a lost connection; a map load of 2 seconds or more. It ends after 5 quiet seconds, and one that would pass 2
minutes closes and is then judged as a condition. The seconds after a login, a hop, a lost connection or a load are
set aside, so they cannot cause a false alarm.

Each lag is scored against the causes below, and the winner names it - but only if it beats the runner-up by 15
points. If not, the answer is **Lag / Can't tell why**, with "Wait for it to happen again." Every cause has a ceiling on
how sure it will say it is, and a check that could not be made (no ping reading, memory pauses not measured) lowers
that by a step - or, when the cause needs that check, rules the cause out. The card holds an answer at least 10 seconds, and about 15 seconds after a lag's last bad
moment it goes back to Smooth ("No lag for 10 s", then minutes), or to a slow spell that is still going on. A slow
spell without a lag of its own - a capped rate, a high ping, slow drawing, a slow world, a low memory limit - shows on
the card once it has held for 10 seconds in a row.

| Answer | Fix | Says this when |
|---|---|---|
| **Disconnected** / Line or world | wait a minute and log in again; or check cable or Wi-Fi if every world does it | the connection dropped; the proof says how ping and re-sends looked just before |
| **Memory stall** / The client | close the world map; restart if it repeats | a memory clean-up pause of 100 ms or more covered most of a frozen frame |
| **Map loading** / Just the map | lower Extended map loading (GPU); else nothing to fix | a map load of 2 seconds or more |
| **Packet loss** / Line or world | if every world does it, check cable or Wi-Fi | 1 in 100 or more of what your PC sent had to be sent again (late ticks and a ping spike back it up) |
| **Ping lag** / Your internet | use a cable, not Wi-Fi; pause downloads | the ping spiked - to double its usual and 50 ms over it - while ticks came both early and late and frames kept up |
| **World lag** / Not you | hop to a quieter world | ticks ran slow (a median gap of 660 ms or more over at least 5 ticks) while ping stayed within 20 ms of its usual, frames were clean and nothing was re-sent; or the same over the last minute (620 ms or more) |
| **Client froze** / A plugin? | turn plugins off one at a time | a frame took too long while the game's thread was busy (over 30 % of the time) |
| **Client waited** / An overlay? | close overlays and recorders | the same while it was not busy |
| **No ticks** / Line or world | hop worlds; if it follows you, it is your line | no tick for 1.2 seconds with frames and ping fine |
| **FPS capped** / Your setting | raise or turn off that cap | a frame cap you set holds the rate under 40 for 10 seconds |
| **High ping** / Your internet | try a world closer to you | a ping of 150 ms or more that stays steady over the last minute |
| **Low FPS** / Your PC | turn the GPU plugin on; or lower draw distance or anti-aliasing | under 40 frames a second for 10 seconds in a row |
| **Low memory** / Your setting | remove the Java memory limit | the client may use under 700 MB |

Three states are not verdicts: **Not logged in** ("Log in to start measuring."), **Measuring** (before the first
reading) and **Waiting** ("No frames are being drawn." - logged in, no frame for more than 2 seconds).

## Settings

RuneLite's settings page for 2h Why Lag has five items. The panel's range chips are the only place the graph range is
set.

- **Exact memory pauses** (default on) - "Read memory clean-up pauses and processor use from Java. Off: memory is
  estimated." With it off, the Mem cell says "pause n/a", the CPU cell has no data, "Memory stall" cannot be named,
  and the report says memory pauses were not measured.
- **Show on game screen** (on) - "A small badge on the game screen that shows what is lagging".
- **Style** (Icon) - "A picture of the cause, the picture with words, a shape with words, or the shape alone".
- **When smooth** (Show) - "Show the green circle while all is well, or hide the badge until something lags".
- **Chat line on a lag** (on) - "One game message after each lag that names the cause".

The last four are under **Game screen**.

## Privacy and network

Nothing is sent, nothing is uploaded, and the plugin writes nothing to disk. Everything it reads stays in memory, for the last 60
minutes, until you close the client.

- The **ping** is read from the game's own connection with RuneLite's own call (the one the world switcher uses). No
  packet is sent for it.
- **Memory and CPU** come from Java's own counters, read only.
- To explain a low frame rate it **reads** the GPU plugin's, 117 HD's and FPS Control's settings; it changes none.
- **Copy report** puts text on your clipboard and nowhere else.

## Getting started

Install it and play. It measures from the moment you log in; there is nothing to set up. Open the sidebar for the
answer and the fix, or just watch the badge. Open **Graphs** and **Lags** for the detail, and press **Copy report**
to hand the numbers to someone else.

## Caveats worth knowing

- **"Lag - can't tell why" is a real answer.** When two causes score close together, or nothing measurable explains a
  spell, the plugin says so rather than guess.
- **A busy world can look like lag.** Slow ticks with a steady ping and clean frames read as "World lag - not you",
  which is true of the server and cannot say whether the world is crowded or something else is wrong with it.
- **The first minute is the least certain.** The plugin learns your usual ping for a world from 30 quiet seconds of
  readings, and starts again when you hop. Until then the causes that compare against it - a ping spike, a slow world
  with an ordinary ping - cannot be named, and the answer may be "Can't tell why".
- **Ping needs your system's help.** Where Java or the system will not give the game connection's numbers, the Ping
  cell says "Not on this PC" or "Could not read it", and the causes that need ping say less.
- **Your own frame cap is not lag.** It is named as a setting, in green, and only when the rate sits on it.

## Licence

BSD 2-Clause. See `LICENSE`.
