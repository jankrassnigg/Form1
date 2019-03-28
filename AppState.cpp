#include "AllSongsPlaylist.h"
#include "AppConfig.h"
#include "AppState.h"
#include "MusicSourceFolder.h"
#include "RegularPlaylist.h"
#include "SmartPlaylist.h"
#include "SoundDevice.h"
#include <QDataStream>
#include <QDir>
#include <QDirIterator>
#include <QFile>
#include <QSettings>

AppState* AppState::s_Singleton = nullptr;

AppState::AppState()
{
  s_Singleton = this;

  connect(SoundDevice::GetSingleton(), &SoundDevice::MediaReady, this, &AppState::onMediaReady);
  connect(SoundDevice::GetSingleton(), &SoundDevice::MediaFinished, this, &AppState::onMediaFinished);
  connect(SoundDevice::GetSingleton(), &SoundDevice::MediaError, this, &AppState::onMediaError);
  connect(SoundDevice::GetSingleton(), &SoundDevice::MediaPositionChanged, this, &AppState::onMediaPositionChanged);
  connect(AppConfig::GetSingleton(), &AppConfig::MusicSourceAdded, this, &AppState::onMusicSourceAdded);

  m_AllPlaylists.push_back(make_unique<AllSongsPlaylist>());
  m_pActivePlaylist = nullptr;
}

void AppState::Startup()
{
  QDir().mkpath(AppConfig::GetSingleton()->GetProfileDirectory());

  {
    const auto& sources = AppConfig::GetSingleton()->GetAllMusicSources();

    for (const QString& path : sources)
    {
      AddMusicSource(unique_ptr<MusicSource>(new MusicSourceFolder(path)));
    }
  }

  LoadAllPlaylists();
  SetActivePlaylist(m_AllPlaylists[0].get());

  LoadUserState();

  SetFinalVolume();
}

AppState::~AppState()
{
  SaveUserState();
  SaveAllPlaylists();
  ShutdownMusicSources();

  m_AllPlaylists.clear();
  s_Singleton = nullptr;
}

void AppState::SetVolume(int volume)
{
  volume = Clamp(volume, 0, 100);

  if (m_iVolume == volume)
    return;

  m_iVolume = volume;
  SetFinalVolume();

  emit VolumeChanged(m_iVolume);
}

void AppState::SetFinalVolume()
{
  // adjust the final volume using the individual song settings
  // for boosting we just ADD the adjustment, which works quite well

  // only use 0-85 range for the volume slider
  // remaining 15% is for boosting individual songs
  const int iBaseVolume = (int)(m_iVolume / 100.0f * 85.0f);

  int iFinalVolume = 0;
  int iAdjust = m_iSongVolumeAdjust;

  if (m_iSongVolumeAdjust < 0)
  {
    const float onePercent = iBaseVolume / 100.0f;
    iAdjust = (int)(onePercent * m_iSongVolumeAdjust);
  }

  iFinalVolume = Clamp(iBaseVolume + iAdjust, 0, 100);

  SoundDevice::GetSingleton()->SetVolume(iFinalVolume / 100.0f);
}

void AppState::UpdateAdjustedVolume()
{
  if (!m_ActiveSong.m_sSongGuid.isEmpty())
  {
    MusicLibrary::GetSingleton()->FindSong(m_ActiveSong.m_sSongGuid, m_ActiveSong);
    m_iSongVolumeAdjust = m_ActiveSong.m_iVolume;

    SetFinalVolume();
  }
}

void AppState::SetNormalizedTrackPosition(double newPos)
{
  if (m_fNormalizedTrackPosition == newPos)
    return;

  m_fNormalizedTrackPosition = newPos;
  SoundDevice::GetSingleton()->SetNormalizedPosition(newPos);
}

void AppState::PlayOrPausePlayback()
{
  if (m_pActivePlaylist == nullptr)
    return;

  if (m_PlayingState == PlayingState::None || m_ActiveSong.m_sSongGuid.isEmpty())
  {
    m_pActivePlaylist->Reshuffle();
    NextSong();
    return;
  }
  else if (m_PlayingState == PlayingState::Playing)
  {
    m_PlayingState = PlayingState::Paused;
    SoundDevice::GetSingleton()->PausePlaying();
  }
  else if (m_PlayingState == PlayingState::Paused)
  {
    m_PlayingState = PlayingState::Playing;
    SoundDevice::GetSingleton()->StartPlaying();
  }

  emit PlayingStateChanged();
}

