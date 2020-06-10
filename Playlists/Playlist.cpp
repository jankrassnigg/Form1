#include "Config/AppConfig.h"
#include "Config/AppState.h"
#include "Playlists/Playlist.h"
#include <QFile>
#include <QMimeData>
#include <algorithm>
#include <random>
#include <QColor>
#include <QFont>

Playlist::Playlist(const QString& sTitle, const QString& guid)
{
  m_sTitle = sTitle;
  m_sGuid = guid;

  connect(AppState::GetSingleton(), &AppState::ActiveSongChanged, this, &Playlist::onActiveSongChanged);
}

int Playlist::columnCount(const QModelIndex& parent /*= QModelIndex()*/) const
{
  return PlaylistColumn::ENUM_COUNT;
}

QVariant Playlist::headerData(int section, Qt::Orientation orientation, int role /*= Qt::DisplayRole*/) const
{
  if (role == Qt::DisplayRole)
  {
    switch (section)
    {
    case PlaylistColumn::Rating:
      return "Rating";

    case PlaylistColumn::Title:
      return "Title";

    case PlaylistColumn::Length:
      return "Duration";

    case PlaylistColumn::Artist:
      return "Artist";

    case PlaylistColumn::Album:
      return "Album";

    case PlaylistColumn::TrackNumber:
      return "Track";

    case PlaylistColumn::LastPlayed:
      return "Last Played";

    case PlaylistColumn::PlayCount:
      return "Count";

    case PlaylistColumn::DateAdded:
      return "Date Added";

    case PlaylistColumn::Order:
      return "#";
    }
  }

  return QVariant();
}

void Playlist::RemoveSong(int index)
{
  AdjustShuffleAfterSongRemoved(index);
}

void Playlist::SetActiveSong(int index)
{
  index = Clamp(index, -1, GetNumSongs() - 1);

  if (m_bActiveSongIndexValid && m_iActiveSong == index)
    return;

  // previously inactive -> reshuffle
  if (m_iActiveSong < 0)
  {
    Reshuffle();
  }

  m_bActiveSongIndexValid = true;
  m_iActiveSong = index;
  emit ActiveSongChanged(m_iActiveSong);

  RemoveSongFromShuffle(m_iActiveSong);
}

void Playlist::ActivateNextSong()
{
  if (GetNumSongs() == 0)
    return;

  int iNextSong = -1;

  if (m_bShuffle)
  {
    iNextSong = GetNextShuffledSongIndex();
  }
  else
  {
    if (m_iActiveSong + 1 < GetNumSongs())
    {
      iNextSong = m_iActiveSong + 1;
    }
  }

  if (iNextSong < 0)
  {
    ReachedEnd();
  }
  else
  {
    SetActiveSong(iNextSong);
  }
}

void Playlist::SaveToFile(const QString& sFile, bool bForce)
{
  if (!CanSerialize() || (!bForce && !m_bWasModified))
    return;

  QFile file(sFile);
  if (!file.open(QIODevice::OpenModeFlag::WriteOnly))
    return;

  {
    QDataStream stream(&file);

    stream << m_sGuid;
    stream << GetFactoryName();
    stream << m_sTitle;

    Save(stream);

    file.close();
  }

  DeletePlaylistFiles();

  m_bWasModified = false;
}

void Playlist::AddFileToDeleteOnSave(const QString& file)
{
  m_FilesToDeleteOnSave.push_back(file);
}

void Playlist::DeletePlaylistFiles()
{
  for (const QString& s : m_FilesToDeleteOnSave)
  {
    QFile::remove(s);
  }

  ClearFilesToDeleteOnSave();
}

void Playlist::ClearFilesToDeleteOnSave()
{
  m_FilesToDeleteOnSave.clear();
}

void Playlist::SetLoop(bool loop)
{
  if (m_bLoop == loop)
    return;

  m_bLoop = loop;
  emit LoopShuffleStateChanged();
}

void Playlist::SetShuffle(bool shuffle)
{
  if (m_bShuffle == shuffle)
    return;

  m_bShuffle = shuffle;
  emit LoopShuffleStateChanged();
}

void Playlist::onActiveSongChanged()
{
  beginResetModel();
  endResetModel();
}

void Playlist::ReachedEnd()
{
  SetActiveSong(-1);

  if (m_bLoop)
  {
    Reshuffle();
    ActivateNextSong();
  }
}

