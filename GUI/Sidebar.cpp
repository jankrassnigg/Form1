#include "Config/AppState.h"
#include "GUI/Sidebar.h"
#include <QFont>
#include <QMimeData>

Sidebar::Sidebar(QObject* parent)
  : QAbstractItemModel(parent)
{
  connect(AppState::GetSingleton(), &AppState::PlaylistsChanged, this, &Sidebar::onPlaylistsChanged);
  connect(AppState::GetSingleton(), &AppState::ActivePlaylistChanged, this, &Sidebar::onPlaylistsChanged);
}

void Sidebar::onPlaylistsChanged()
{
  beginResetModel();
  endResetModel();
}

QModelIndex Sidebar::index(int row, int column, const QModelIndex& parent /*= QModelIndex()*/) const
{
  if (parent.isValid())
    return QModelIndex();

  return createIndex(row, column);
}

QModelIndex Sidebar::parent(const QModelIndex& child) const
{
  return QModelIndex();
}

int Sidebar::rowCount(const QModelIndex& parent /*= QModelIndex()*/) const
{
  const auto& playlists = AppState::GetSingleton()->GetAllPlaylists();

  return (int)playlists.size();
}

int Sidebar::columnCount(const QModelIndex& parent /*= QModelIndex()*/) const
{
  return 1;
}

QVariant Sidebar::data(const QModelIndex& index, int role /*= Qt::DisplayRole*/) const
{
  const auto& playlists = AppState::GetSingleton()->GetAllPlaylists();
  const int row = index.row();

  if (row < 0 || row >= playlists.size())
    return QVariant();

  if (role == Qt::DisplayRole)
  {
    return playlists[row]->GetTitle();
  }
  else if (role == Qt::FontRole)
  {
    if (playlists[row].get() == AppState::GetSingleton()->GetActivePlaylist())
    {
      QFont font;
      font.setBold(true);
      return font;
    }
  }
  else if (role == Qt::DecorationRole)
  {
    if (index.column() == 0)
    {
      return playlists[row]->GetIcon();
    }
  }

  return QVariant();
}

bool Sidebar::canDropMimeData(const QMimeData *data, Qt::DropAction action, int row, int column, const QModelIndex &parent) const
{
  if (data->hasFormat("application/songguids.form1"))
    return true;

  return false;
}

bool Sidebar::dropMimeData(const QMimeData *data, Qt::DropAction action, int row, int column, const QModelIndex &parent)
{
  if (!data->hasFormat("application/songguids.form1"))
    return false;

  if (!parent.isValid())
    return false;

  Playlist* pl = AppState::GetSingleton()->GetAllPlaylists()[parent.row()].get();
  if (!pl->CanModifySongList())
    return false;

  QByteArray encodedData = data->data("application/songguids.form1");
  QDataStream stream(&encodedData, QIODevice::ReadOnly);

  while (!stream.atEnd())
  {
    QString guid;
    stream >> guid;

    if (pl->CanAddSong(guid))
      pl->AddSong(guid);
  }

  return true;
}

Qt::ItemFlags Sidebar::flags(const QModelIndex &index) const
{
  const Qt::ItemFlags defaultFlags = QAbstractItemModel::flags(index);
  const int row = index.row();

  const auto& playlists = AppState::GetSingleton()->GetAllPlaylists();

  if (!index.isValid() || row < 0 || row >= playlists.size())
    return defaultFlags;

  if (playlists[row]->CanModifySongList())
    return defaultFlags | Qt::ItemFlag::ItemIsDropEnabled;

  return defaultFlags;
}