void AppState::PausePlayback()
{
  if (m_pActivePlaylist == nullptr || m_ActiveSong.m_sSongGuid.isEmpty())
    return;

  m_PlayingState = PlayingState::Paused;
  SoundDevice::GetSingleton()->PausePlaying();

  emit PlayingStateChanged();
}

void AppState::StopPlayback()
{
  m_PlayingState = PlayingState::None;
  m_fNormalizedTrackPosition = 0;
  SoundDevice::GetSingleton()->StopPlaying();

  emit PlayingStateChanged();
}

void AppState::NextSong()
{
  if (m_pActivePlaylist)
  {
    m_pActivePlaylist->ActivateNextSong();
  }
}

void AppState::PrevSong()
{
  if (m_pActivePlaylist)
  {
    if (!m_SongHistory.empty())
      m_SongHistory.pop_back();

    if (!m_SongHistory.empty())
    {
      QString sSongGuid = m_SongHistory.back();
      m_SongHistory.pop_back();

      if (!m_pActivePlaylist->TryActivateSong(sSongGuid))
      {
        MusicLibrary::GetSingleton()->FindSong(sSongGuid, m_ActiveSong);

        if (!m_ActiveSong.m_sSongGuid.isEmpty())
        {
          m_ActiveSong.m_sSongGuid = sSongGuid;
          m_SongHistory.push_back(sSongGuid);

          std::deque<QString> locations;
          MusicLibrary::GetSingleton()->GetSongLocations(m_ActiveSong.m_sSongGuid, locations);

          emit ActiveSongChanged();

          for (const QString& loc : locations)
          {
            if (QFileInfo::exists(loc))
            {
              SoundDevice::GetSingleton()->SetMedia(loc.toUtf8().data(), m_ActiveSong.m_iStartOffset, m_ActiveSong.m_iEndOffset);
              SoundDevice::GetSingleton()->StartPlaying();

              m_PlayingState = PlayingState::Playing;
              emit PlayingStateChanged();
              break;
            }
          }
        }
      }
    }
    else
    {
      StopPlayback();
    }
  }
}

QString AppState::GetCurrentSongTitle() const
{
  if (!m_ActiveSong.m_sSongGuid.isEmpty())
  {
    return m_ActiveSong.m_sTitle;
  }

  return QString();
}

QString AppState::GetCurrentSongArtist() const
{
  if (!m_ActiveSong.m_sSongGuid.isEmpty())
  {
    if (m_ActiveSong.m_sArtist.isEmpty())
      return "Unknown Artist";

    return m_ActiveSong.m_sArtist;
  }

  return QString();
}

qint64 AppState::GetCurrentSongTime() const
{
  return (qint64)(SoundDevice::GetSingleton()->GetPosition() * 1000.0);
}

qint64 AppState::GetCurrentSongDuration() const
{
  return (qint64)(SoundDevice::GetSingleton()->GetDuration() * 1000.0);
}

const vector<unique_ptr<Playlist>>& AppState::GetAllPlaylists() const
{
  return m_AllPlaylists;
}

void AppState::AddPlaylist(unique_ptr<Playlist>&& playlist)
{
  m_AllPlaylists.push_back(std::move(playlist));
  sort(m_AllPlaylists.begin(), m_AllPlaylists.end(), [](const unique_ptr<Playlist>& lhs, const unique_ptr<Playlist>& rhs) -> bool
  {
    if (lhs->GetCategory() == rhs->GetCategory())
    {
      return lhs->GetTitle().compare(rhs->GetTitle(), Qt::CaseInsensitive) < 0;
    }

    return lhs->GetCategory() <= rhs->GetCategory();
  });

  for (int i = 0; i < m_AllPlaylists.size(); ++i)
  {
    m_AllPlaylists[i]->SetPlaylistIndex(i);
  }

  emit PlaylistsChanged();
}

void AppState::DeletePlaylist(Playlist* playlist)
{
  if (!playlist->CanBeDeleted())
    return;

  if (m_pActivePlaylist == playlist)
  {
    SetActivePlaylist(nullptr);
  }

  for (size_t i = 0; i < m_AllPlaylists.size(); ++i)
  {
    if (m_AllPlaylists[i].get() == playlist)
    {
      m_AllPlaylists[i]->DeletePlaylistFiles();
      m_AllPlaylists[i] = nullptr;

      m_AllPlaylists.erase(m_AllPlaylists.begin() + i);
      break;
    }
  }

  emit PlaylistsChanged();
}

