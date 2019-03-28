#pragma once

#include <QString>
#include <memory>
#include <vector>

using namespace std;

class Playlist;
class AllSongsPlaylist;
class MusicLibrary;
class AppState;
class SongInfo;
class AppConfig;
class QDataStream;
class QMenu;

struct SortPlaylistEntry;

int Clamp(int val, int minVal, int maxVal);
QString ToTime(quint64 ms);
int Min(int l, int r);
int Max(int l, int r);

enum class PlaylistCategory
{
  BuiltIn_All = 0,
  Procedural = 5,
  Regular = 10,
};

enum PlaylistColumn
{
  Rating,
  Title,
  Length,
  Artist,
  Album,
  TrackNumber,
  LastPlayed,
  PlayCount,
  DateAdded,

  ENUM_COUNT
};