QVariant Playlist::commonData(const QModelIndex& index, int role, const QString& sSongGuid) const
{
  if (role == Qt::UserRole + 1)
  {
    return sSongGuid;
  }

  if (role == Qt::BackgroundColorRole)
  {
    SongInfo song;
    if (!LookupSongByIndex(index.row(), song))
    {
      return QColor::fromRgb(255, 130, 130);
    }
  }

  if (role == Qt::DisplayRole)
  {
    SongInfo song;
    if (!LookupSongByIndex(index.row(), song))
    {
      if (index.column() == 1)
        return "<Invalid Song>";
      else
        return QVariant();
    }

    switch (index.column())
    {
    case PlaylistColumn::Rating:
      return song.m_iRating;
    case PlaylistColumn::Title:
      return song.m_sTitle;
    case PlaylistColumn::Length:
      return ToTime(song.m_iLengthInMS);
    case PlaylistColumn::Artist:
      return song.m_sArtist;
    case PlaylistColumn::Album:
      return song.m_sAlbum;
    case PlaylistColumn::TrackNumber:
      if (song.m_iDiscNumber > 0)
        return QString("%1 (%2)").arg(song.m_iTrackNumber).arg(song.m_iDiscNumber);
      else
        return QString("%1").arg(song.m_iTrackNumber);
    case PlaylistColumn::LastPlayed:
      return song.m_sLastPlayed;
    case PlaylistColumn::PlayCount:
      return song.m_iPlayCount;
    case PlaylistColumn::DateAdded:
      return song.m_sDateAdded;
    case PlaylistColumn::Order:
      return index.row() + 1;

    default:
      return "";
    }
  }

  if (role == Qt::FontRole)
  {
    if (AppState::GetSingleton()->GetActivePlaylist() == this && m_bActiveSongIndexValid && m_iActiveSong == index.row())
    {
      QFont font;
      font.setBold(true);
      return font;
    }
    else if (AppState::GetSingleton()->GetActiveSongGuid() == sSongGuid)
    {
      QFont font;
      font.setItalic(true);
      return font;
    }
  }

  return QVariant();
}

double Playlist::GetSongDuration(const QString& guid)
{
  SongInfo info;
  if (!MusicLibrary::GetSingleton()->FindSong(guid, info))
    return 0;

  return info.m_iLengthInMS /1000.0;
}

void Playlist::Reshuffle()
{
  m_songShuffleOrder.resize(GetNumSongs());

  int num = GetNumSongs();
  for (int i = 0; i < num; ++i)
  {
    m_songShuffleOrder[i] = i;
  }

  std::random_device rd;
  std::mt19937 g(rd());

  std::shuffle(m_songShuffleOrder.begin(), m_songShuffleOrder.end(), g);
}

int Playlist::GetNextShuffledSongIndex()
{
  if (m_songShuffleOrder.empty())
    return -1;

  const int res = m_songShuffleOrder.back();
  m_songShuffleOrder.pop_back();

  return res;
}

void Playlist::RemoveSongFromShuffle(int index)
{
  auto it = std::find(m_songShuffleOrder.begin(), m_songShuffleOrder.end(), index);

  if (it != m_songShuffleOrder.end())
  {
    m_songShuffleOrder.erase(it);
  }
}

void Playlist::AdjustShuffleAfterSongRemoved(int index)
{
  RemoveSongFromShuffle(index);

  for (size_t i = 0; i < m_songShuffleOrder.size(); ++i)
  {
    if (m_songShuffleOrder[i] >= index)
    {
      --m_songShuffleOrder[i];
    }
  }
}

QMimeData* Playlist::mimeData(const QModelIndexList& indexes) const
{
  QMimeData* pMimedata = new QMimeData();

  QByteArray encodedData;
  QDataStream stream(&encodedData, QIODevice::WriteOnly);

  for (const QModelIndex& index : indexes)
  {
    if (index.isValid() && index.column() == 0)
    {
      QVariant guid = data(index, Qt::UserRole + 1);

      if (guid.isValid())
      {
        stream << guid.toString();
      }
    }
  }

  pMimedata->setData("application/songguids.form1", encodedData);
  return pMimedata;
}

Qt::ItemFlags Playlist::flags(const QModelIndex& index) const
{
  Qt::ItemFlags defaultFlags = QAbstractItemModel::flags(index);

  if (index.column() == 0)
    defaultFlags |= Qt::ItemFlag::ItemIsEditable;

  return defaultFlags | Qt::ItemFlag::ItemIsDragEnabled;
}

