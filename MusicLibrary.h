#pragma once

#include "Common.h"
#include "Playlist.h"
#include "Song.h"
#include <QHash>
#include <deque>
#include <map>
#include <sqlite3.h>
#include <QFuture>
#include "ModificationRecorder.h"
#include <mutex>

struct LibraryModification : public Modification
{
  enum class Type
  {
    None,
    SetRating,
    SetVolume,
    SetStartOffset,
    SetEndOffset,
    AddPlayDate,
    SetDiscNumber,
  };

  Type m_Type = Type::None;
  QString m_sSongGuid;
  int m_iData = 0;

  void Apply(MusicLibrary* pContext) const;
  void Save(QDataStream& stream) const;
  void Load(QDataStream& stream);
  void Coalesce(ModificationRecorder<LibraryModification, MusicLibrary*>& recorder, size_t passThroughIndex);
};

class MusicLibrary : public QObject
{
  Q_OBJECT

public:
  MusicLibrary();
  ~MusicLibrary();

  static MusicLibrary* GetSingleton() { return s_Singleton; }

  void Startup(const QString& sAppDir);
  void Shutdown();
  void SaveUserState();
  void LoadUserState();

  void SetSearchText(const QString& text);

  void AddSupportedFileExtension(const char* szExtension);
  bool IsSupportedFileExtension(const char* szExtension) const;

  void FindSong(const QString& songGuid, SongInfo& song) const;

  std::deque<SongInfo> GetAllSongs() const;
  std::deque<SongInfo> LookupSongs(const QString& where, const QString& orderBy = "artist, album, disc, track") const;

  void CountSongPlayed(const QString& sGuid);

  void AddSongToLibrary(const QString& sGuid, const SongInfo& info);
  void RemoveSongFromLibrary(const QString& sGuid);
  void AddSongLocation(const QString& sGuid, const QString& sLocation, const QString& sLastModified);
  void RemoveSongLocation(const QString& sLocation);
  void GetSongLocations(const QString& sGuid, std::deque<QString>& out_Locations) const;
  bool HasSongLocations(const QString& sGuid) const;
  bool IsLocationModified(const QString& sLocation, const QString& sLastModified) const;
  void UpdateSongDuration(const QString& sGuid, int duration);
  void UpdateSongTitle(const QString& sGuid, const QString& value);
  void UpdateSongArtist(const QString& sGuid, const QString& value);
  void UpdateSongAlbum(const QString& sGuid, const QString& value);
  void UpdateSongTrackNumber(const QString& sGuid, int value);
  void UpdateSongDiscNumber(const QString& sGuid, int value, bool bRecord);
  void UpdateSongYear(const QString& sGuid, int value);
  void UpdateSongRating(const QString& sGuid, int value, bool bRecord);
  void UpdateSongVolume(const QString& sGuid, int value, bool bRecord);
  void UpdateSongStartOffset(const QString& sGuid, int value, bool bRecord);
  void UpdateSongEndOffset(const QString& sGuid, int value, bool bRecord);
  void UpdateSongPlayDate(const QString& sGuid, int value);
  void FindSongsInLocation(const QString& sLocationPrefix, std::deque<QString>& out_Guids) const;

signals:
  void SearchTextChanged(const QString& newText);

private slots:
  void onBusyWorkChanged(bool active);
  void onProfileDirectoryChanged();

private:
  static MusicLibrary* s_Singleton;

  bool CreateTable();
  void SqlExec(const QString& stmt, int(*callback)(void*, int, char**, char**), void* userData) const;

  void LoadLibraryFile(const QString& file);
  void CleanupThread();
  void CleanUpLocations();
  void CleanUpSongs();

  QString m_sSearchText;
  std::vector<QString> m_MusicFileExtensions;
  sqlite3* m_pSongDatabase = nullptr;

  QFuture<void> m_WorkerTask;
  volatile bool m_bWorkersActive = false;

  std::mutex m_RecorderMutex;
  bool m_bRecordedModifcations = false;
  ModificationRecorder<LibraryModification, MusicLibrary*> m_Recorder;
  std::vector<QString> m_LibFilesToDeleteOnSave;
};
