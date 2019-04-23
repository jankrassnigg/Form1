#include "Config/AppState.h"
#include "MusicLibrary/MusicLibrary.h"
#include "SmartPlaylists/SmartPlaylist.h"
#include "SmartPlaylistDlg.h"
#include "Misc/Song.h"
#include <QColor>
#include <QFont>
#include <QMenu>
#include <random>

SmartPlaylist::SmartPlaylist(const QString& sTitle, const QString& guid)
    : Playlist(sTitle, guid)
{
  m_Query.m_MainGroup.m_Fulfil = SmartPlaylistQuery::Fulfil::Any;

  {
    SmartPlaylistQuery::Statement stmt;
    stmt.m_Criterium = SmartPlaylistQuery::Criterium::Artist;
    stmt.m_Compare = SmartPlaylistQuery::Comparison::IsUnknown;

    m_Query.m_MainGroup.m_Statements.push_back(stmt);
  }
}

void SmartPlaylist::Refresh()
{
  QString sql = m_Query.GenerateSQL();

  std::deque<SongInfo> songs = MusicLibrary::GetSingleton()->LookupSongs(sql, m_Query.GenerateOrderBySQL());

  m_Songs.clear();
  for (const SongInfo& si : songs)
  {
    m_Songs.push_back(si.m_sSongGuid);
  }

  if (m_Query.m_SortOrder == SmartPlaylistQuery::SortOrder::Random)
  {
    std::random_device rd;
    std::mt19937 g(rd());

    std::shuffle(m_Songs.begin(), m_Songs.end(), g);
  }

  if (m_Query.m_iSongLimit > 0 && m_Songs.size() > m_Query.m_iSongLimit)
  {
    m_Songs.resize(m_Query.m_iSongLimit);
  }

  beginResetModel();
  endResetModel();
}

void SmartPlaylist::ExtendContextMenu(QMenu* pMenu)
{
  connect(pMenu->addAction("Edit Smart Playlist..."), &QAction::triggered, this, &SmartPlaylist::onShowEditDlg);
}

int SmartPlaylist::GetNumSongs() const
{
  return (int)m_Songs.size();
}

QIcon SmartPlaylist::GetIcon() const
{
  return QIcon(":/icons/icons/playlist_smart.png");
}

void SmartPlaylist::SetTitle(const QString& title)
{
  if (m_sTitle != title)
  {
    m_bWasModified = true;

    SmartPlaylistModification mod;
    mod.m_Type = SmartPlaylistModification::Type::RenamePlaylist;
    mod.m_sIdentifier = title;
    m_Recorder.AddModification(mod, this);

    emit TitleChanged(m_sTitle);
  }
}

PlaylistCategory SmartPlaylist::GetCategory()
{
  return PlaylistCategory::Procedural;
}

bool SmartPlaylist::CanModifySongList() const
{
  return false;
}

bool SmartPlaylist::CanAddSong(const QString& songGuid) const
{
  return false;
}

void SmartPlaylist::AddSong(const QString& songGuid)
{
}

const QString SmartPlaylist::GetSongGuid(int index) const
{
  if (index < 0 || index >= m_Songs.size())
    return QString();

  return m_Songs[index];
}

void SmartPlaylist::RemoveSong(int index)
{
}

bool SmartPlaylist::TryActivateSong(const QString& songGuid)
{
  for (size_t i = 0; i < m_Songs.size(); ++i)
  {
    if (m_Songs[i] == songGuid)
    {
      SetActiveSong((int)i);
      return true;
    }
  }

  return false;
}

bool SmartPlaylist::CanSerialize()
{
  return true;
}

void SmartPlaylist::Save(QDataStream& stream)
{
  m_Recorder.CoalesceEntries();
  m_Recorder.Save(stream);
}

void SmartPlaylist::Load(QDataStream& stream)
{
  beginResetModel();

  m_Songs.clear();

  m_Recorder.LoadAdditional(stream);
  m_Recorder.ApplyAll(this);

  endResetModel();
}

bool SmartPlaylist::LookupSongByIndex(int index, SongInfo& song) const
{
  QString guid = m_Songs[index];
  return MusicLibrary::GetSingleton()->FindSong(guid, song);
}

void SmartPlaylist::ShowEditor()
{
  SmartPlaylistDlg dlg(m_Query, nullptr);
  if (dlg.exec() == QDialog::Accepted)
  {
    m_bWasModified = true;

    SmartPlaylistModification mod;
    mod.m_Query = dlg.GetQuery();
    mod.m_Type = SmartPlaylistModification::Type::ChangeQuery;

    m_Recorder.AddModification(mod, this);

    Refresh();
  }
}

bool SmartPlaylist::ContainsSong(const QString& songGuid)
{
  return false;
}

void SmartPlaylist::onShowEditDlg()
{
  ShowEditor();
}

QModelIndex SmartPlaylist::index(int row, int column, const QModelIndex& parent /*= QModelIndex()*/) const
{
  if (parent.isValid())
    return QModelIndex();

  return createIndex(row, column);
}

QModelIndex SmartPlaylist::parent(const QModelIndex& child) const
{
  return QModelIndex();
}

int SmartPlaylist::rowCount(const QModelIndex& parent /*= QModelIndex()*/) const
{
  if (!parent.isValid())
  {
    return (int)m_Songs.size();
  }

  return 0;
}

QVariant SmartPlaylist::data(const QModelIndex& index, int role /*= Qt::DisplayRole*/) const
{
  const QString& songGuid = m_Songs[index.row()];

  return commonData(index, role, songGuid);
}

QString SmartPlaylist::GetFactoryName() const
{
  return "SmartPlaylist";
}

void SmartPlaylistModification::Apply(SmartPlaylist* pContext) const
{
  if (m_Type == Type::RenamePlaylist)
  {
    pContext->m_sTitle = m_sIdentifier;
    return;
  }

  if (m_Type == Type::ChangeQuery)
  {
    pContext->m_Query = m_Query;
    return;
  }
}

void SmartPlaylistModification::Save(QDataStream& stream) const
{
  stream << (int)m_Type;

  if (m_Type == Type::RenamePlaylist)
  {
    stream << m_sIdentifier;
  }

  if (m_Type == Type::ChangeQuery)
  {
    m_Query.Save(stream);
  }
}

void SmartPlaylistModification::Load(QDataStream& stream)
{
  int type = 0;
  stream >> type;
  m_Type = (Type)type;

  if (m_Type == Type::RenamePlaylist)
  {
    stream >> m_sIdentifier;
  }

  if (m_Type == Type::ChangeQuery)
  {
    m_Query.Load(stream);
  }
}

void SmartPlaylistModification::Coalesce(ModificationRecorder<SmartPlaylistModification, SmartPlaylist*>& recorder, size_t passThroughIndex)
{
  switch (m_Type)
  {
  case Type::ChangeQuery:
  {
    recorder.InvalidatePrevious(passThroughIndex, [this](const SmartPlaylistModification& mod) -> bool {
      // remove all other query changes
      return mod.m_Type == Type::ChangeQuery;
    });
  }
  break;

  case Type::RenamePlaylist:
  {
    recorder.InvalidatePrevious(passThroughIndex, [](const SmartPlaylistModification& mod) -> bool {
      // remove all previous renames
      return mod.m_Type == Type::RenamePlaylist;
    });
  }
  break;
  }
}
