#include "Playlists/Radio/RadioPlaylist.h"
#include "Config/AppState.h"
#include "Misc/Song.h"
#include "MusicLibrary/MusicLibrary.h"

#include "RadioPlaylistDlg.h"
#include <QMenu>

RadioPlaylist::RadioPlaylist(const QString& sTitle, const QString& guid)
    : Playlist(sTitle, guid)
{
}

void RadioPlaylist::Refresh(PlaylistRefreshReason reason)
{
  m_Songs.resize(1);
  const auto& songs = MusicLibrary::GetSingleton()->LookupSongs("artist='Shinedown'");
  m_Songs[0] = songs[rand() % songs.size()].m_sSongGuid;

  m_bLoop = true;
}

void RadioPlaylist::ExtendContextMenu(QMenu* pMenu)
{
  connect(pMenu->addAction("Edit Radio Playlist..."), &QAction::triggered, this, &RadioPlaylist::onShowEditDlg);
}

int RadioPlaylist::GetNumSongs() const
{
  return (int)m_Songs.size();
}

QIcon RadioPlaylist::GetIcon() const
{
  return QIcon(":/icons/icons/playlist_radio.png");
}

void RadioPlaylist::SetTitle(const QString& title)
{
  if (m_sTitle != title)
  {
    m_bWasModified = true;

    RadioPlaylistModification mod;
    mod.m_Type = RadioPlaylistModification::Type::RenamePlaylist;
    mod.m_sIdentifier = title;
    m_Recorder.AddModification(mod, this);

    emit TitleChanged(m_sTitle);
  }
}

bool RadioPlaylist::CanSort()
{
  return false;
}

PlaylistCategory RadioPlaylist::GetCategory()
{
  return PlaylistCategory::Radio;
}

bool RadioPlaylist::CanModifySongList() const
{
  return false;
}

bool RadioPlaylist::CanAddSong(const QString& songGuid) const
{
  return false;
}

void RadioPlaylist::AddSong(const QString& songGuid)
{
  // TODO: emit proper model signals
  beginResetModel();
  endResetModel();

  m_CachedTotalDuration = 0;
  emit StatsChanged();
}

const QString RadioPlaylist::GetSongGuid(int index) const
{
  if (index < 0 || index >= m_Songs.size())
    return QString();

  return m_Songs[index];
}

void RadioPlaylist::RemoveSong(int index)
{
  Playlist::RemoveSong(index);

  // TODO: emit proper model signals
  beginResetModel();
  endResetModel();

  m_CachedTotalDuration = 0;
  emit StatsChanged();
}

bool RadioPlaylist::TryActivateSong(const QString& songGuid)
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

bool RadioPlaylist::CanSerialize()
{
  return true;
}

void RadioPlaylist::Save(QDataStream& stream)
{
  m_Recorder.CoalesceEntries();
  m_Recorder.Save(stream);
}

void RadioPlaylist::Load(QDataStream& stream)
{
  beginResetModel();

  m_Songs.clear();

  m_Recorder.LoadAdditional(stream);
  m_Recorder.ApplyAll(this);

  endResetModel();
}

bool RadioPlaylist::LookupSongByIndex(int index, SongInfo& song) const
{
  QString guid = m_Songs[index];
  return MusicLibrary::GetSingleton()->FindSong(guid, song);
}

bool RadioPlaylist::ContainsSong(const QString& songGuid)
{
  return std::find(m_Songs.begin(), m_Songs.end(), songGuid) != m_Songs.end();
}

double RadioPlaylist::GetTotalDuration()
{
  if (m_CachedTotalDuration == 0)
  {
    for (const QString& guid : m_Songs)
    {
      m_CachedTotalDuration += GetSongDuration(guid);
    }
  }

  return m_CachedTotalDuration;
}

void RadioPlaylist::onShowEditDlg()
{
  RadioPlaylistDlg dlg(this, nullptr);

  if (dlg.exec() == QDialog::Accepted)
  {
    m_bWasModified = true;

    //RadioPlaylistModification mod;
    //mod.m_Type = RadioPlaylistModification::Type::ChangeQuery;

    //m_Recorder.AddModification(mod, this);

    Refresh(PlaylistRefreshReason::PlaylistModified);
  }
}