void AppState::RenamePlaylist(Playlist* playlist, const QString& newName)
{
  playlist->SetTitle(newName);

  emit PlaylistsChanged();
}

void AppState::SetActivePlaylist(Playlist* playlist)
{
  if (m_pActivePlaylist == playlist)
    return;

  m_SongHistory.clear();

  Playlist* prevList = m_pActivePlaylist;

  if (prevList)
  {
    // notify that the list is not playing anymore
    prevList->SetActiveSong(-1);

    disconnect(prevList, &Playlist::ActiveSongChanged, this, &AppState::onActiveSongChanged);
  }

  m_pActivePlaylist = playlist;

  if (m_pActivePlaylist)
  {
    connect(m_pActivePlaylist, &Playlist::ActiveSongChanged, this, &AppState::onActiveSongChanged);
    m_pActivePlaylist->Refresh();
  }

  emit ActivePlaylistChanged(prevList, m_pActivePlaylist);
}

void AppState::onActiveSongChanged(int index)
{
  StopPlayback();
  m_ActiveSong.m_sSongGuid = QString();
  m_bCountedSongAsPlayed = false;
  m_iSongVolumeAdjust = 0;

  if (index >= 0)
  {
    QString guid = m_pActivePlaylist->GetSongGuid(index);
    MusicLibrary::GetSingleton()->FindSong(guid, m_ActiveSong);

    bool foundLocation = false;

    if (!m_ActiveSong.m_sSongGuid.isEmpty())
    {
      m_ActiveSong.m_sSongGuid = guid;
      m_iSongVolumeAdjust = m_ActiveSong.m_iVolume;
      SetFinalVolume();

      std::deque<QString> locations;
      MusicLibrary::GetSingleton()->GetSongLocations(m_ActiveSong.m_sSongGuid, locations);

      for (const QString& loc : locations)
      {
        if (QFileInfo::exists(loc))
        {
          foundLocation = true;
          m_SongHistory.push_back(guid);

          SoundDevice::GetSingleton()->SetMedia(loc.toUtf8().data(), m_ActiveSong.m_iStartOffset, m_ActiveSong.m_iEndOffset);
          SoundDevice::GetSingleton()->StartPlaying();

          m_PlayingState = PlayingState::Playing;
          emit PlayingStateChanged();
          break;
        }
      }
    }

    if (!foundLocation)
    {
      onMediaError();
      return;
    }
  }

  emit ActiveSongChanged();
}

void AppState::onMusicSourceAdded(const QString& path)
{
  AddMusicSource(unique_ptr<MusicSource>(new MusicSourceFolder(path)));
}

void AppState::onMediaReady()
{
  if (m_pActivePlaylist == nullptr || m_ActiveSong.m_sSongGuid.isEmpty())
    return;

  if (m_fJumpToNormalizedTrackPosition > 0)
  {
    SoundDevice::GetSingleton()->SetNormalizedPosition(m_fJumpToNormalizedTrackPosition);
    m_fJumpToNormalizedTrackPosition = 0;
  }

  // write the duration of the song to the database
  // we already have this information from the MP3 tag, but that data can be unreliable
  const int durationInMS = (int)(SoundDevice::GetSingleton()->GetDuration() * 1000.0);
  MusicLibrary::GetSingleton()->UpdateSongDuration(m_ActiveSong.m_sSongGuid, durationInMS);

  // also regularly save the user state
  SaveUserState();
}

void AppState::onMediaFinished()
{
  if (m_pActivePlaylist == nullptr)
    return;

  if (!m_ActiveSong.m_sSongGuid.isEmpty() && !m_bCountedSongAsPlayed)
  {
    // this can happen when the start/end offset is outside the regular range

    m_bCountedSongAsPlayed = true;
    MusicLibrary::GetSingleton()->CountSongPlayed(m_ActiveSong.m_sSongGuid);
  }

  m_fJumpToNormalizedTrackPosition = 0;
  m_pActivePlaylist->ActivateNextSong();
}

void AppState::onMediaError()
{
  onMediaFinished();
}

