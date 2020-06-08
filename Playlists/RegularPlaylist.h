#pragma once

#include "Misc/ModificationRecorder.h"
#include "Playlists/Playlist.h"

class RegularPlaylist;

struct RegularPlaylistModification : public Modification
{
  enum class Type
  {
    None,
    AddSong,
    RemoveSong,
    RenamePlaylist,
  };

  Type m_Type = Type::None;
  QString m_sIdentifier;

  void Apply(RegularPlaylist* pContext) const;
  void Save(QDataStream& stream) const;
  void Load(QDataStream& stream);
  void Coalesce(ModificationRecorder<RegularPlaylistModification, RegularPlaylist*>& recorder, size_t passThroughIndex);
};

class RegularPlaylist : public Playlist
{
  Q_OBJECT

public:
  RegularPlaylist(const QString& sTitle, const QString& guid);

  // Qt interface to implement
  virtual QModelIndex index(int row, int column, const QModelIndex& parent = QModelIndex()) const override;
  virtual QModelIndex parent(const QModelIndex& child) const override;
  virtual int rowCount(const QModelIndex& parent = QModelIndex()) const override;
  virtual QVariant data(const QModelIndex& index, int role = Qt::DisplayRole) const override;
  virtual void sort(int column, Qt::SortOrder order = Qt::AscendingOrder) override;

  //////////////////////////////////////////////////////////////////////////

  virtual QString GetFactoryName() const override;
  virtual void Refresh() override;

  virtual int GetNumSongs() const override;
  virtual QIcon GetIcon() const override;
  virtual PlaylistCategory GetCategory() override;
  virtual void SetTitle(const QString& title) override;

  virtual bool CanSort() override;
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

  virtual bool ContainsSong(const QString& songGuid) override;

private:
  friend RegularPlaylistModification;

  std::vector<QString> m_Songs;
  ModificationRecorder<RegularPlaylistModification, RegularPlaylist*> m_Recorder;
};
