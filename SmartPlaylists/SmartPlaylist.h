#pragma once

#include "Misc/Song.h"
#include "Misc/ModificationRecorder.h"
#include "Playlists/Playlist.h"
#include "SmartPlaylists/SmartPlaylistQuery.h"

class SmartPlaylist;

struct SmartPlaylistModification : public Modification
{
  enum class Type
  {
    None,
    RenamePlaylist,
    ChangeQuery,
  };

  Type m_Type = Type::None;
  QString m_sIdentifier;
  SmartPlaylistQuery m_Query;

  void Apply(SmartPlaylist* pContext) const;
  void Save(QDataStream& stream) const;
  void Load(QDataStream& stream);
  void Coalesce(ModificationRecorder<SmartPlaylistModification, SmartPlaylist*>& recorder, size_t passThroughIndex);
};

class SmartPlaylist : public Playlist
{
  Q_OBJECT

public:
  SmartPlaylist(const QString& sTitle, const QString& guid);

  // Qt interface to implement
  virtual QModelIndex index(int row, int column, const QModelIndex& parent = QModelIndex()) const override;
  virtual QModelIndex parent(const QModelIndex& child) const override;
  virtual int rowCount(const QModelIndex& parent = QModelIndex()) const override;
  virtual QVariant data(const QModelIndex& index, int role = Qt::DisplayRole) const override;

  //////////////////////////////////////////////////////////////////////////

  virtual QString GetFactoryName() const override;
  virtual void Refresh() override;

  virtual void ExtendContextMenu(QMenu* pMenu) override;

  virtual int GetNumSongs() const override;
  virtual QIcon GetIcon() const override;
  virtual PlaylistCategory GetCategory() override;
  virtual void SetTitle(const QString& title) override;

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

  virtual void ShowEditor() override;

  virtual bool ContainsSong(const QString& songGuid) override;

private slots:
  void onShowEditDlg();

private:
  friend SmartPlaylistModification;

  SmartPlaylistQuery m_Query;
  std::deque<QString> m_Songs;
  ModificationRecorder<SmartPlaylistModification, SmartPlaylist*> m_Recorder;
};
