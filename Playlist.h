#pragma once

#include "Common.h"
#include <QAbstractItemModel>
#include <QDataStream>
#include <QIcon>

class Playlist : public QAbstractItemModel
{
  Q_OBJECT

public:
  Playlist(const QString& sTitle, const QString& guid);

  void SetPlaylistIndex(int idx) { m_iPlaylistIndex = idx; }
  int GetPlaylistIndex() const { return m_iPlaylistIndex; }

  const QString& GetGuid() const { return m_sGuid; }

  // Qt interface to implement
  //virtual QModelIndex index(int row, int column, const QModelIndex &parent = QModelIndex()) const override;
  //virtual QModelIndex parent(const QModelIndex &child) const override;
  //virtual int rowCount(const QModelIndex &parent = QModelIndex()) const override;
  virtual int columnCount(const QModelIndex& parent = QModelIndex()) const override;
  //virtual QVariant data(const QModelIndex &index, int role = Qt::DisplayRole) const override;
  //virtual QHash<int, QByteArray> roleNames() const override;
  virtual QVariant headerData(int section, Qt::Orientation orientation, int role = Qt::DisplayRole) const override;

  virtual QString GetFactoryName() const = 0;
  virtual void Refresh() = 0;

  virtual void ExtendContextMenu(QMenu* pMenu) {}

  /// \brief Returns the name of the playlist
  virtual const QString& GetTitle() const { return m_sTitle; }
  /// \brief Changes the name of the playlist
  virtual void SetTitle(const QString& title) = 0;

  virtual QIcon GetIcon() const = 0;
  virtual PlaylistCategory GetCategory() = 0;

  /// \brief Returns the number of songs in this playlist
  virtual int GetNumSongs() const = 0;

  virtual bool CanBeRenamed() const { return true; }
  virtual bool CanBeDeleted() const { return true; }
  virtual bool CanModifySongList() const = 0;
  virtual bool CanSort() { return false; }
  virtual bool CanAddSong(const QString& songGuid) const = 0;
  virtual void AddSong(const QString& songGuid) = 0;
  virtual const QString GetSongGuid(int index) const = 0;
  virtual void RemoveSong(int index);

  void SetActiveSong(int index);
  int GetActiveSong() const { return m_iActiveSong; }

  /// \brief If a song is active, this sets the next song active.
  void ActivateNextSong();
  virtual bool TryActivateSong(const QString& songGuid) = 0;

  //static unique_ptr<Playlist> LoadFromFile(const QString& sFile);
  void SaveToFile(const QString& sFile);

  virtual bool CanSerialize() = 0;
  virtual void Save(QDataStream& stream) = 0;
  virtual void Load(QDataStream& stream) = 0;

  void AddFileToDeleteOnSave(const QString& file);
  void DeletePlaylistFiles();

  void SetLoop(bool loop);
  bool GetLoop() const { return m_bLoop; }
  void SetShuffle(bool shuffle);
  bool GetShuffle() const { return m_bShuffle; }

  void Reshuffle();
  int GetNextShuffledSongIndex();
  void RemoveSongFromShuffle(int index);
  void AdjustShuffleAfterSongRemoved(int index);

  virtual QMimeData* mimeData(const QModelIndexList& indexes) const override;
  virtual Qt::ItemFlags flags(const QModelIndex &index) const override;
  virtual void LookupSongByIndex(int index, SongInfo& song) const = 0;

  void SortPlaylistData(std::vector<SortPlaylistEntry>& infos, PlaylistColumn column, bool bDescending);

signals:
  void ActiveSongChanged(int index);
  void TitleChanged(const QString& newName);
  void LoopShuffleStateChanged();

protected slots:
  virtual void onActiveSongChanged();
  void onMarkModified();

protected:
  virtual void ReachedEnd();

  QVariant commonData(const QModelIndex& index, int role, const QString& sSongGuid) const;

  bool m_bWasModified = false;
  bool m_bLoop = false;
  bool m_bShuffle = false;
  bool m_bActiveSongIndexValid = false;
  QString m_sGuid;
  QString m_sTitle;
  int m_iPlaylistIndex = -1;
  int m_iActiveSong = -1;
  std::vector<QString> m_FilesToDeleteOnSave;
  std::vector<int> m_songShuffleOrder;
};
