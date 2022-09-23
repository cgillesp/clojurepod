# Schema


## Channel

iTunes ID

Feed: 
- title
  - itunes:title
- description
  - itunes:summary
  - itunes:subtitle
- itunes:image
- language
- itunes:category $
- itunes:explicit

(Recommended)
- itunes:author
- link
- itunes:owner $

(Situational)
- itunes:title
- itunes:type (episodic: newest, serial: oldest)
- copyright
- itunes:new-feed-url $
- itunes:block
- itunes:complete

$ = Not in DB
New feed URL should be handled in code
Owner is irrelevant
Category needs special handling (bridging relationship?)

## Episode

- title
  - itunes:title
- enclosure
  - URL
  - Length
  - Type

(Recommended)
- guid
- pubDate
- description
  - itunes:summary
  - itunes:subtitle
- itunes:duration
- link
- itunes:image
- itunes:explicit

(Situational)
- itunes:title
- itunes:episode
- itunes:season
- itunes:season
- itunes:episodeType (Full, Trailer, or Bonus)
- itunes:block