void AppState::onMediaPositionChanged()
{
  m_fNormalizedTrackPosition = SoundDevice::GetSingleton()->GetPosition() / SoundDevice::GetSingleton()->GetDuration();
  emit NormalizedTrackPositionChanged(m_fNormalizedTrackPosition);
  emit CurrentSongTimeChanged();

  if (!m_bCountedSongAsPlayed && m_fNormalizedTrackPosition > 0.7)
  {
    m_bCountedSongAsPlayed = true;

    MusicLibrary::GetSingleton()->CountSongPlayed(m_ActiveSong.m_sSongGuid);
  }
}

Playlist* AppState::GetActivePlaylist() const
{
  return m_pActivePlaylist;
}

QString AppState::SuggestPlaylistName(const std::vector<QString>& songGuids) const
{
  QString commonArtist, commonAlbum;

  for (size_t i = 0; i < songGuids.size(); ++i)
  {
    SongInfo info;
    MusicLibrary::GetSingleton()->FindSong(songGuids[i], info);

    if (info.m_sSongGuid.isEmpty())
      continue;

    if (i == 0)
    {
      commonArtist = info.m_sArtist;
      commonAlbum = info.m_sAlbum;
    }
    else
    {
      if (commonAlbum != info.m_sAlbum)
        commonAlbum = QString();

      if (commonArtist != info.m_sArtist)
        commonArtist = QString();
    }
  }

  QString result = "";

  if (!commonArtist.isEmpty())
  {
    result = commonArtist;
  }

  if (!commonAlbum.isEmpty())
  {
    if (!result.isEmpty())
      result.append(" - ");

    result.append(commonAlbum);
  }


  return result;
}

void AppState::AddMusicSource(unique_ptr<MusicSource>&& musicSource)
{
  m_MusicSources.push_back(std::move(musicSource));

  m_MusicSources.back()->Startup();
}

void AppState::BeginBusyWork()
{
  if (m_BusyWorkCounter++ == 0)
  {
    emit BusyWorkActive(true);
  }
}

void AppState::EndBusyWork()
{
  if (--m_BusyWorkCounter == 0)
  {
    emit BusyWorkActive(false);
  }
}

bool AppState::IsBusyWorkActive() const
{
  return m_BusyWorkCounter > 0;
}

void AppState::SongsHaveBeenImported()
{
  emit RefreshSelectedPlaylist();
}

void AppState::ShutdownMusicSources()
{
  for (size_t i = 0; i < m_MusicSources.size(); ++i)
  {
    m_MusicSources[i]->Shutdown();
    m_MusicSources[i] = nullptr;
  }

  m_MusicSources.clear();
}

void AppState::StartSongFromPlaylist(Playlist* playlist, int songIndex)
{
  m_fNormalizedTrackPosition = 0;
  m_fJumpToNormalizedTrackPosition = 0;
  SetActivePlaylist(playlist);

  if (m_pActivePlaylist)
  {
    m_pActivePlaylist->SetActiveSong(songIndex);
  }
}

void AppState::StartPlaylist(Playlist* playlist)
{
  m_fNormalizedTrackPosition = 0;
  m_fJumpToNormalizedTrackPosition = 0;
  SetActivePlaylist(playlist);

  if (m_pActivePlaylist)
  {
    m_pActivePlaylist->SetActiveSong(-1);
    m_pActivePlaylist->Reshuffle();
    NextSong();
  }

}

void AppState::LoadAllPlaylists()
{
  const QString sDir = AppConfig::GetSingleton()->GetProfileDirectory() + "/playlists/";
  QDir().mkpath(sDir);

  QDirIterator dirIt(sDir, QDirIterator::Subdirectories | QDirIterator::FollowSymlinks);

  while (dirIt.hasNext())
  {
    dirIt.next();

    const QFileInfo fileInfo = dirIt.fileInfo();

    if (fileInfo.isDir())
      continue;

    const QString sPlaylist = fileInfo.absoluteFilePath();

    //dirIt.filePath();

    if (!sPlaylist.endsWith(".f1pl", Qt::CaseInsensitive))
      continue;

    LoadPlaylist(sPlaylist);
  }
}

