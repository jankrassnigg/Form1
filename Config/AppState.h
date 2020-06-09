#pragma once

#include "Misc/Common.h"
#include "MusicLibrary/MusicLibrary.h"
#include "MusicLibrary/MusicSource.h"

class AppState : public QObject
{
  Q_OBJECT

public:
  AppState();
  ~AppState();

  enum class PlayingState
  {
    None,
    Paused,
    Playing,
  };

  static AppState* GetSingleton() { return s_Singleton; }

  void Startup();

  void SetVolume(int volume);
  int GetVolume() const { return m_iVolume; }
  void UpdateAdjustedVolume();

  void SetNormalizedTrackPosition(double newPos);
  double GetNormalizedTrackPosition() const { return m_fNormalizedTrackPosition; }

  void PlayOrPausePlayback();
  void PausePlayback();
  void StopPlayback();
  void NextSong();
  void PrevSong();

  PlayingState GetCurrentPlayingState() const { return m_PlayingState; }

  QString GetCurrentSongTitle() const;
  QString GetCurrentSongArtist() const;
  qint64 GetCurrentSongTime() const;
  qint64 GetCurrentSongDuration() const;

  const vector<unique_ptr<Playlist>>& GetAllPlaylists() const;

  void AddPlaylist(unique_ptr<Playlist>&& playlist, bool bShowEditor);
  void DeletePlaylist(Playlist* playlist);

  /// \brief Sets a new title for the given playlist, unless another playlist already has that name. Returns true on success.
  bool RenamePlaylist(Playlist* playlist, const QString& newName);

  /// \brief Searches for a playlist with the given name. Case insensitive.
  Playlist* FindPlaylist(const QString& name) const;

  void SetActivePlaylist(Playlist* playlist);
  Playlist* GetActivePlaylist() const;
  QString SuggestPlaylistName(const std::vector<QString>& songGuids) const;

  const QString& GetActiveSongGuid() const { return m_ActiveSong.m_sSongGuid; }

  void AddMusicSource(unique_ptr<MusicSource>&& musicSource);
  const vector<unique_ptr<MusicSource>>& GetAllMusicSources() const { return m_MusicSources; }

  void BeginBusyWork();
  void EndBusyWork();
  bool IsBusyWorkActive() const;
  void SongsHaveBeenImported();

  void StartSongFromPlaylist(Playlist* playlist, int songIndex);
  void StartPlaylist(Playlist* playlist);

  void LoadAllPlaylists();
  void SaveAllPlaylists(bool bForce);

  void SaveUserState();
  void LoadUserState();


signals:
  void VolumeChanged(int newVolume);
  void NormalizedTrackPositionChanged(double pos);
  void CurrentSongTimeChanged();
  void PlaylistsChanged();
  void ActivePlaylistChanged(Playlist* oldList, Playlist* newList);
  void ActiveSongChanged();
  void PlayingStateChanged();
  void RefreshSelectedPlaylist();
  void BusyWorkActive(bool bActive);
  void SongRequiresRating(QString guid);

private slots:
  void onActiveSongChanged(int index);
  void onMusicSourceAdded(const QString& path);
  void onMediaReady();
  void onMediaFinished();
  void onMediaError();
  void onMediaPositionChanged();
  void onProfileDirectoryChanged();

private:
  void ShutdownMusicSources();
  void LoadPlaylist(const QString& sPath);
  void SetFinalVolume();

  static AppState* s_Singleton;

  PlayingState m_PlayingState = PlayingState::None;
  double m_fNormalizedTrackPosition = 0;
  double m_fJumpToNormalizedTrackPosition = 0;
  int m_iVolume = 25;
  int m_iSongVolumeAdjust = 0;
  bool m_bCountedSongAsPlayed = false;

  vector<unique_ptr<Playlist>> m_AllPlaylists;
  Playlist* m_pActivePlaylist = nullptr;
  vector<unique_ptr<MusicSource>> m_MusicSources;
  vector<QString> m_SongHistory;

  SongInfo m_ActiveSong;
  QAtomicInt m_BusyWorkCounter;
};
