#pragma once

#include "Misc/Common.h"

class SongInfo
{
public:
  enum Part
  {
    Title = 1 << 0,
    Artist = 1 << 1,
    Album = 1 << 2,
    Track = 1 << 3,
    DiscNumber = 1 << 4,
    Year = 1 << 5,
    Rating = 1 << 6,
    Volume = 1 << 7,
    StartOffset = 1 << 8,
    EndOffset = 1 << 9,
  };

  QString m_sSongGuid;
  QString m_sTitle;
  QString m_sArtist;
  QString m_sAlbum;
  short m_iTrackNumber = 0; // 0 = not set
  short m_iYear = 0;
  int m_iLengthInMS = 0;
  signed char m_iRating = 0;
  signed char m_iVolume = 0;
  signed char m_iDiscNumber = 0;
  int m_iStartOffset = 0;
  int m_iEndOffset = 0;
  int m_iLastPlayed = -1;
  int m_iDateAdded = -1;
  int m_iPlayCount = 0;
  QString m_sLastPlayed;
  QString m_sDateAdded;

  void Clear();
  bool ReadSongInfo(const QString& sFile);

  static bool ModifyFileTag(const QString& file, const SongInfo& info, unsigned int PartMask);
};

struct SortPlaylistEntry
{
  SongInfo m_Info;
  size_t m_iOldIndex;
};
