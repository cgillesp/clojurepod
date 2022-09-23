-- :name create-channels-table :!
CREATE TABLE IF NOT EXISTS channels (
itunesID bigint PRIMARY KEY,
feedUrl varchar,
title varchar,
itunesTitle varchar,
description varchar,
itunesSummary varchar,
itunesSubtitle varchar,
itunesImage varchar,
language varchar,
itunesExplicit boolean,
itunesAuthor varchar,
link varchar,
itunesType varchar,
copyright varchar,
itunesBlock boolean,
itunesComplete boolean,
lastFetched TIMESTAMP WITH TIME ZONE
);

-- :name create-episodes-table :!
CREATE TABLE IF NOT EXISTS episodes (
id bigint AUTO_INCREMENT PRIMARY KEY,
channel bigint,
title varchar,
itunesTitle varchar,
fileUrl varchar,
fileSize bigint,
fileType varchar,
guid varchar,
pubDate TIMESTAMP WITH TIME ZONE,
description varchar,
itunesSummary varchar,
itunesSubtitle varchar,
itunesDuration bigint,
link varchar,
itunesImage varchar,
itunesExplicit boolean,
itunesEpisode bigint,
itunesSeason bigint,
itunesEpisodeType varchar,
itunesBlock boolean,
lastFetched TIMESTAMP WITH TIME ZONE,

foreign key (channel) references channels(itunesID)
);

CREATE INDEX IF NOT EXISTS guid_idx ON episodes(guid);

-- :name create-topchart-table :!
CREATE TABLE IF NOT EXISTS topchart (
id bigint AUTO_INCREMENT PRIMARY KEY,
channel bigint,
rank int,
fetchedAt TIMESTAMP WITH TIME ZONE,

FOREIGN KEY (channel) REFERENCES channels(itunesID)
);

CREATE INDEX IF NOT EXISTS chart_fetched_idx on topchart(fetchedAt);

-- :name upsert-channel :!
MERGE INTO channels values (
:itunesID,
:feedUrl,
:title,
:itunesTitle,
:description,
:itunesSummary,
:itunesSubtitle,
:itunesImage,
:language,
:itunesExplicit,
:itunesAuthor,
:link,
:itunesType,
:copyright,
:itunesBlock,
:itunesComplete,
:lastFetched);

-- :name upsert-episode :!
-- :doc guid MUST NOT BE NULL
MERGE INTO episodes
(channel,
title,
itunesTitle,
fileUrl,
fileSize,
fileType,
guid,
pubDate,
description,
itunesSummary,
itunesSubtitle,
itunesDuration,
link,
itunesImage,
itunesExplicit,
itunesEpisode,
itunesSeason,
itunesEpisodeType,
itunesBlock,
lastFetched )
KEY(guid) values (
:channel,
:title,
:itunesTitle,
:fileUrl,
:fileSize,
:fileType,
:guid,
:pubDate,
:description,
:itunesSummary,
:itunesSubtitle,
:itunesDuration,
:link,
:itunesImage,
:itunesExplicit,
:itunesEpisode,
:itunesSeason,
:itunesEpisodeType,
:itunesBlock,
:lastFetched
);

-- :name add-to-topchart :!
INSERT INTO topchart (
channel,
rank,
fetchedAt
) VALUES (
:channel,
:rank,
:fetchedAt
);


-- :name channels-by-ids :? :*
SELECT * FROM channels WHERE itunesID in (:v*:ids) LIMIT 100;

-- :name eps-by-chanid :? :*
SELECT * FROM episodes WHERE channel = :id ORDER BY pubDate DESC;

-- :name latest-chart :?
SELECT c.*, tc.rank FROM topchart tc
INNER JOIN channels c
ON tc.channel = c.itunesID
WHERE
fetchedAt = (SELECT max(fetchedAt) FROM topchart)
ORDER BY rank ASC
LIMIT 200;
