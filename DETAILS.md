# 2h Why Lag

> This is the full description of 2h Why Lag - every answer, figure and setting. The short version, with pictures,
> is the plugin's page on the Plugin Hub (the repository's README).

When the game lags, this plugin says **what caused it and what to do about it**: your PC, your connection or the
world. It reads three things together - the frame rate, the server ticks and the ping - because a late tick can come
from any of them, and puts the answer in two plain lines with the one fix under it. It also keeps a small badge on
the game screen, a list of every lag with its time and cause, and a one-click test, **Troubleshoot...**, whose
report you can copy into a bug post.

## What the sidebar shows

Top to bottom:

- **The header.** "Why Lag" on the left, your world ("World 416") or "Not logged in", and at the right end the
  **gear**, grey at rest and orange under the mouse, with the tooltip "Settings". The gear opens the menu described
  below. The panel has no button row and no version footer.
- **The answer card.** Two big lines: what caused the lag, then whose side it is on ("World lag / Not you", "Low FPS /
  Your PC"). Under them, what was measured, the word **Fix** in orange with the fix beside it when there is one, and
  a line saying when it was ("21:47:30, world 416, 4 min ago") with how sure the plugin is - **Sure**, **Likely**,
  **Hint** or **Can't tell**. Once a lag is over and the card is back on Smooth, that line reads "Last lag 21:47,
  this world"; a slow spell that is still going on reads "since 21:40".
  A shape at the left says how bad: a red square, an amber triangle, a green circle, or a hollow ring when there is
  no data. Hover the card to see, after the headline, what was measured and found fine ("Frames and ping were
  fine.").
- **Three cells**, each a third of the width. Each is green, amber or red (grey with no data), and each has a tip on
  hover:

  | Cell | Shows | Amber | Red |
  |---|---|---|---|
  | FPS | frames in the newest second | under 40 | under 25 |
  | Tick | the mean gap between server ticks over the last 60 s (a normal tick is 600 ms) | a tick 200 ms or more off | 400 ms or more off |
  | Ping | the round trip of the game's own connection | 80 ms | 150 ms |

  "Off" for a tick is measured after taking away one frame's time, because a tick can only be handled on a frame. A
  frame cap you set yourself, with the frame rate sitting on it, counts as green. With no reading a cell shows a
  dash and its tip says why ("Not logged in", "Nothing sent", "Not on this PC", "Could not read it").
- **The range row.** **1 min**, **10 min** or **60 min**; the panel opens on 1 min. It sets both the graphs and the
  list of lags. While the session is younger than the range, a grey note says how much is held ("30 s so far").
- **Graphs** (open by default; press the row to fold it). Three lanes on one time line - frame rate, ticks, ping -
  each coloured by the same limits as the cells, with every lag as a shaded band across them and the cause's own
  lane drawn redder. Hover for one moment's clock and its three numbers.
- **Lags** (folded until you open it; press the row). Each lag in the range, newest first, at most six rows: its
  start time, the cause's group (Connection, Frame rate, World or Not sure) and how long it lasted. Press a row and
  the card, the cells and the graph values show that lag's numbers (the Tick cell shows its worst tick); press it
  again to go back to now. Hover a row for its headline.
- **This session.** The number of lags since the plugin started, and how many of each group ("Conn 1  Frame 1
  World 1"; a "? n" appears for lags it could not place; a count over 99 reads "99+").

## The gear menu

The gear at the top right opens a short menu, built fresh each time so that what it ticks is what is stored. A
choice closes it. Top down:

- **Badge on the game screen** - a tick box.
- **Badge style** - a submenu: Icon, Icon and words, Shape and words, Shape only (the current one ticked).
- **Show badge when smooth** - a tick box; ticked shows the green circle while all is well, unticked hides it.
- **Chat line on a lag** - a tick box.
- a divider, **Troubleshoot...**, a divider,
- the version, "2h Why Lag 1.0.0", in grey. It is not a control.

## Troubleshoot

**Troubleshoot...** opens a window and tests the plugin itself. It opens at once on "Testing..."; a moment later it
shows a **one-line verdict** on top and the **whole report** below it in a read-only box, with two buttons, **Copy
report** and **Close**. The window is titled "2h Why Lag 1.0.0 - Troubleshoot", stays open beside the game, and is
closed when the sidebar panel is hidden. Choose Troubleshoot... again while it stands and it runs a new test in the
same window. **Copy report** puts exactly the report's text on the clipboard and reads "Copied" for two seconds.
Nothing is written to disk and nothing is sent.

**The ten quick checks**, run in this order:

| # | Check | Passes when |
|---|---|---|
| 1 | Logged in | a world is known and the newest second is in-game |
| 2 | Frames arrive | frames were counted in the last 2 s, at 1 to 1,000 a second |
| 3 | Ticks arrive | the last tick is under 1.2 s old, 5 or more came in 10 s, and their mean gap is 500 to 700 ms |
| 4 | Ping readable | a fresh round trip time (at most 5 s old) was read from the game's own connection |
| 5 | The sampler runs | the plugin's once-a-second step started under 2 s ago and its work took under 2 ms |
| 6 | Settings read | the renderer and the frame cap are known |
| 7 | The badge is up | it is registered with the client (hidden by the setting also passes, with a note) |
| 8 | Client fits | RuneLite 1.13.0 or newer, Java 11 or newer, and the system is named |
| 9 | Clock sane | the wall clock never jumped back |
| 10 | Not stuck measuring | once logged in for over 60 s, the card has left "Measuring" |

The verdict is the plain-words reason of the **first check that fails** ("Not logged in: nothing is measured yet"),
or "Everything is being measured." when none does. A check that cannot apply because nobody is logged in passes with
"not logged in, not checked", so you see one failure, not four.

**The report**, in plain ASCII, in this order: the version line (plugin version, world, start and end time, minutes);
**Verdict**; **Checks** (one line each, PASS or FAIL); **Now** (the answer and the three readings); the session's
lags (a count by group, then one line per lag: time, length, group, how sure, the headline, the proof and the fix);
the **settings** that matter (RuneLite's version, renderer, draw distance and anti-aliasing, frame cap and who set
it); **Last 60 minutes**, one line per minute; and three logs, **Notes**, **Warnings** and **Errors**, each
saying "(none)" when empty.

A minute line reads `21:47  fps 48/50/51  tick 601/952 ms  ping 40-43 ms  lags 1  masked 0 s`: the lowest, mean and
highest frame rate, the mean and worst tick gap, the lowest and highest ping, the lags that started in it, and the
seconds left out (a login, a hop or a map load is not lag).

**If the plugin's own thread does not answer within 3 seconds**, the window stops waiting and fills with a fallback
report made from the last reading, and says so: its verdict is "Sampler did not answer in 3 s" and the Checks part
reads "not run". A real answer that comes later is ignored.

## The game badge

A small badge on the game screen that follows the same answer as the card. Its **Style** picks one of four:

- **Icon** - a picture of the cause (a globe for the world, a Wi-Fi mark for your connection, a screen for your PC, a
  question mark for "can't tell") with the status shape in its corner.
- **Icon and words** - the icon, and the answer's two lines beside it.
- **Shape and words** - the status shape, and the two lines.
- **Shape only** - the status shape alone; the hover tells the causes apart.

The two styles without words are a real RuneLite infobox: they sit in the infobox row and you move, flip or detach
them with RuneLite's own menu. The two with words are an overlay, top left by default, moved with Alt + drag.

While all is well the badge is a green circle - or nothing at all with **Show badge when smooth** off. A lag shows
the answer in amber or red. When the lag ends the badge stays for 15 seconds, dimmed, then goes back. A slow spell
that is not a lag by itself (a capped frame rate, a high steady ping, slow drawing, a slow world) is shown in amber
or red without dimming, whatever the smooth setting says. "Measuring" and "Waiting" show a hollow ring (nothing, with
the smooth setting off); "Not logged in" shows nothing.

Hover it for two lines. On a lag: the answer in one line, then the numbers of that cause - "Ticks 1,240 ms, ping 41
ms" for the world, "Ping 310 ms, ticks 1,240 ms" for your connection, "Worst frame 480 ms, 50 fps" for your PC,
"Ticks 1,240 ms, worst frame 170 ms" when it cannot tell.

With **Chat line on a lag** on, one game message follows each lag: "[Why Lag] World lag - not you (14 s). Ticks 1,240
ms, ping 41 ms." At most one every 30 seconds, so a bad minute cannot fill the chat.

## How it decides

Once a second the plugin reads the frames, the server ticks and the game connection (ping, and packets that had to be
sent again). A **lag** starts at the first of these: a single frame that took 200 ms or more (longer when a low frame
cap makes frames slower); a tick 250 ms or more off; no tick for 1.2 seconds while frames still draw; 1 % or more of
what your PC sent being sent again; a lost connection; a map load of 2 seconds or more. A ping spike on its own does
not start one. A lag ends after 5 quiet seconds, and one that would pass 2 minutes closes and is then judged as a
condition. The seconds after a login, a hop, a lost connection or a load are set aside, so they cannot cause a false
alarm.

Each lag is scored against the causes below, and the winner names it - but only if it beats the runner-up by 15
points. If not, the answer is **Lag / Can't tell why**, with "Wait for it to happen again." Every cause has a ceiling
on how sure it will say it is, and a check that could not be made (no ping reading) lowers that by a step - or, when
the cause needs that check, rules the cause out. The card holds an answer at least 10 seconds, and about 15 seconds
after a lag's last bad moment it goes back to Smooth ("No lag for 10 s", then minutes), or to a slow spell that is
still going on. A slow spell without a lag of its own - a capped rate, a high ping, slow drawing, a slow world - shows
on the card once it has held for 10 seconds in a row.

| Answer | Fix | Says this when |
|---|---|---|
| **Disconnected** / Line or world | wait a minute and log in again; if ping or re-sends were bad just before, check cable or Wi-Fi when every world does it | the connection dropped; the proof says how ping and re-sends looked just before |
| **Map loading** / Just the map | lower Extended map loading (GPU and 117 HD); else nothing to fix | a map load of 2 seconds or more |
| **Packet loss** / Line or world | if every world does it, check cable or Wi-Fi | 1 in 100 or more of what your PC sent had to be sent again (late ticks and a ping spike back it up) |
| **Ping lag** / Your internet | use a cable, not Wi-Fi; pause downloads | the ping spiked - to double its usual and 50 ms over it - while ticks came both early and late and frames kept up |
| **World lag** / Not you | hop to a quieter world | ticks ran slow (a median gap of 660 ms or more over at least 5 ticks) while ping stayed within 20 ms of its usual, frames were clean and nothing was re-sent; or the same over the last minute (620 ms or more) |
| **Client froze** / Your PC | turn plugins off one at a time; try more memory for RuneLite | a frame took 200 ms or more (longer with a low cap) and nothing else explains it: no map load, no cap of yours pacing the frames, no re-sent packets. At most a Hint |
| **No ticks** / Line or world | hop worlds; if it follows you, it is your line | no tick for 1.2 seconds with frames and ping fine |
| **FPS capped** / Your setting | raise or turn off that cap | a frame cap you set (FPS Control, or the FPS target or V-Sync of the GPU plugin or 117 HD) holds the rate under 40 for 10 seconds |
| **High ping** / Your internet | try a world closer to you | a ping of 150 ms or more that stays steady over the last minute |
| **Low FPS** / Your PC | turn the GPU plugin on (on the client's own renderer); else lower draw distance or anti-aliasing | under 40 frames a second for 10 seconds in a row |
| **Lag** / Can't tell why | wait for it to happen again | two causes score close together, or nothing measurable explains the spell |

Three states are not verdicts: **Not logged in** ("Log in to start measuring."), **Measuring** (before the first
reading) and **Waiting** ("No frames are being drawn." - logged in, no frame for more than 2 seconds).

## Settings

RuneLite's settings page for 2h Why Lag has four items, all under **Game screen**. The panel's range chips are the
only place the graph range is set.

- **Show on game screen** (on) - "A small badge on the game screen that shows what is lagging".
- **Style** (Icon) - "A picture of the cause, the picture with words, a shape with words, or the shape alone".
- **When smooth** (Show) - "Show the green circle while all is well, or hide the badge until something lags".
- **Chat line on a lag** (on) - "One game message after each lag that names the cause".

The gear menu holds the same four, stored under the same names, so either place works.

## Privacy and network

**The plugin sends nothing at all**: no request, no ping, no packet. Nothing is uploaded, and it writes no file of its
own. Everything it reads stays in memory, for the last 60 minutes, until you close the client.

- The **ping** is the game's own connection's round trip, read with RuneLite's own call (the one the world switcher
  uses), which also says how much was sent and sent again. No packet is sent for it.
- Whether **GPU rendering** is on comes from RuneLite's API. To explain a low frame rate the plugin **reads** the GPU
  plugin's, 117 HD's and FPS Control's settings through RuneLite's settings storage; it changes none of them.
- **No JVM counters of any kind are read** - no memory, no processor, no garbage collection - and the plugin makes no
  JVM management or runtime calls and uses no reflection. A guard test in the repository fails on any of them.
- **The report** names no player, no account and no address, and any file path in it prints as `<path>`; the most
  personal things in it are the world number, the RuneLite and Java versions and the name of your operating system.
  **Copy report** puts it on your clipboard and nowhere else.

## Getting started

Install it and play. It measures from the moment you log in; there is nothing to set up. Open the sidebar for the
answer and the fix, or just watch the badge. Open **Graphs** and **Lags** for the detail. To report a bug, open the
gear, choose **Troubleshoot...**, press **Copy report** and paste the text into your post.

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
- **Memory and the processor are not read.** The Plugin Hub does not allow a plugin the calls that would read them,
  so a pause caused by memory shows as "Client froze", and its fix says to try more memory for RuneLite.

## Licence

BSD 2-Clause. See `LICENSE`.
