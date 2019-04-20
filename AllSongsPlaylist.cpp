#include "AllSongsPlaylist.h"
#include "AppState.h"
#include "MusicLibrary.h"
#include <QFont>

AllSongsPlaylist::AllSongsPlaylist()
    : Playlist("All Songs", "<all>")
{
}

void AllSongsPlaylist::Refresh()
{
  std::deque<SongInfo> songs = MusicLibrary::GetSingleton()->GetAllSongs();

  beginResetModel();

  m_AllSongs.clear();
  for (const SongInfo& si : songs)
  {
    m_AllSongs.push_back(si.m_sSongGuid);
  }

  endResetModel();
}

int AllSongsPlaylist::GetNumSongs() const
{
  return (int)m_AllSongs.size();
}

QIcon AllSongsPlaylist::GetIcon() const
{
  return QIcon(":/icons/icons/playlist_all.png");
}

PlaylistCategory AllSongsPlaylist::GetCategory()
{
  return PlaylistCategory::BuiltIn_All;
}

void AllSongsPlaylist::SetTitle(const QString& title)
{
  // do nothing
}

bool AllSongsPlaylist::CanModifySongList() const
{
  return false;
}

bool AllSongsPlaylist::CanAddSong(const QString& songGuid) const
{
  return false;
}

void AllSongsPlaylist::AddSong(const QString& songGuid)
{
}

const QString AllSongsPlaylist::GetSongGuid(int index) const
{
  if (index < 0 || index >= m_AllSongs.size())
    return QString();

  return m_AllSongs[index];
}

void AllSongsPlaylist::RemoveSong(int index)
{
}

bool AllSongsPlaylist::TryActivateSong(const QString& songGuid)
{
  for (size_t i = 0; i < m_AllSongs.size(); ++i)
  {
    if (m_AllSongs[i] == songGuid)
    {
      SetActiveSong((int)i);
      return true;
    }
  }

  return false;
}

bool AllSongsPlaylist::CanSerialize()
{
  return false;
}

void AllSongsPlaylist::Save(QDataStream& stream)
{
  throw std::logic_error("The method or operation is not implemented.");
}

void AllSongsPlaylist::Load(QDataStream& stream)
{
  throw std::logic_error("The method or operation is not implemented.");
}

bool AllSongsPlaylist::LookupSongByIndex(int index, SongInfo& song) const
{
  QString guid = m_AllSongs[index];
  return MusicLibrary::GetSingleton()->FindSong(guid, song);
}

QModelIndex AllSongsPlaylist::index(int row, int column, const QModelIndex& parent /*= QModelIndex()*/) const
{
  if (parent.isValid())
    return QModelIndex();

  return createIndex(row, column);
}

QModelIndex AllSongsPlaylist::parent(const QModelIndex& child) const
{
  return QModelIndex();
}

int AllSongsPlaylist::rowCount(const QModelIndex& parent /*= QModelIndex()*/) const
{
  if (!parent.isValid())
  {
    return GetNumSongs();
  }

  return 0;
}

QVariant AllSongsPlaylist::data(const QModelIndex& index, int role /*= Qt::DisplayRole*/) const
{
  const QString& songGuid = m_AllSongs[index.row()];

  return commonData(index, role, songGuid);
}

QString AllSongsPlaylist::GetFactoryName() const
{
  return "AllSongsPlaylist";
}

void AllSongsPlaylist::sort(int column, Qt::SortOrder order /*= Qt::AscendingOrder*/)
{
  const size_t numSongs = m_AllSongs.size();

  std::vector<SortPlaylistEntry> infos;
  infos.resize(numSongs);

  for (size_t i = 0; i < numSongs; ++i)
  {
    infos[i].m_iOldIndex = i;
    MusicLibrary::GetSingleton()->FindSong(m_AllSongs[i], infos[i].m_Info);
  }

  SortPlaylistData(infos, (PlaylistColumn)column, order == Qt::DescendingOrder);

  beginResetModel();

  for (size_t i = 0; i < numSongs; ++i)
  {
    m_AllSongs[i] = infos[i].m_Info.m_sSongGuid;
  }

  endResetModel();
}