void RadioPlaylist::ReachedEnd()
{
  const auto& songs = MusicLibrary::GetSingleton()->LookupSongs("artist='Shinedown'");
  m_Songs[0] = songs[rand() % songs.size()].m_sSongGuid;

  Playlist::ReachedEnd();
}

QModelIndex RadioPlaylist::index(int row, int column, const QModelIndex& parent /*= QModelIndex()*/) const
{
  if (parent.isValid())
    return QModelIndex();

  return createIndex(row, column);
}

QModelIndex RadioPlaylist::parent(const QModelIndex& child) const
{
  return QModelIndex();
}

int RadioPlaylist::rowCount(const QModelIndex& parent /*= QModelIndex()*/) const
{
  if (!parent.isValid())
  {
    return (int)m_Songs.size();
  }

  return 0;
}

QVariant RadioPlaylist::data(const QModelIndex& index, int role /*= Qt::DisplayRole*/) const
{
  const QString& songGuid = m_Songs[index.row()];

  return commonData(index, role, songGuid);
}

void RadioPlaylist::sort(int column, Qt::SortOrder order /*= Qt::AscendingOrder*/)
{
}

QString RadioPlaylist::GetFactoryName() const
{
  return "RadioPlaylist";
}

void RadioPlaylistModification::Apply(RadioPlaylist* pContext) const
{
  if (m_Type == Type::RenamePlaylist)
  {
    pContext->m_sTitle = m_sIdentifier;
    return;
  }

  if (m_Type == Type::ChangeSettings)
  {
    pContext->m_Settings = m_Settings;
    return;
  }
}

void RadioPlaylistModification::Save(QDataStream& stream) const
{
  stream << (int)m_Type;
  stream << m_sIdentifier;

  if (m_Type == Type::RenamePlaylist)
  {
    stream << m_sIdentifier;
  }

  if (m_Type == Type::ChangeSettings)
  {
    m_Settings.Save(stream);
  }
}

void RadioPlaylistModification::Load(QDataStream& stream)
{
  int type = 0;
  stream >> type;
  m_Type = (Type)type;
  stream >> m_sIdentifier;

  if (m_Type == Type::RenamePlaylist)
  {
    stream >> m_sIdentifier;
  }

  if (m_Type == Type::ChangeSettings)
  {
    m_Settings.Load(stream);
  }
}

void RadioPlaylistModification::Coalesce(ModificationRecorder<RadioPlaylistModification, RadioPlaylist*>& recorder, size_t passThroughIndex)
{
  switch (m_Type)
  {
  case Type::None:
  {
    recorder.InvalidateThis(passThroughIndex);
    break;
  }

  case Type::RenamePlaylist:
  {
    recorder.InvalidatePrevious(passThroughIndex, [](const RadioPlaylistModification& mod) -> bool {
      // remove all previous renames
      return mod.m_Type == Type::RenamePlaylist;
    });

    break;
  }

  case Type::ChangeSettings:
  {
    recorder.InvalidatePrevious(passThroughIndex, [this](const RadioPlaylistModification& mod) -> bool {
      // remove all other query changes
      return mod.m_Type == Type::ChangeSettings;
    });
  }
  break;
  }
}

void RadioPlaylistSettings::Save(QDataStream& stream) const
{
  const int version = 1;
  stream << version;

  int num = m_Items.size();
  stream << num;

  for (int i = 0; i < num; ++i)
  {
    stream << m_Items[i].m_bEnabled;
    stream << m_Items[i].m_sPlaylistGuid;
    stream << m_Items[i].m_iLikelyhood;
  }
}

void RadioPlaylistSettings::Load(QDataStream& stream)
{
  int version = 0;
  stream >> version;

  if (version != 1)
    return;

  int num = 0;
  stream >> num;
  m_Items.resize(num);

  for (int i = 0; i < num; ++i)
  {
    stream >> m_Items[i].m_bEnabled;
    stream >> m_Items[i].m_sPlaylistGuid;
    stream >> m_Items[i].m_iLikelyhood;
  }
}
