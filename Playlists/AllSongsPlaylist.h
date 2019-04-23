#pragma once

#include "Misc/Common.h"
#include "Playlists/Playlist.h"
#include "Misc/Song.h"
#include <deque>

class AllSongsPlaylist : public Playlist
{
  Q_OBJECT

public:
  AllSongsPlaylist();

  // Qt interface to implement
  virtual QModelIndex index(int row, int column, const QModelIndex& parent = QModelIndex()) const override;
  virtual QModelIndex parent(const QModelIndex& child) const override;
  virtual int rowCount(const QModelIndex& parent = QModelIndex()) const override;
  virtual QVariant data(const QModelIndex& index, int role = Qt::DisplayRole) const override;
  virtual void sort(int column, Qt::SortOrder order /*= Qt::AscendingOrder*/) override;

  //////////////////////////////////////////////////////////////////////////

  virtual QString GetFactoryName() const override;
  virtual void Refresh() override;
  virtual int GetNumSongs() const override;
  virtual QIcon GetIcon() const override;
  virtual PlaylistCategory GetCategory() override;
  virtual void SetTitle(const QString& title) override;

  virtual bool CanSort() { return false; }
  virtual bool CanBeRenamed() const { return false; }
  virtual bool CanBeDeleted() const override { return false; }
  virtual bool CanModifySongList() const override;
  virtual bool CanAddSong(const QString& songGuid) const override;
  virtual void AddSong(const QString& songGuid) override;
  virtual const QString GetSongGuid(int index) const override;
  virtual void RemoveSong(int index) override;
  virtual bool TryActivateSong(const QString& songGuid) override;

  virtual bool CanSerialize() override;
  virtual void Save(QDataStream& stream) override;
  virtual void Load(QDataStream& stream) override;

  virtual bool LookupSongByIndex(int index, SongInfo& song) const override;

private:
  std::deque<QString> m_AllSongs;
};
