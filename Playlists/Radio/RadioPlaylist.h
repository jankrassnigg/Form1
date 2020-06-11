#pragma once

#include "Misc/ModificationRecorder.h"
#include "Playlists/Playlist.h"
#include <vector>
#include <random>

class RadioPlaylist;

struct RadioPlaylistItem
{
  bool m_bEnabled = false;
  QString m_sPlaylistGuid;
  int m_iLikelyhood = 0;
};

struct RadioPlaylistSettings
{
  void Save(QDataStream& stream) const;
  void Load(QDataStream& stream);

  std::vector<RadioPlaylistItem> m_Items;
};

struct RadioPlaylistModification : public Modification
{
  enum class Type
  {
    None,
    RenamePlaylist,
    ChangeSettings,
  };

  Type m_Type = Type::None;
  QString m_sIdentifier;
  RadioPlaylistSettings m_Settings;

  void Apply(RadioPlaylist* pContext) const;
  void Save(QDataStream& stream) const;
  void Load(QDataStream& stream);
  void Coalesce(ModificationRecorder<RadioPlaylistModification, RadioPlaylist*>& recorder, size_t passThroughIndex);
};

class RadioPlaylist : public Playlist
{
  Q_OBJECT

public:
  RadioPlaylist(const QString& sTitle, const QString& guid);

  // Qt interface to implement
  virtual QModelIndex index(int row, int column, const QModelIndex& parent = QModelIndex()) const override;
  virtual QModelIndex parent(const QModelIndex& child) const override;
  virtual int rowCount(const QModelIndex& parent = QModelIndex()) const override;
  virtual QVariant data(const QModelIndex& index, int role = Qt::DisplayRole) const override;
  virtual void sort(int column, Qt::SortOrder order = Qt::AscendingOrder) override;

  //////////////////////////////////////////////////////////////////////////

  virtual QString GetFactoryName() const override;
  virtual void Refresh(PlaylistRefreshReason reason) override;

  virtual void ExtendContextMenu(QMenu* pMenu) override;

  virtual int GetNumSongs() const override;
  virtual QIcon GetIcon() const override;
  virtual PlaylistCategory GetCategory() override;
  virtual void SetTitle(const QString& title) override;

  virtual bool CanSelectLoop() const { return false; }
  virtual bool CanSelectShuffle() const { return false; }
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

  virtual double GetTotalDuration() override;

private slots:
  void onShowEditDlg();

private:
  void SelectNextSong();

  friend RadioPlaylistModification;
  friend class RadioPlaylistDlg;

  RadioPlaylistSettings m_Settings;

  double m_CachedTotalDuration = 0;
  std::vector<QString> m_Songs;
  ModificationRecorder<RadioPlaylistModification, RadioPlaylist*> m_Recorder;

  std::mt19937 m_RNG;

protected:
  virtual void ReachedEnd() override;
};
