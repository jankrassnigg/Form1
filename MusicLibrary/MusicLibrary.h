#pragma once

#include "Misc/Common.h"
#include "Misc/ModificationRecorder.h"
#include "Misc/Song.h"
#include "Playlists/Playlist.h"
#include <QFuture>
#include <QHash>
#include <deque>
#include <map>
#include <mutex>
#include <sqlite3.h>

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

  bool HasModificationData(const LibraryModification& rhs) const
  {
    // do NOT compare the m_iDate, we only want to know whether an entry exists (not if the data is the same)
    return (m_Type == rhs.m_Type && m_sSongGuid == rhs.m_sSongGuid);
  }
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

  /// \brief Retrieves the information for the given song by song GUID.
  /// Returns false, if the GUID is for an unknown song. In that case \a song will remain empty (including the GUID).
  bool FindSong(const QString& songGuid, SongInfo& song) const;

  /// \brief Returns the SongInfo objects for all songs in the entire library. Uses the search string to filter the results.
  std::deque<SongInfo> GetAllSongs(bool bUseSearchString) const;

  /// \brief Returns the GUIDs for all songs in the entire library. Uses the search string to filter the results.
  std::deque<QString> GetAllSongGuids(bool bUseSearchString) const;

  std::deque<SongInfo> LookupSongs(const QString& where, const QString& orderBy = "artist, album, disc, track") const;

  void CountSongPlayed(const QString& sGuid);

  void AddSongToLibrary(const QString& sGuid, const SongInfo& info);
  void RemoveSongFromLibrary(const QString& sGuid);
  void AddSongLocation(const QString& sGuid, const QString& sLocation, const QString& sLastModified);
  void RemoveSongLocation(const QString& sLocation);

  /// \brief Returns all the locations on disk that are known for the given song.
  void GetSongLocations(const QString& sGuid, std::deque<QString>& out_Locations) const;
  bool HasSongLocations(const QString& sGuid) const;

  /// \brief Returns all album artists that are known at this time
  void GetAllKnownArtists(std::deque<QString>& out_Artists) const;

  /// \brief Returns all albums that are known at this time (for the given artist, if non-empty)
  void GetAllKnownAlbums(std::deque<QString>& out_Albums, const QString& sArtist) const;

  /// \brief Checks whether the database knows the file 'sLocation' with the given last modification date. If yes, it returns false (not changed).
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

  /// \brief Finds all songs below a certain folder on disk
  void FindSongsInLocation(const QString& sLocationPrefix, std::deque<QString>& out_Guids) const;

  /// \brief Called in a background thread to synchronize modifications that are only stored in the local database back to the persistent storage
  ///
  /// If a local change is made and the application crashes or the data about the library state is somehow else lost,
  /// this will ensure the state inside the database is added to the library description (the journaling entries) again.
  void RestoreFromDatabase();

signals:
  void SearchTextChanged(const QString& newText);

private slots:
  void onBusyWorkChanged(bool active);
  void onProfileDirectoryChanged();

private:
  static MusicLibrary* s_Singleton;

  bool CreateTable();
  void SqlExec(const QString& stmt, int (*callback)(void*, int, char**, char**), void* userData) const;

  void LoadLibraryFile(const QString& file);
  void CleanupThread();
  void CleanUpLocations();
  void CleanUpSongs();
  void UpdateSongPlayCount();

  QString m_sSearchText;
  std::vector<QString> m_MusicFileExtensions;
  sqlite3* m_pSongDatabase = nullptr;

  QFuture<void> m_WorkerTask;
  volatile bool m_bWorkersActive = false;

  mutable std::mutex m_RecorderMutex;
  ModificationRecorder<LibraryModification, MusicLibrary*> m_Recorder;
  std::vector<QString> m_LibFilesToDeleteOnSave;

  mutable std::mutex m_CacheMutex;
  mutable std::deque<SongInfo> m_songInfoCache;
};