void AppState::LoadPlaylist(const QString& sPath)
{
  QFile file(sPath);
  if (!file.open(QIODevice::OpenModeFlag::ReadOnly))
    return;

  QDataStream stream(&file);

  QString sGuid, sFactory, sTitle;
  stream >> sGuid;
  stream >> sFactory;
  stream >> sTitle;

  Playlist* pPlaylist = nullptr;

  for (size_t i = 0; i < m_AllPlaylists.size(); ++i)
  {
    if (m_AllPlaylists[i]->GetGuid() == sGuid)
    {
      pPlaylist = m_AllPlaylists[i].get();
      break;
    }
  }

  if (pPlaylist == nullptr)
  {
    // TODO: use factory

    if (sFactory == "RegularPlaylist")
    {
      unique_ptr<RegularPlaylist> newPlaylist = make_unique<RegularPlaylist>(sTitle, sGuid);
      pPlaylist = newPlaylist.get();
      AddPlaylist(std::move(newPlaylist));
    }

    if (sFactory == "SmartPlaylist")
    {
      unique_ptr<SmartPlaylist> newPlaylist = make_unique<SmartPlaylist>(sTitle, sGuid);
      pPlaylist = newPlaylist.get();
      AddPlaylist(std::move(newPlaylist));
    }
  }

  pPlaylist->Load(stream);
  pPlaylist->AddFileToDeleteOnSave(sPath);

  {
    QSettings s;
    s.beginGroup("PlaylistStates");

    QString val;

    val = QString("%1-shuffle").arg(pPlaylist->GetGuid());
    pPlaylist->SetShuffle(s.value(val, pPlaylist->GetShuffle()).toBool());
    val = QString("%1-loop").arg(pPlaylist->GetGuid());
    pPlaylist->SetLoop(s.value(val, pPlaylist->GetLoop()).toBool());

    s.endGroup();
  }
}

void AppState::SaveAllPlaylists()
{
  const QString dt = QDateTime::currentDateTimeUtc().toString("yyyy-MM-dd-hh-mm-ss");
  const QString sDir = AppConfig::GetSingleton()->GetProfileDirectory() + "/playlists/";
  const QString sBaseFile = sDir + dt + " - ";

  QDir().mkdir(sDir);

  QSettings s;
  s.beginGroup("PlaylistStates");

  QString val;

  for (int i = 0; i < m_AllPlaylists.size(); ++i)
  {
    val = QString("%1-shuffle").arg(m_AllPlaylists[i]->GetGuid());
    s.setValue(val, m_AllPlaylists[i]->GetShuffle());
    val = QString("%1-loop").arg(m_AllPlaylists[i]->GetGuid());
    s.setValue(val, m_AllPlaylists[i]->GetLoop());

    if (!m_AllPlaylists[i]->CanSerialize())
      continue;

    const QString sPath = sBaseFile + m_AllPlaylists[i]->GetTitle() + ".f1pl";

    m_AllPlaylists[i]->SaveToFile(sPath);
  }

  s.endGroup();
}

void AppState::SaveUserState()
{
  QSettings s;
  s.beginGroup("UserState");

  if (m_pActivePlaylist)
  {
    s.setValue("Volume", m_iVolume);
    s.setValue("LastPlaylist", m_pActivePlaylist->GetGuid());
    s.setValue("LastSongIdx", m_pActivePlaylist->GetActiveSong());
    s.setValue("TrackPosition", m_fNormalizedTrackPosition);
  }

  s.endGroup();
}

void AppState::LoadUserState()
{
  QString lastPlaylist;
  int lastSong = -1;
  double trackPos = 0;

  QSettings s;
  s.beginGroup("UserState");
  m_iVolume = s.value("Volume", 25).toInt();
  lastPlaylist = s.value("LastPlaylist", lastPlaylist).toString();
  lastSong = s.value("LastSongIdx", lastSong).toInt();
  trackPos = s.value("TrackPosition", 0.0).toDouble();
  s.endGroup();

  if (!lastPlaylist.isEmpty())
  {
    for (size_t i = 0; i < m_AllPlaylists.size(); ++i)
    {
      if (m_AllPlaylists[i]->GetGuid() == lastPlaylist)
      {
        SetActivePlaylist(m_AllPlaylists[i].get());
        m_fJumpToNormalizedTrackPosition = trackPos;
        m_AllPlaylists[i]->SetActiveSong(lastSong);
        PausePlayback();
        break;
      }
    }
  }
}
