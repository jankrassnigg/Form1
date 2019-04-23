#include "SoundDeviceQt.h"

void SoundDeviceQt::Startup()
{
  m_pMediaPlayer.reset(new QMediaPlayer(this));

  connect(m_pMediaPlayer.data(), SIGNAL(positionChanged(qint64)), this, SLOT(onPlaybackPositionChanged(qint64)));
  connect(m_pMediaPlayer.data(), SIGNAL(mediaStatusChanged(QMediaPlayer::MediaStatus)), this, SLOT(onPlaybackStateChanged(QMediaPlayer::MediaStatus)));
}

void SoundDeviceQt::Shutdown()
{
  m_pMediaPlayer.reset(nullptr);
}

bool SoundDeviceQt::SetMedia(const char* szFile, int startOffsetMS, int endOffsetMS)
{
  StopPlaying();
  m_pMediaPlayer->setMedia(QUrl::fromLocalFile(szFile));

  return true;
}

void SoundDeviceQt::StartPlaying()
{
  m_pMediaPlayer->play();
}

void SoundDeviceQt::StopPlaying()
{
  m_pMediaPlayer->stop();
}

void SoundDeviceQt::PausePlaying()
{
  m_pMediaPlayer->pause();
}

void SoundDeviceQt::SetVolume(float volume)
{
  m_pMediaPlayer->setVolume((int)(volume * 100.0f));
}

float SoundDeviceQt::GetVolume() const
{
  return m_pMediaPlayer->volume() / 100.0f;
}

double SoundDeviceQt::GetDuration() const
{
  if (m_pMediaPlayer->isAudioAvailable())
  {
    return m_pMediaPlayer->duration() / 1000.0;
  }

  return 0;
}

double SoundDeviceQt::GetPosition() const
{
  if (m_pMediaPlayer->isAudioAvailable())
  {
    return m_pMediaPlayer->position() / 1000.0;
  }

  return 0;
}

void SoundDeviceQt::SetNormalizedPosition(double norm)
{
  if (m_pMediaPlayer->isAudioAvailable())
  {
    double sec = GetDuration() * norm;
    m_pMediaPlayer->setPosition((qint64)(sec * 1000.0));
  }
}

void SoundDeviceQt::onPlaybackPositionChanged(qint64 pos)
{
  emit MediaPositionChanged();
}

void SoundDeviceQt::onPlaybackStateChanged(QMediaPlayer::MediaStatus status)
{
  switch (status)
  {
  case QMediaPlayer::MediaStatus::BufferedMedia:
  case QMediaPlayer::MediaStatus::LoadedMedia:
    emit MediaReady();
    break;

  case QMediaPlayer::MediaStatus::EndOfMedia:
    emit MediaFinished();
    break;

  case QMediaPlayer::MediaStatus::InvalidMedia:
    emit MediaError();
    break;
  }
}