void Playlist::SortPlaylistData(std::vector<SortPlaylistEntry>& infos, PlaylistColumn column, bool bDescending)
{
  switch (column)
  {
  case PlaylistColumn::Order:
    // never change the order
    return;

  case PlaylistColumn::Rating:
    {
      std::stable_sort(infos.begin(), infos.end(), [](const SortPlaylistEntry& lhs, const SortPlaylistEntry& rhs)->bool
      {
        return lhs.m_Info.m_iRating < rhs.m_Info.m_iRating;
      });
    }
    break;

  case PlaylistColumn::Title:
    {
      std::stable_sort(infos.begin(), infos.end(), [](const SortPlaylistEntry& lhs, const SortPlaylistEntry& rhs)->bool
      {
        return lhs.m_Info.m_sTitle < rhs.m_Info.m_sTitle;
      });
    }
    break;

  case PlaylistColumn::Length:
    {
      std::stable_sort(infos.begin(), infos.end(), [](const SortPlaylistEntry& lhs, const SortPlaylistEntry& rhs)->bool
      {
        return lhs.m_Info.m_iLengthInMS < rhs.m_Info.m_iLengthInMS;
      });
    }
    break;

  case PlaylistColumn::Artist:
    {
      std::stable_sort(infos.begin(), infos.end(), [](const SortPlaylistEntry& lhs, const SortPlaylistEntry& rhs)->bool
      {
        return lhs.m_Info.m_sArtist < rhs.m_Info.m_sArtist;
      });
    }
    break;

  case PlaylistColumn::Album:
    {
      std::stable_sort(infos.begin(), infos.end(), [](const SortPlaylistEntry& lhs, const SortPlaylistEntry& rhs)->bool
      {
        return lhs.m_Info.m_sAlbum < rhs.m_Info.m_sAlbum;
      });
    }
    break;

  case PlaylistColumn::TrackNumber:
    {
      std::stable_sort(infos.begin(), infos.end(), [](const SortPlaylistEntry& lhs, const SortPlaylistEntry& rhs)->bool
      {
        if (lhs.m_Info.m_iDiscNumber < rhs.m_Info.m_iDiscNumber)
          return true;
        if (lhs.m_Info.m_iDiscNumber > rhs.m_Info.m_iDiscNumber)
          return false;

        return lhs.m_Info.m_iTrackNumber < rhs.m_Info.m_iTrackNumber;
      });
    }
    break;

  case PlaylistColumn::LastPlayed:
    {
      std::stable_sort(infos.begin(), infos.end(), [](const SortPlaylistEntry& lhs, const SortPlaylistEntry& rhs)->bool
      {
        return lhs.m_Info.m_iLastPlayed < rhs.m_Info.m_iLastPlayed;
      });
    }
    break;

  case PlaylistColumn::PlayCount:
    {
      std::stable_sort(infos.begin(), infos.end(), [](const SortPlaylistEntry& lhs, const SortPlaylistEntry& rhs)->bool
      {
        return lhs.m_Info.m_iPlayCount < rhs.m_Info.m_iPlayCount;
      });
    }
    break;

  case PlaylistColumn::DateAdded:
    {
      std::stable_sort(infos.begin(), infos.end(), [](const SortPlaylistEntry& lhs, const SortPlaylistEntry& rhs)->bool
      {
        return lhs.m_Info.m_iDateAdded < rhs.m_Info.m_iDateAdded;
      });
    }
    break;
  }

  if (bDescending)
  {
    std::reverse(infos.begin(), infos.end());
  }

  const size_t numSongs = infos.size();

  if (m_iActiveSong >= 0)
  {
    for (size_t i = 0; i < numSongs; ++i)
    {
      if (infos[i].m_iOldIndex == m_iActiveSong)
      {
        m_iActiveSong = (int)i;
        break;
      }
    }
  }

  for (size_t oldIdx = 0; oldIdx < m_songShuffleOrder.size(); ++oldIdx)
  {
    for (size_t newIdx = 0; newIdx < numSongs; ++newIdx)
    {
      if (infos[newIdx].m_iOldIndex == m_songShuffleOrder[oldIdx])
      {
        m_songShuffleOrder[oldIdx] = (int)newIdx;
        break;
      }
    }
  }
}
