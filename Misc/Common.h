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
double Clamp(double val, double minVal, double maxVal);

QString ToTime(quint64 ms);
int Min(int l, int r);
int Max(int l, int r);

enum class PlaylistCategory
{
  BuiltIn_All = 0,
  Procedural = 5,
  Radio = 10,
  Regular = 15,
};

enum PlaylistColumn
{
  Rating,
  Title,
  Length,
  Artist,
  Album,
  TrackNumber,
  PlayCount,
  LastPlayed,
  DateAdded,
  Order,

  ENUM_COUNT
};
