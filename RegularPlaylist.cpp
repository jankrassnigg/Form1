#include "RegularPlaylist.h"
#include "AppState.h"
#include "MusicLibrary.h"
#include "Song.h"

RegularPlaylist::RegularPlaylist(const QString& sTitle, const QString& guid)
    : Playlist(sTitle, guid)
{
}

void RegularPlaylist::Refresh()
{
  beginResetModel();
  endResetModel();
}

int RegularPlaylist::GetNumSongs() const
{
  return (int)m_Songs.size();
}

QIcon RegularPlaylist::GetIcon() const
{
  return QIcon(":/icons/icons/playlist_regular.png");
}

void RegularPlaylist::SetTitle(const QString& title)
{
  if (m_sTitle != title)
  {
    m_bWasModified = true;

    RegularPlaylistModification mod;
    mod.m_Type = RegularPlaylistModification::Type::RenamePlaylist;
    mod.m_sIdentifier = title;
    m_Recorder.AddModification(mod, this);

    emit TitleChanged(m_sTitle);
  }
}

bool RegularPlaylist::CanSort()
{
  return true;
}

PlaylistCategory RegularPlaylist::GetCategory()
{
  return PlaylistCategory::Regular;
}

bool RegularPlaylist::CanModifySongList() const
{
  return true;
}

bool RegularPlaylist::CanAddSong(const QString& songGuid) const
{
  return true;
}

void RegularPlaylist::AddSong(const QString& songGuid)
{
  m_bWasModified = true;

  RegularPlaylistModification mod;
  mod.m_Type = RegularPlaylistModification::Type::AddSong;
  mod.m_sIdentifier = songGuid;

  m_Recorder.AddModification(mod, this);

  // TODO: emit proper model signals
  beginResetModel();
  endResetModel();
}

const QString RegularPlaylist::GetSongGuid(int index) const
{
  if (index < 0 || index >= m_Songs.size())
    return QString();

  return m_Songs[index];
}

void RegularPlaylist::RemoveSong(int index)
{
  Playlist::RemoveSong(index);

  m_bWasModified = true;

  if (m_iActiveSong == index)
    m_bActiveSongIndexValid = false;

  // adjust the active song index
  if (index <= m_iActiveSong)
    m_iActiveSong--;

  RegularPlaylistModification mod;
  mod.m_Type = RegularPlaylistModification::Type::RemoveSong;
  mod.m_sIdentifier = m_Songs[index];

  m_Recorder.AddModification(mod, this);

  // TODO: emit proper model signals
  beginResetModel();
  endResetModel();
}

bool RegularPlaylist::TryActivateSong(const QString& songGuid)
{
  auto it = std::find(m_Songs.begin(), m_Songs.end(), songGuid);

  if (it != m_Songs.end())
  {
    const size_t idx = it - m_Songs.begin();

    SetActiveSong((int)idx);
    return true;
  }

  return false;
}

bool RegularPlaylist::CanSerialize()
{
  return true;
}

void RegularPlaylist::Save(QDataStream& stream)
{
  m_Recorder.CoalesceEntries();
  m_Recorder.Save(stream);
}

void RegularPlaylist::Load(QDataStream& stream)
{
  beginResetModel();

  m_Songs.clear();

  m_Recorder.LoadAdditional(stream);
  m_Recorder.ApplyAll(this);

  endResetModel();
}

bool RegularPlaylist::LookupSongByIndex(int index, SongInfo& song) const
{
  QString guid = m_Songs[index];
  return MusicLibrary::GetSingleton()->FindSong(guid, song);
}

QModelIndex RegularPlaylist::index(int row, int column, const QModelIndex& parent /*= QModelIndex()*/) const
{
  if (parent.isValid())
    return QModelIndex();

  return createIndex(row, column);
}

QModelIndex RegularPlaylist::parent(const QModelIndex& child) const
{
  return QModelIndex();
}

int RegularPlaylist::rowCount(const QModelIndex& parent /*= QModelIndex()*/) const
{
  if (!parent.isValid())
  {
    return (int)m_Songs.size();
  }

  return 0;
}

QVariant RegularPlaylist::data(const QModelIndex& index, int role /*= Qt::DisplayRole*/) const
{
  const QString& songGuid = m_Songs[index.row()];

  return commonData(index, role, songGuid);
}

void RegularPlaylist::sort(int column, Qt::SortOrder order /*= Qt::AscendingOrder*/)
{
  const size_t numSongs = m_Songs.size();

  std::vector<SortPlaylistEntry> infos;
  infos.resize(numSongs);

  for (size_t i = 0; i < numSongs; ++i)
  {
    infos[i].m_iOldIndex = i;
    if (!MusicLibrary::GetSingleton()->FindSong(m_Songs[i], infos[i].m_Info))
    {
      infos[i].m_Info.m_sSongGuid = m_Songs[i];
    }
  }

  SortPlaylistData(infos, (PlaylistColumn)column, order == Qt::DescendingOrder);

  beginResetModel();

  for (size_t i = 0; i < numSongs; ++i)
  {
    m_Songs[i] = infos[i].m_Info.m_sSongGuid;
  }

  endResetModel();
}

QString RegularPlaylist::GetFactoryName() const
{
  return "RegularPlaylist";
}

void RegularPlaylistModification::Apply(RegularPlaylist* pContext) const
{
  if (m_Type == Type::AddSong)
  {
    auto it = std::find(pContext->m_Songs.begin(), pContext->m_Songs.end(), m_sIdentifier);

    // only insert once
    if (it == pContext->m_Songs.end())
      pContext->m_Songs.push_back(m_sIdentifier);

    return;
  }

  if (m_Type == Type::RemoveSong)
  {
    auto it = std::find(pContext->m_Songs.begin(), pContext->m_Songs.end(), m_sIdentifier);

    if (it != pContext->m_Songs.end())
      pContext->m_Songs.erase(it);

    return;
  }

  if (m_Type == Type::RenamePlaylist)
  {
    pContext->m_sTitle = m_sIdentifier;
    return;
  }
}

void RegularPlaylistModification::Save(QDataStream& stream) const
{
  stream << (int)m_Type;
  stream << m_sIdentifier;
}

void RegularPlaylistModification::Load(QDataStream& stream)
{
  int type = 0;
  stream >> type;
  m_Type = (Type)type;
  stream >> m_sIdentifier;
}

void RegularPlaylistModification::Coalesce(ModificationRecorder<RegularPlaylistModification, RegularPlaylist*>& recorder, size_t passThroughIndex)
{
  switch (m_Type)
  {
  case Type::None:
  {
    recorder.InvalidateThis(passThroughIndex);
    break;
  }

  case Type::AddSong:
  case Type::RemoveSong:
  {
    if (m_sIdentifier.isEmpty())
    {
      recorder.InvalidateThis(passThroughIndex);
      break;
    }

    recorder.InvalidatePrevious(passThroughIndex, [this](const RegularPlaylistModification& mod) -> bool {
      // remove all previous adds/removes of the same song
      if (mod.m_sIdentifier == this->m_sIdentifier)
      {
        return mod.m_Type == Type::AddSong || mod.m_Type == Type::RemoveSong;
      }

      return false;
    });

    break;
  }

  case Type::RenamePlaylist:
  {
    recorder.InvalidatePrevious(passThroughIndex, [](const RegularPlaylistModification& mod) -> bool {
      // remove all previous renames
      return mod.m_Type == Type::RenamePlaylist;
    });

    break;
  }
  }
}
